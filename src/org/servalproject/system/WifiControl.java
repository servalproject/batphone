package org.servalproject.system;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.shell.Command;
import org.servalproject.shell.Shell;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Stack;

public class WifiControl {
	public final WifiManager wifiManager;
	public final WifiApControl wifiApManager;
	public final ConnectivityManager connectivityManager;
	public final WifiAdhocControl adhocControl;
	private static final String TAG = "WifiControl";
	private final Handler handler;
	private final HandlerThread handlerThread;
	final ServalBatPhoneApplication app;
	private Shell rootShell;
	private PowerManager.WakeLock wakelock;
	private Stack<Level> currentState;
	private Stack<Level> destState;
	private Completion completion;
	private static final int TRANSITION = 0;
	private static final int SCAN = 1;
	private boolean adhocRepaired = false;
	private long lastAction;
	private static final int SCAN_TIME = 30000;
	private static final int DISCOVERY_TIME = 5000;
	private static final int MODE_CHANGE_TIME = 5000;

	public enum CompletionReason {
		Success,
		Cancelled,
		Failure,
	}

	public interface Completion {
		// Note, these methods may be called from any thread.
		// Don't do anything that could block
		public void onFinished(CompletionReason reason);
		public void onQueued();
	}

	public void onWifiStateChanged(Intent intent) {
		int oldState = intent.getIntExtra(
				WifiManager.EXTRA_PREVIOUS_WIFI_STATE,
				-1);
		int state = intent.getIntExtra(
				WifiManager.EXTRA_WIFI_STATE,
				-1);

		logStatus("Received intent, Wifi client has changed from "
				+ convertWifiState(oldState)
				+ " to " + convertWifiState(state));

		if (state == WifiManager.WIFI_STATE_UNKNOWN) {
			// TODO disable adhoc (even if not known to be running?) and try
			// again
		}

		// if the user starts client mode, make that our destination and
		// try to help them get there.
		if (state == WifiManager.WIFI_STATE_ENABLING && !isLevelPresent(wifiClient)) {
			logStatus("Making sure we start wifi client");
			startClientMode(null);
			return;
		}

		// if the user tries to turn off wifi, cancel what we were doing
		if (state == WifiManager.WIFI_STATE_DISABLING
				&& (destState==null || isLevelClassPresent(WifiClient.class, destState))) {
			replaceDestination(new Stack<Level>(), null, CompletionReason.Cancelled);
		}

		if (shouldScan()){
			handler.removeMessages(SCAN);
			handler.sendEmptyMessage(SCAN);
		}
		triggerTransition();
	}

	public void onApStateChanged(Intent intent) {

		int oldState = WifiApControl.fixStateNumber(intent.getIntExtra(
				WifiApControl.EXTRA_PREVIOUS_WIFI_AP_STATE, -1));
		int state = WifiApControl.fixStateNumber(intent.getIntExtra(
				WifiApControl.EXTRA_WIFI_AP_STATE, -1));

		logStatus("Received intent, Personal HotSpot has changed from "
				+ convertApState(oldState) + " to "
				+ convertApState(state));

		// if the user starts hotspot mode, make that our destination and
		// try to help them get there.
		if (state == WifiApControl.WIFI_AP_STATE_ENABLING) {
			if (!(isLevelClassPresent(HotSpot.class, currentState) || isLevelClassPresent(
					HotSpot.class, destState))) {
				logStatus("The user seems to be toggling hotspot on through the system UI. Making sure we try to repair and restart hotspot if it fails");
				Stack<Level> dest = new Stack<Level>();
				hotSpot.withUserConfig = true;
				dest.push(hotSpot);
				replaceDestination(dest, null,
						CompletionReason.Cancelled);
				return;
			}
		}

		// if the user tries to turn off hotspot, try to put their config back and then turn off again.
		if (state == WifiApControl.WIFI_AP_STATE_DISABLING &&
				(destState == null || isLevelClassPresent(HotSpot.class, destState))) {
			replaceDestination(new Stack<Level>(), null, CompletionReason.Cancelled);
		}

		triggerTransition();
	}

	private boolean isSupplicantActive(SupplicantState state) {
		if (state == null)
			return false;
		switch (state) {
		case ASSOCIATING:
			// case AUTHENTICATING:
		case ASSOCIATED:
		case COMPLETED:
		case FOUR_WAY_HANDSHAKE:
		case GROUP_HANDSHAKE:
			return true;
		}
		return false;
	}

	private boolean isSupplicantActive() {
		WifiInfo info = this.wifiManager.getConnectionInfo();
		if (info==null)
			return false;
		return isSupplicantActive(info.getSupplicantState());
	}

	public void onSupplicantStateChanged(Intent intent) {
		SupplicantState state = intent
				.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
		boolean supplicantActive = isSupplicantActive(state);
		if (shouldScan() && !handler.hasMessages(SCAN)) {
			handler.sendEmptyMessage(SCAN);
		}

		logStatus("Supplicant state is " + state);
		triggerTransition();
	}

	private void triggerTransition() {
		handler.removeMessages(TRANSITION);
		handler.sendEmptyMessage(TRANSITION);
	}

	private void triggerTransition(int delay) {
		if (handler.hasMessages(TRANSITION))
			return;
		handler.sendEmptyMessageDelayed(TRANSITION, delay);
	}

	WifiControl(Context context) {
		app = ServalBatPhoneApplication.context;
		currentState = new Stack<Level>();
		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		wifiApManager = WifiApControl.getApControl(wifiManager);
		connectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		adhocControl = new WifiAdhocControl(this);
		commotionAdhoc = new CommotionAdhoc();
		meshTether = new MeshTether(commotionAdhoc);

		PowerManager pm = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);
		wakelock = pm
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiControl");

		// Are we recovering from a crash / reinstall?
		handlerThread = new HandlerThread("WifiControl");
		handlerThread.start();
		handler = new Handler(handlerThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				if (msg.what == TRANSITION)
					transition();

				if (msg.what == SCAN && shouldScan()) {
					logStatus("Asking android to start a wifi scan");
					wifiManager.startScan();
					handler.sendEmptyMessageDelayed(SCAN, SCAN_TIME);
				}

				super.handleMessage(msg);
			}
		};

		switch (wifiManager.getWifiState()) {
		case WifiManager.WIFI_STATE_DISABLING:
		case WifiManager.WIFI_STATE_ENABLING:
		case WifiManager.WIFI_STATE_ENABLED:
			logStatus("Setting initial state to " + wifiClient.name);
			wifiClient.entered = true;
			currentState.push(wifiClient);
		}

		if (currentState.isEmpty()) {
			if (wifiApManager != null) {
				switch (wifiApManager.getWifiApState()) {
				case WifiApControl.WIFI_AP_STATE_DISABLING:
				case WifiApControl.WIFI_AP_STATE_ENABLING:
				case WifiApControl.WIFI_AP_STATE_ENABLED:
					logStatus("Setting initial state to " + hotSpot.name);
					hotSpot.entered = true;
					currentState.push(hotSpot);
					hotSpot.entered = true;
				}
			}
		}

		if (currentState.isEmpty()) {
			WifiAdhocNetwork network = adhocControl.getConfig();
			if (network != null) {
				if (adhocControl.getState() == NetworkState.Disabled) {
					// enable on boot
					this.connectAdhoc(network, null);
				} else {
					// enable after forced close
					AdhocMode adhoc = new AdhocMode(network);
					adhoc.running();
					logStatus("Setting initial state to " + adhoc.name);
					currentState.push(adhoc);
				}
			}
		}

		if (shouldScan())
			handler.sendEmptyMessage(SCAN);

		if (!currentState.isEmpty())
			triggerTransition();
	}

	private boolean shouldScan(){
		// TODO only scan while networks activity is open or autocycling
		return this.wifiManager.isWifiEnabled()
				&& !this.isSupplicantActive()
				&& !commotionAdhoc.isActive();
	}

	enum LevelState {
		Off,
		Starting,
		Started,
		Stopping,
		Failed,
	}

	abstract class Level {
		abstract LevelState getState() throws IOException;

		LevelState getStateSafe() {
			try {
				return getState();
			} catch (IOException e) {
				return LevelState.Failed;
			}
		}

		boolean entered = false;
		final String name;

		Level(String name) {
			this.name = name;
		}

		boolean recover() {
			return false;
		}
		void enter() throws IOException {
			entered = true;
		}

		void exit() throws IOException {
			entered = false;
		}
	}

	public static NetworkState getWifiClientState(int state){
		switch(state){
			case WifiManager.WIFI_STATE_DISABLED:
				return NetworkState.Disabled;
			case WifiManager.WIFI_STATE_ENABLING:
				return NetworkState.Enabling;
			case WifiManager.WIFI_STATE_ENABLED:
				return NetworkState.Enabled;
			case WifiManager.WIFI_STATE_DISABLING:
				return NetworkState.Disabling;
			case WifiManager.WIFI_STATE_UNKNOWN:
				return NetworkState.Error;
		}
		return null;
	}
	public NetworkState getWifiClientState(){
		return getWifiClientState(this.wifiManager.getWifiState());
	}

	private static LevelState convertWifiState(int state) {
		switch (state) {
		case WifiManager.WIFI_STATE_DISABLED:
			return LevelState.Off;
		case WifiManager.WIFI_STATE_ENABLING:
			return LevelState.Starting;
		case WifiManager.WIFI_STATE_ENABLED:
			return LevelState.Started;
		case WifiManager.WIFI_STATE_DISABLING:
			return LevelState.Stopping;
		case WifiManager.WIFI_STATE_UNKNOWN:
			return LevelState.Failed;
		}
		return null;
	}

	private static LevelState convertApState(int state) {
		switch (state) {
		case WifiApControl.WIFI_AP_STATE_DISABLED:
			return LevelState.Off;
		case WifiApControl.WIFI_AP_STATE_ENABLING:
			return LevelState.Starting;
		case WifiApControl.WIFI_AP_STATE_ENABLED:
			return LevelState.Started;
		case WifiApControl.WIFI_AP_STATE_DISABLING:
			return LevelState.Stopping;
		default:
			Log.v(TAG, "Unknown AP State: " + state);
		case WifiApControl.WIFI_AP_STATE_FAILED:
			return LevelState.Failed;
		}
	}

	final WifiClient wifiClient = new WifiClient();
	class WifiClient extends Level {
		WifiClient() {
			super("Wifi Client");
		}

		@Override
		boolean recover() {
			return repairAdhoc();
		}

		@Override
		void enter() throws IOException {
			super.enter();
			logStatus("Enabling wifi client");
			if (!wifiManager.setWifiEnabled(true))
				throw new IOException("Failed to enable Wifi");
		}

		@Override
		void exit() throws IOException {
			super.exit();
			logStatus("Disabling wifi client");
			if (!wifiManager.setWifiEnabled(false))
				throw new IOException("Failed to disable Wifi");
		}

		@Override
		LevelState getState() throws IOException {
			LevelState ls = convertWifiState(wifiManager.getWifiState());
			if (ls == LevelState.Failed)
				if (entered)
					throw new IOException("Failed to enable wifi client");
				else
					ls = LevelState.Off;
			return ls;
		}
	}

	static Method connectMethod;
	static {
		for (Method m : WifiManager.class.getDeclaredMethods()) {
			if (m.getName().equals("connect")) {
				connectMethod = m;
				Log.v(TAG, "Found hidden connect method");
			}
		}
		if (connectMethod == null)
			Log.v(TAG, "Did not find connect method");
	}

	public int addNetwork(WifiConfiguration config) {
		logStatus("Adding network configuration for "
				+ config.SSID);
		int networkId = wifiManager.addNetwork(config);

		if (networkId == -1) {
			// we need to quote the ssid so we can set it.
			String ssid = config.SSID;
			config.SSID = "\"" + ssid + "\"";
			networkId = wifiManager.addNetwork(config);
			// but lets put it back to what it was again...
			config.SSID = ssid;
		}

		return networkId;
	}

	class WifiClientProfile extends Level {
		final WifiConfiguration config;
		int networkId;
		LevelState state = LevelState.Off;
		boolean hasTried = false;

		public WifiClientProfile(WifiConfiguration config) {
			super("ClientProfile " + config.SSID);
			this.config = config;
			this.networkId = config.networkId;
		}

		@Override
		void enter() throws IOException {
			super.enter();
			state = LevelState.Starting;
			hasTried = false;

			if (config == null)
				throw new IOException("Invalid state transition");

			if (connectMethod != null) {
				try {
					logStatus("Attempting to connect to " + config.SSID
							+ " using hidden api");
					connectMethod.invoke(wifiManager, config, null);
				} catch (IllegalArgumentException e) {
					Log.e(TAG, e.getMessage(), e);
					connectMethod = null;
				} catch (IllegalAccessException e) {
					Log.e(TAG, e.getMessage(), e);
					connectMethod = null;
				} catch (InvocationTargetException e) {
					Log.e(TAG, e.getMessage(), e);
					connectMethod = null;
				}
			}

			if (connectMethod == null) {

				if (networkId <= 0) {
					networkId = addNetwork(config);

					if (networkId == -1)
						throw new IOException(
								"Failed to add network configuration");

				}
				// Ideally we'd be able to connect to this network without
				// disabling all others
				// TODO Investigate calling hidden connect method
				logStatus("Enabling network " + config.SSID);
				if (!wifiManager.enableNetwork(networkId, true))
					throw new IOException("Failed to enable network");
			}
		}

		@Override
		void exit() throws IOException {
			super.exit();
			// We don't really need to force a disconnection
			// Either the wifi will be turn off, or another connection will be
			// made.
			state = LevelState.Off;
		}

		private String removeQuotes(String src) {
			if (src == null)
				return null;
			if (src.startsWith("\"") && src.endsWith("\""))
				return src.substring(1, src.length() - 1);
			return src;
		}

		@Override
		LevelState getState() throws IOException {
			if (this.state==LevelState.Starting){
				WifiInfo info = wifiManager.getConnectionInfo();

				if (info!=null){
					SupplicantState supplicantState = info.getSupplicantState();

					if (info.getSSID() == null
							&& supplicantState == SupplicantState.ASSOCIATING
							&& !hasTried) {
						hasTried = true;
					}

					if (supplicantState == SupplicantState.DISCONNECTED
							&& hasTried) {
						throw new IOException("Failed to connect to "
								+ config.SSID);
					}

					if (removeQuotes(config.SSID).equals(
							removeQuotes(info.getSSID()))
							&& supplicantState == SupplicantState.COMPLETED) {
						this.state = LevelState.Started;
					}
				}
			}
			return state;
		}
	}

	private boolean repairAdhoc() {
		try {
			if (WifiAdhocControl.isAdhocSupported()) {
				if (this.adhocRepaired)
					throw new IOException(
							"Failed to start wifi driver after disabling Serval's adhoc support. Rebooting your phone should fix it.");

				this.adhocRepaired = true;
				Shell shell = getRootShell();
				adhocControl.stopAdhoc(shell);
				if (wifiApManager.setWifiApEnabled(null, true))
					return true;
			}
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		}
		return false;
	}

	public HotSpot hotSpot = new HotSpot();

	public class HotSpot extends Level {
		boolean withUserConfig=true;
		int restoreAttempts=0;
		boolean restoring = false;

		HotSpot() {
			super("Personal Hotspot");
		}

		public boolean isRestoring(){
			return restoring;
		}

		@Override
		boolean recover() {
			return repairAdhoc();
		}

		@Override
		void enter() throws IOException {
			super.enter();
			restoreAttempts=0;
			logStatus("Enabling hotspot");

			if (!wifiApManager.enable(withUserConfig))
				throw new IOException("Failed to enable Hotspot");
		}

		@Override
		void exit() throws IOException {
			// if we need to restore user config before turning off,
			// we can just return and exit() will be called again.
			if (wifiApManager.shouldRestoreConfig()) {
				int state = wifiApManager.getWifiApState();
				LevelState ls = convertApState(state);

				// if we've tried a couple of times while Hotspot is enabled, and it hasn't worked
				// try turning it off and on again
				if (ls == LevelState.Started && restoreAttempts++>=2){
					Log.v(TAG, "Turning off Hotspot so we can attempt to restore config while turning on.");
					restoring = true;
					wifiApManager.disable();
					restoreAttempts=0;
					return;
				}
				if (ls != LevelState.Stopping) {
					Log.v(TAG, "Attempting to restore user Hotspot config " + ls);
					wifiApManager.restoreUserConfig();
				}
				return;
			}

			restoring = false;
			super.exit();

			logStatus("Disabling Hotspot");
			if (!wifiApManager.disable())
				throw new IOException("Failed to disable Hotspot");
		}

		@Override
		LevelState getState() throws IOException {
			int state = wifiApManager.getWifiApState();
			LevelState ls = convertApState(state);

			// If starting failed, throw an error. If stopping failed, just pretend it worked.
			if (ls == LevelState.Failed) {
				if (entered)
					throw new IOException("Failed to enable Hot Spot");
				else
					ls = LevelState.Off;
			}else if (entered && ls!=LevelState.Started && ls!=LevelState.Starting && wifiApManager.shouldRestoreConfig()){
				// Pretend we're started so we can attempt to restore user config.
				Log.v(TAG, "Pretending "+name+" is Started instead of "+ls+" so we can restore config");
				ls = LevelState.Started;
			}
			return ls;
		}
	}

	private static boolean compare(Object a, Object b) {
		if (a == b)
			return true;
		if (a == null || b == null)
			return false;
		return a.equals(b);
	}

	public static boolean compareAp(WifiConfiguration a, WifiConfiguration b) {
		if (a == b)
			return true;
		if (a == null || b == null)
			return false;

		int authA = WifiApControl.getKeyType(a);
		int authB = WifiApControl.getKeyType(b);

		return compare(a.SSID, b.SSID) &&
				authA == authB &&
				(authA == KeyMgmt.NONE || compare(a.preSharedKey,
						b.preSharedKey));
	}

	public final CommotionAdhoc commotionAdhoc;
	final MeshTether meshTether;

	class MeshTether extends Level {
		private final CommotionAdhoc adhoc;
		MeshTether(CommotionAdhoc adhoc) {
			super(adhoc.getSSID());
			this.adhoc=adhoc;
		}

		@Override
		void enter() throws IOException {
			super.enter();
			adhoc.enable(ServalBatPhoneApplication.context, true);
		}

		@Override
		void exit() throws IOException {
			super.exit();
		}

		@Override
		LevelState getState() throws IOException {
			if (entered)
				return LevelState.Started;
			return LevelState.Off;
		}
	}

	class AdhocMode extends Level {
		LevelState state = LevelState.Off;
		WifiAdhocNetwork config;
		int version = -1;

		AdhocMode(WifiAdhocNetwork config) {
			super("Adhoc Wifi " + config.getSSID());
			this.config = config;
		}

		@Override
		LevelState getState() {
			return state;
		}

		void running() {
			this.entered = true;
			state = LevelState.Started;
		}

		@Override
		void enter() throws IOException {
			super.enter();
			if (config == null)
				throw new IOException("Invalid state");

			state = LevelState.Starting;
			adhocRepaired = false;
			Shell shell = getRootShell();
			this.version = config.getVersion();
			adhocControl.startAdhoc(shell, config);
			running();
		}

		@Override
		void exit() throws IOException {
			super.exit();
			state = LevelState.Stopping;

			Shell shell = getRootShell();
			adhocRepaired = true;
			adhocControl.stopAdhoc(shell);
			version = -1;
			state = LevelState.Off;
		}

		int getVersion() {
			if (version != -1)
				return version;
			return config.getVersion();
		}

		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (o == null)
				return false;
			if (!(o instanceof AdhocMode))
				return false;
			AdhocMode other = (AdhocMode) o;
			return compare(this.config, other.config)
					&& getVersion() == other.getVersion();
		}

		@Override
		public int hashCode() {
			return config.getSSID().hashCode();
		}
	}

	private Shell getRootShell() throws IOException {
		Shell shell = rootShell;
		if (shell == null) {
			logStatus("Getting root shell");
			shell = rootShell = Shell.startRootShell();
		}
		return shell;
	}

	class ShellCommand extends Command {
		ShellCommand(String... command) {
			super(command);
		}

		@Override
		public void exitCode(int code) {
			super.exitCode(code);
			triggerTransition();
		}

		@Override
		public void output(String line) {
			logStatus(line);
		}
	}

	// generic level class that issues shell commands as root for enter and/or
	// exit
	class ShellLevel extends Level {
		final String onCommand;
		final String offCommand;
		Command lastCommand;
		LevelState state = LevelState.Off;

		ShellLevel(String levelName, String onCommand, String offCommand) {
			super(levelName);
			this.onCommand = onCommand;
			this.offCommand = offCommand;
		}

		@Override
		void enter() throws IOException {
			super.enter();
			if (onCommand != null) {
				Shell shell = getRootShell();
				lastCommand = new ShellCommand(onCommand);
				shell.add(lastCommand);
				state = LevelState.Starting;
			} else
				state = LevelState.Started;
		}

		@Override
		void exit() throws IOException {
			super.exit();
			if (offCommand != null) {
				Shell shell = getRootShell();
				lastCommand = new ShellCommand(offCommand);
				shell.add(lastCommand);
				state = LevelState.Stopping;
			} else
				state = LevelState.Off;
		}

		@Override
		LevelState getState() {
			if ((state == LevelState.Starting || state == LevelState.Stopping)
					&& lastCommand.hasFinished()) {
				try {
					if (lastCommand.exitCode() != 0)
						state = LevelState.Failed;
					else
						state = (state == LevelState.Starting) ? LevelState.Started
								: LevelState.Off;
				} catch (InterruptedException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
			return state;
		}
	}

	void logStatus(String message) {
		Log.v(TAG, message);
	}

	private void logState(Stack<Level> state) {
		if (state.isEmpty()) {
			logStatus("Off");
			return;
		}
		StringBuilder sb = new StringBuilder("State");
		for (int i = 0; i < state.size(); i++) {
			Level l = state.get(i);
			if (l == null)
				sb.append("null?");
			else
				sb.append(", ").append(l.name).append(' ')
						.append(l.getStateSafe().toString());
		}
		logStatus(sb.toString());
	}

	private void transition() {
		Stack<Level> dest = destState;

		// how many levels can we keep?
		int keep = 0;

		if (dest == null) {
			keep = currentState.size();
		} else {
			for (; keep < dest.size() && keep < currentState.size()
					&& dest.get(keep).equals(currentState.get(keep)); keep++)
				;
		}

		// if a lower layer suddenly reports that they aren't started, throw
		// everything above away
		for (int i = 0; i < keep - 1; i++) {
			Level l = currentState.get(i);
			LevelState state = l.getStateSafe();
			if (state == LevelState.Started)
				continue;

			logStatus(l.name + " is now reporting " + state);
			keep = i + 1;
			lastAction = SystemClock.elapsedRealtime();

			if (state == LevelState.Off || state == LevelState.Failed)
				keep--;
			break;
		}

		while (dest == destState) {

			if (currentState.isEmpty()) {
				if (dest != null && currentState.size() < dest.size()) {
					Level l = dest.get(currentState.size());
					currentState.push(l);
					keep = currentState.size();
					lastAction = SystemClock.elapsedRealtime();
					continue;
				} else {
					// Yay, we have reached our destination!
					if (dest != null && dest == destState)
						replaceDestination(null, null, CompletionReason.Success);
					return;
				}
			}

			if (dest != null) {
				long now = SystemClock.elapsedRealtime();
				if (now - this.lastAction > 20000) {
					logStatus("Giving up after " + (now - this.lastAction)
							+ "ms");
					replaceDestination(null, null, CompletionReason.Failure);
					return;
				}
			}
			Level active = currentState.peek();
			LevelState state = null;
			try {
				state = active.getState();
				switch (state) {
				case Off:
					// stop and pop any levels we need to remove
					if (currentState.size() > keep || dest == null) {
						currentState.pop();
						lastAction = SystemClock.elapsedRealtime();
					} else {
						try {
							active.enter();

							if (active.getState() == LevelState.Off) {
								// if the state hasn't changed, assume that
								// state changes are async
								// and give the level a second to change it's
								// mind before trying again
								triggerTransition(1000);
								return;
							}

						} catch (IOException e) {
							// if entry fails, immediately try to exit again
							// (which may also fail...)
							logStatus("Exitting " + active.name
									+ " due to failure");
							try {
								active.exit();
							} catch (IOException x) {
								// add the enter exception to the end of the
								// exit exceptions cause list
								Throwable c = x;
								while (c.getCause() != null)
									c = c.getCause();
								c.initCause(e);
								throw x;
							}
							throw e;
						}
					}
					break;

				case Started:
					// stop and pop any levels we need to remove
					if (currentState.size() > keep) {
						active.exit();

						if (active.getState() == LevelState.Started) {
							// if the state hasn't changed, assume that
							// state changes are async
							// and give the level a second to change it's
							// mind before trying again
							triggerTransition(1000);
							return;
						}

						break;
					}
					if (dest != null && currentState.size() < dest.size()) {
						Level l = dest.get(currentState.size());
						currentState.push(l);
						keep = currentState.size();
					} else {
						// we have reached our destination!
						if (dest != null && dest == destState)
							replaceDestination(null, null,
									CompletionReason.Success);
						return;
					}
					break;

				case Stopping:
					// if our current destination is turning off, abandon it
					// (Flight mode can cause a hotspot to turn off immediately after turning on)
					if (dest!=null && currentState.size() <= keep){
						replaceDestination(null, null,
								CompletionReason.Failure);
					}
				case Starting:
					triggerTransition(500);
					return;
				}
			} catch (IOException e) {
				Log.e("WifiControl", e.getMessage(), e);
				app.displayToastMessage(e.getMessage());
				state = LevelState.Failed;
			}

			if (state == LevelState.Failed) {
				if (!active.recover()) {
					logStatus("Removing " + active.name + " due to failure");
					currentState.pop();
					lastAction = SystemClock.elapsedRealtime();
					// If we have an unrecoverable problem exiting a level, and
					// it's required for our current destination, stop trying to
					// reach it
					if (destState != null && destState.contains(active))
						replaceDestination(null, null, CompletionReason.Failure);
				}
			}
		}
	}

	private boolean isLevelClassPresent(Class<? extends Level> c,
			List<Level> state) {
		if (state == null)
			return false;
		for (int i = 0; i < state.size(); i++) {
			if (c.isInstance(state.get(i)))
				return true;
		}
		return false;
	}

	private boolean isLevelClassPresent(Class<? extends Level> c) {
		return isLevelClassPresent(c, currentState)
				|| isLevelClassPresent(c, destState);
	}

	private boolean isLevelPresent(Level l) {
		return currentState.contains(l) ||
				(destState != null && destState.contains(l));
	}

	private void replaceDestination(Stack<Level> dest, Completion completion,
			CompletionReason reason) {
		Completion oldCompletion = null;
		Stack<Level> oldDestination = null;

		// atomic replace..
		synchronized (this) {
			oldDestination = this.destState;
			oldCompletion = this.completion;
			this.destState = dest;
			this.completion = completion;
			this.lastAction = SystemClock.elapsedRealtime();

			if (dest == null)
				wakelock.release();
			else
				wakelock.acquire();
		}

		if (oldDestination != null) {
			logStatus(reason + " reaching destination;");
			logState(oldDestination);
			if (reason == CompletionReason.Failure) {
				logStatus("Current state is;");
				logState(currentState);
			}
		}

		if (dest != null) {
			logStatus("Destination has changed to;");
			logState(dest);
			triggerTransition();
		} else {
			// when we no longer have a destination, close any root shell.
			Shell shell = this.rootShell;
			rootShell = null;
			if (shell != null) {
				try {
					logStatus("closing root shell");
					shell.close();
				} catch (IOException e) {
					Log.e("WifiControl", e.getMessage(), e);
				}
			}

			// enable all disabled networks.
			List<WifiConfiguration> networks = this.wifiManager.getConfiguredNetworks();
			if (networks!=null){
				for (WifiConfiguration c : networks) {
					if (c.status == WifiConfiguration.Status.DISABLED) {
						logStatus("Re-enabling " + c.SSID);
						this.wifiManager.enableNetwork(c.networkId, false);
					}
				}
			}
		}

		if (completion!=null)
			completion.onQueued();

		if (oldCompletion != null)
			oldCompletion.onFinished(reason);
	}

	public void connectAdhoc(WifiAdhocNetwork network, Completion completion) {
		Level destLevel = new AdhocMode(network);
		if (isLevelPresent(destLevel)) {
			if (completion != null) {
				completion.onQueued();
				completion.onFinished(CompletionReason.Success);
			}
			return;
		}
		Stack<Level> dest = new Stack<Level>();
		dest.push(destLevel);
		replaceDestination(dest, completion, CompletionReason.Cancelled);
	}

	public void onAdhocConfigChange() {
		for (Level l : this.currentState) {
			if (l instanceof AdhocMode) {
				AdhocMode m = (AdhocMode) l;
				LevelState state = m.getState();
				if (state != LevelState.Off && state != LevelState.Stopping) {
					// reconnect if the configuration of this network has
					// changed
					connectAdhoc(m.config, null);
				}
				return;
			}
		}
	}

	public void turnOffAdhoc() {
		// if adhoc is running or is our current destination, turn the radio
		// off.
		if (this.isLevelClassPresent(AdhocMode.class)) {
			this.off(null);
		}
	}

	public void connectAp(boolean withUserConfig, Completion completion) {
		hotSpot.withUserConfig = withUserConfig;
		if (isLevelPresent(hotSpot)) {
			// TODO apply config immediately if already running
			if (completion != null) {
				completion.onQueued();
				completion.onFinished(CompletionReason.Success);
			}
			return;
		}
		Stack<Level> dest = new Stack<Level>();
		dest.push(hotSpot);
		replaceDestination(dest, completion, CompletionReason.Cancelled);
	}

	public void startClientMode(Completion completion) {
		if (isLevelPresent(wifiClient)) {
			// special case, we don't want to disconnect from any existing
			// client profile.
			if (completion != null) {
				completion.onQueued();
				completion.onFinished(CompletionReason.Success);
			}
			return;
		}
		Stack<Level> dest = new Stack<Level>();
		dest.push(wifiClient);
		replaceDestination(dest, completion, CompletionReason.Cancelled);
	}

	public void connectMeshTether(Completion completion) {
		Stack<Level> dest = new Stack<Level>();
		dest.push(meshTether);
		replaceDestination(dest, completion, CompletionReason.Cancelled);
	}

	public void off(Completion completion) {
		replaceDestination(new Stack<Level>(), completion, CompletionReason.Cancelled);
	}

	public boolean testAdhoc(Shell shell, LogOutput log) throws IOException {
		// TODO fail / cancel..
		if (!this.currentState.isEmpty())
			throw new IOException("Cancelled");
		this.adhocRepaired = false;
		return adhocControl.testAdhoc(shell, log);
	}

}
