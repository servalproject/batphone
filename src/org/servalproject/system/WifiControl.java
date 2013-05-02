package org.servalproject.system;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.batphone.BatPhone;
import org.servalproject.shell.Command;
import org.servalproject.shell.Shell;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
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

public class WifiControl {
	public final WifiManager wifiManager;
	public final WifiApControl wifiApManager;
	public final ConnectivityManager connectivityManager;
	private final AlarmManager alarmManager;
	final WifiAdhocControl adhocControl;
	private static final String TAG = "WifiControl";
	private final Handler handler;
	private final HandlerThread handlerThread;
	final ServalBatPhoneApplication app;
	private Shell rootShell;
	private PowerManager.WakeLock wakelock;
	private Stack<Level> currentState;
	private Stack<Level> destState;
	private Completion completion;
	private boolean autoCycling = false;
	private long nextAlarm;
	private static final int TRANSITION = 0;
	private static final int SCAN = 1;
	private AlarmLock supplicantLock;
	private AlarmLock appLock;
	private AlarmLock changingLock;
	private boolean adhocRepaired = false;
	private static final int SCAN_TIME = 30000;
	private static final int DISCOVERY_TIME = 5000;
	private static final int MODE_CHANGE_TIME = 5000;

	public enum CompletionReason {
		Success,
		Cancelled,
		Failure,
	}

	public interface Completion {
		// Note, this may be called from the state transition or main UI
		// threads. Don't do anything that could block
		public void onFinished(CompletionReason reason);
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
		if (state == WifiManager.WIFI_STATE_ENABLING) {
			if (!isLevelPresent(wifiClient)) {
				logStatus("Making sure we start wifi client");
				startClientMode(null);
				return;
			}
		}

		if (state == WifiManager.WIFI_STATE_ENABLED) {
			handler.removeMessages(SCAN);
			handler.sendEmptyMessage(SCAN);
		} else {
			supplicantLock.change(false);
		}
		triggerTransition();
	}

	public void onApStateChanged(Intent intent) {

		int oldState = intent.getIntExtra(
				WifiApControl.EXTRA_PREVIOUS_WIFI_AP_STATE, -1);
		int state = intent.getIntExtra(
				WifiApControl.EXTRA_WIFI_AP_STATE, -1);

		wifiApManager.onApStateChanged(state);

		logStatus("Received intent, Personal HotSpot has changed from "
				+ convertApState(oldState) + " to "
				+ convertApState(state));

		if (state == WifiApControl.WIFI_AP_STATE_FAILED) {
			// TODO disable adhoc (even if not known to be running?) and try
			// again
		}

		// if the user starts client mode, make that our destination and
		// try to help them get there.
		if (state == WifiApControl.WIFI_AP_STATE_ENABLING) {
			if (!(isLevelClassPresent(HotSpot.class, currentState) || isLevelClassPresent(
					HotSpot.class, destState))) {
				logStatus("Making sure we start hotspot");
				Stack<Level> dest = new Stack<Level>();
				dest.push(hotSpot);
				WifiApNetwork network = wifiApManager.getMatchingNetwork();
				if (network != null && network.config != null)
					currentState.push(new OurHotSpotConfig(network.config));
				replaceDestination(dest, null,
						CompletionReason.Cancelled);
				return;
			}
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
		supplicantLock.change(supplicantActive);
		if (!supplicantActive) {
			handler.removeMessages(SCAN);
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
		supplicantLock = getLock("Supplicant");
		currentState = new Stack<Level>();
		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		wifiApManager = WifiApControl.getApControl(wifiManager);
		connectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		adhocControl = new WifiAdhocControl(this);

		PowerManager pm = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);
		wakelock = pm
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiControl");

		alarmManager = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);

		// Are we recovering from a crash / reinstall?
		handlerThread = new HandlerThread("WifiControl");
		handlerThread.start();
		handler = new Handler(handlerThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				if (msg.what == TRANSITION)
					transition();

				if (msg.what == SCAN && wifiManager.isWifiEnabled()) {
					if (!isSupplicantActive()) {
						logStatus("Asking android to start a wifi scan");
						wifiManager.startScan();
						handler.sendEmptyMessageDelayed(SCAN, SCAN_TIME);
					}
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
			if (isSupplicantActive())
				supplicantLock.change(true);
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
					WifiApNetwork network = wifiApManager.getMatchingNetwork();
					if (network != null && network.config != null) {
						OurHotSpotConfig config = new OurHotSpotConfig(
								network.config);
						config.entered = true;
						config.started = SystemClock.elapsedRealtime();
						config.state = LevelState.Started;
						logStatus("& " + config.name);
						currentState.push(config);
					}
					hotSpot.entered = true;
				}
			}
		}

		if (currentState.isEmpty()) {
			WifiAdhocNetwork network = adhocControl.getConfig();
			if (network != null) {
				if (adhocControl.getState() == WifiAdhocControl.ADHOC_STATE_DISABLED) {
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

		if (!currentState.isEmpty())
			triggerTransition();

		nextAlarm = app.settings.getLong("next_alarm", -1);
		this.autoCycling = app.settings.getBoolean("wifi_auto", false);
		this.setAlarm();
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
		case WifiApControl.WIFI_AP_STATE_FAILED:
			return LevelState.Failed;
		}
		return null;
	}

	WifiClient wifiClient = new WifiClient();
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
				// disabling
				// all others
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

	HotSpot hotSpot = new HotSpot();

	class HotSpot extends Level {
		HotSpot() {
			super("Personal Hotspot");
		}

		@Override
		boolean recover() {
			return repairAdhoc();
		}

		@Override
		void enter() throws IOException {
			super.enter();
			logStatus("Enabling hotspot");
			if (!wifiApManager.setWifiApEnabled(null, true))
				throw new IOException("Failed to enable Hotspot");
		}

		@Override
		void exit() throws IOException {
			super.exit();
			logStatus("Disabling hotspot");
			if (!wifiApManager.setWifiApEnabled(null, false))
				throw new IOException("Failed to disable Hotspot");
		}

		@Override
		LevelState getState() throws IOException {
			int state = wifiApManager.getWifiApState();
			LevelState ls = convertApState(state);
			if (ls == LevelState.Failed) {
				if (entered)
					throw new IOException("Failed to enable Hot Spot");
				else
					ls = LevelState.Off;
			}
			if (entered && ls == LevelState.Off) {
				ls = LevelState.Starting;
			}
			return ls;
		}
	}

	class OurHotSpotConfig extends Level {
		final WifiConfiguration config;
		LevelState state = LevelState.Off;
		long started = -1;

		OurHotSpotConfig(WifiConfiguration config) {
			super("Personal Hotspot " + config.SSID);
			this.config = config;
		}

		@Override
		void enter() throws IOException {
			super.enter();
			if (compareAp(config, wifiApManager.getWifiApConfiguration())) {
				logStatus("Leaving config asis, it already matches");
				state = LevelState.Started;
				return;
			}
			state = LevelState.Starting;
			logStatus("Attempting to apply our custom profile");
			if (!wifiApManager.enableOurProfile(config))
				throw new IOException("Failed to update configuration");
			started = SystemClock.elapsedRealtime();
		}

		@Override
		void exit() throws IOException {
			super.exit();
			started = -1;

			state = LevelState.Stopping;
			logStatus("Attempting to restore user profile");
			if (!wifiApManager.restoreUserProfile())
				throw new IOException("Failed to restore configuration");
		}

		@Override
		LevelState getState() throws IOException {
			wifiApManager.onApStateChanged(wifiApManager.getWifiApState());
			long now = SystemClock.elapsedRealtime();

			if (compareAp(config, wifiApManager.getWifiApConfiguration())) {
				if (state != LevelState.Stopping)
					state = LevelState.Started;
			} else {
				if (started == -1) {
					logStatus("Completed config change");
					state = LevelState.Off;
				} else if (now - started > 10000) {
					logStatus("Failed to set config, doesn't match");
					throw new IOException(
							"Failed to apply custom hotspot configuration");
				}
			}
			return state;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof OurHotSpotConfig))
				return false;
			OurHotSpotConfig other = (OurHotSpotConfig) o;
			return compareAp(this.config, other.config);
		}

		@Override
		public int hashCode() {
			return config.SSID.hashCode()
					^ config.allowedAuthAlgorithms.hashCode()
					^ (config.preSharedKey == null ? 0xFF : config.preSharedKey
							.hashCode());
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
					continue;
				} else {
					// Yay, we have reached our destination!
					if (dest != null && dest == destState)
						replaceDestination(null, null, CompletionReason.Success);
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

				case Starting:
				case Stopping:
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

			if (dest == null)
				wakelock.release();
			else
				wakelock.acquire();
		}

		if (oldDestination != null) {
			logStatus(reason + " reaching destination;");
			if (oldDestination.isEmpty())
				logStatus("Off");
			else
				logState(oldDestination);
		}

		if (dest != null) {
			logStatus("Destination has changed to;");
			if (dest.isEmpty())
				logStatus("Off");
			else
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

			// if the software has changed networks, reset the time of our auto
			// alarm.
			setNextAlarm();
		}

		if (changingLock == null)
			changingLock = this.getLock("Mode Changing");
		changingLock.change(dest != null);

		if (oldCompletion != null)
			oldCompletion.onFinished(reason);
	}

	public void connectAdhoc(WifiAdhocNetwork network, Completion completion) {
		Level destLevel = new AdhocMode(network);
		if (isLevelPresent(destLevel))
			return;
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

	public void onAlarm() {
		// note we rely on completing a transition or unlocking to set the alarm
		// again.
		cycleMode();
	}

	public void onAppStateChange(State state) {
		if (appLock == null)
			appLock = this.getLock("Services Enabled");
		appLock.change(state != State.On);
	}

	private void cycleMode() {
		// only cycle modes when services are enabled, we've been asked to auto
		// cycle, and we're not in the middle of something.
		if (!canCycle())
			return;

		logStatus("Attempting to cycle mode");

		WifiMode mode = WifiMode.Off;

		if (this.wifiManager.isWifiEnabled()) {
			mode = WifiMode.Client;
		} else if (this.wifiApManager != null
				&& this.wifiApManager.isWifiApEnabled()) {
			mode = WifiMode.Ap;
		} else if (WifiAdhocControl.isAdhocSupported()
				&& this.adhocControl.getState() == WifiAdhocControl.ADHOC_STATE_ENABLED) {
			mode = WifiMode.Adhoc;
		}

		WifiMode next = mode;
		while (true) {
			next = WifiMode.nextMode(next);
			if (next == mode)
				break;

			switch (next) {

			case Client:
				startClientMode(null);
				return;

			case Ap:
				if (this.wifiApManager != null) {
					WifiApNetwork network = this.wifiApManager
							.getDefaultNetwork();
					if (network != null && network.config != null) {
						this.connectAp(network.config, null);
						return;
					}
				}
				break;

			case Adhoc:
				if (WifiAdhocControl.isAdhocSupported()) {
					WifiAdhocNetwork network = this.adhocControl
							.getDefaultNetwork();
					if (network != null) {
						this.connectAdhoc(network, null);
						return;
					}
				}
				break;
			}
		}
		logStatus("No valid next mode found.");
	}

	private void setNextAlarm() {
		/*
		 * We need to stay in access point & adhoc modes long enough for someone
		 * to scan us, connect & discover neighbours
		 *
		 * We need to spend more time in client mode scanning for other people
		 * than in all other modes put together
		 */

		long interval = SCAN_TIME + DISCOVERY_TIME;

		if (this.wifiManager.isWifiEnabled()) {
			interval = SCAN_TIME * 2 + (long) (SCAN_TIME * 2 * Math.random());
		}

		nextAlarm = SystemClock.elapsedRealtime() + interval;

		Editor ed = app.settings.edit();
		ed.putLong("next_alarm", nextAlarm);
		ed.commit();

		setAlarm();
	}

	public boolean canCycle() {
		return autoCycling && lockCount.get() == 0;
	}

	private void setAlarm() {
		Intent intent = new Intent(BatPhone.ACTION_MODE_ALARM);
		PendingIntent pending = PendingIntent.getBroadcast(app, 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT);
		alarmManager.cancel(pending);

		if (!canCycle())
			return;

		long now = SystemClock.elapsedRealtime();
		if (nextAlarm < now + 1000 || nextAlarm > now + 600000)
			nextAlarm = now + 1000;

		alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextAlarm,
				pending);
		logStatus("Next alarm is in "
				+ (nextAlarm - SystemClock.elapsedRealtime()) + "ms");
	}

	public interface AlarmLock {
		public void change(boolean lock);
	}

	private AtomicInteger lockCount = new AtomicInteger(0);

	public AlarmLock getLock(final String name) {
		return new AlarmLock() {
			boolean locked = false;

			@Override
			public void change(boolean lock) {
				if (lock == locked)
					return;
				logStatus("Lock " + name + " is "
						+ (lock ? "locked" : "released"));
				locked = lock;
				if (lock) {
					int was = lockCount.getAndIncrement();
					if (was == 0)
						setAlarm();
				} else {
					int now = lockCount.decrementAndGet();
					if (now == 0)
						setAlarm();
				}
			}
		};
	}

	public boolean autoCycle(boolean enabled) {
		if (this.wifiApManager == null
				&& (!WifiAdhocControl.isAdhocSupported()))
			return false;

		if (autoCycling == enabled)
			return true;

		autoCycling = enabled;

		Editor ed = app.settings.edit();
		ed.putBoolean("wifi_auto", enabled);
		ed.commit();

		setNextAlarm();
		return true;
	}

	public boolean isAutoCycling() {
		return autoCycling;
	}

	public void turnOffAdhoc() {
		// if adhoc is running or is our current destination, turn the radio
		// off.
		if (this.isLevelClassPresent(AdhocMode.class)) {
			this.off(null);
		}
	}

	public void connectAp(WifiConfiguration config, Completion completion) {
		OurHotSpotConfig destLevel = new OurHotSpotConfig(config);
		if (isLevelPresent(destLevel))
			return;
		Stack<Level> dest = new Stack<Level>();
		dest.push(hotSpot);
		dest.push(destLevel);
		replaceDestination(dest, completion, CompletionReason.Cancelled);
	}

	public void connectAp(Completion completion) {
		if (isLevelPresent(hotSpot)
				&& !isLevelClassPresent(OurHotSpotConfig.class))
			return;
		Stack<Level> dest = new Stack<Level>();
		dest.push(hotSpot);
		replaceDestination(dest, completion, CompletionReason.Cancelled);
	}

	public void startClientMode(Completion completion) {
		if (isLevelPresent(wifiClient)) {
			// special case, we don't want to disconnect from any existing
			// client profile.
			if (completion != null)
				completion.onFinished(CompletionReason.Success);
			return;
		}
		Stack<Level> dest = new Stack<Level>();
		dest.push(wifiClient);
		replaceDestination(dest, completion, CompletionReason.Cancelled);
	}

	public void connectClient(WifiConfiguration config, Completion completion) {
		if (config == null)
			throw new NullPointerException();

		Stack<Level> dest = new Stack<Level>();
		dest.push(wifiClient);
		dest.push(new WifiClientProfile(config));
		replaceDestination(dest, completion, CompletionReason.Cancelled);
	}

	public void off(Completion completion) {
		Stack<Level> dest = new Stack<Level>();
		replaceDestination(dest, completion, CompletionReason.Cancelled);
	}

	public boolean testAdhoc(Shell shell, LogOutput log) throws IOException {
		// TODO fail / cancel..
		if (!this.currentState.isEmpty())
			throw new IOException("Cancelled");
		this.adhocRepaired = false;
		return adhocControl.testAdhoc(shell, log);
	}
}
