package org.servalproject.system;

import java.io.IOException;
import java.util.List;
import java.util.Stack;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.shell.Command;
import org.servalproject.shell.Shell;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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

	public void onSupplicantStateChanged(Intent intent) {
		SupplicantState state = intent
				.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
		logStatus("Supplicant state is " + state);
		triggerTransition();
	}

	public void onWifiNetworkStateChanged(Intent intent) {
		NetworkInfo networkInfo = intent
				.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
		String bssid = intent.getStringExtra(WifiManager.EXTRA_BSSID);

		logStatus("Network state changed;");
		logStatus("State; " + networkInfo.getState());
		logStatus("Detailed State; " + networkInfo.getDetailedState());
		logStatus("Reason; " + networkInfo.getReason());
		logStatus("Extra info; " + networkInfo.getExtraInfo());
		logStatus("Type; " + networkInfo.getTypeName());
		logStatus("Subtype; " + networkInfo.getSubtypeName());
		logStatus("Available; " + networkInfo.isAvailable());
		logStatus("Connected; " + networkInfo.isConnected());
		logStatus("Connected or connecting; "
				+ networkInfo.isConnectedOrConnecting());
		logStatus("Failover; " + networkInfo.isFailover());
		logStatus("Roaming; " + networkInfo.isRoaming());

		logStatus("BSSID; " + bssid);
	}

	public void onConnectivityChanged(Intent intent) {

	}

	private void triggerTransition() {
		handler.removeMessages(0);
		handler.sendEmptyMessage(0);
	}

	private void triggerTransition(int delay) {
		if (handler.hasMessages(0))
			return;
		handler.sendEmptyMessageDelayed(0, delay);
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

		// Are we recovering from a crash / reinstall?
		handlerThread = new HandlerThread("WifiControl");
		handlerThread.start();
		handler = new Handler(handlerThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				transition();
				super.handleMessage(msg);
			}
		};

		switch (wifiManager.getWifiState()) {
		case WifiManager.WIFI_STATE_DISABLING:
		case WifiManager.WIFI_STATE_ENABLING:
		case WifiManager.WIFI_STATE_ENABLED:
			logStatus("Setting current state to " + wifiClient.name);
			wifiClient.entered = true;
			currentState.push(wifiClient);
		}

		if (currentState.isEmpty()) {
			if (wifiApManager != null) {
				switch (wifiApManager.getWifiApState()) {
				case WifiApControl.WIFI_AP_STATE_DISABLING:
				case WifiApControl.WIFI_AP_STATE_ENABLING:
				case WifiApControl.WIFI_AP_STATE_ENABLED:
					logStatus("Setting current state to " + hotSpot.name);
					hotSpot.entered = true;
					currentState.push(hotSpot);
					WifiApNetwork network = wifiApManager.getMatchingNetwork();
					if (network != null && network.config != null) {
						OurHotSpotConfig config = new OurHotSpotConfig(
								network.config);
						config.entered = true;
						config.started = SystemClock.elapsedRealtime();
						config.state = LevelState.Started;
						currentState.push(config);
					}
					hotSpot.entered = true;
				}
			}
		}

		if (currentState.isEmpty()) {
			if (this.adhocControl.getState() != WifiAdhocControl.ADHOC_STATE_DISABLED) {
				AdhocMode adhoc = new AdhocMode(null);
				adhoc.state = LevelState.Started;
				currentState.push(adhoc);
			}
		}

		if (!currentState.isEmpty())
			triggerTransition();

		PowerManager pm = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My Tag");
	}

	enum LevelState {
		Off,
		Starting,
		Started,
		Stopping,
		Failed,
	}

	abstract class Level {
		abstract LevelState getState();

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
			try {
				if (WifiAdhocControl.isAdhocSupported()) {
					Shell shell = getRootShell();
					adhocControl.stopAdhoc(shell);
					if (wifiManager.setWifiEnabled(true))
						return true;
				}
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			return false;
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
		LevelState getState() {
			LevelState ls = convertWifiState(wifiManager.getWifiState());
			if (ls == LevelState.Failed && !entered)
				ls = LevelState.Off;
			return ls;
		}
	}

	class WifiClientProfile extends Level {
		WifiConfiguration config;

		public WifiClientProfile(WifiConfiguration config) {
			super("ClientProfile " + config.SSID);
			this.config = config;
		}

		@Override
		void enter() throws IOException {
			super.enter();

			if (config == null)
				throw new IOException("Invalid state transition");

			int id = config.networkId;

			if (id <= 0) {
				logStatus("Adding network configuration for "
						+ config.SSID);
				id = wifiManager.addNetwork(config);

				if (id == -1) {
					// we need to quote the ssid so we can set it.
					String ssid = config.SSID;
					config.SSID = "\"" + ssid + "\"";
					id = wifiManager.addNetwork(config);
					// but lets put it back to what it was again...
					config.SSID = ssid;
				}

				if (id == -1)
					throw new IOException("Failed to add network configuration");

			}
			// Ideally we'd be able to connect to this network without disabling
			// all others
			// TODO Investigate calling hidden connect method
			logStatus("Enabling network " + config.SSID);
			if (!wifiManager.enableNetwork(id, true))
				throw new IOException("Failed to enable network");
		}

		@Override
		void exit() throws IOException {
			super.exit();
			// We don't really need to force a disconnection
			// Either the wifi will be turn off, or another connection will be
			// made.
			config = null;
		}

		private String removeQuotes(String src) {
			if (src == null)
				return null;
			if (src.startsWith("\"") && src.endsWith("\""))
				return src.substring(1, src.length() - 1);
			return src;
		}

		@Override
		LevelState getState() {
			if (config == null)
				return LevelState.Off;

			WifiInfo info = wifiManager.getConnectionInfo();
			if (info == null && this.entered)
				return LevelState.Starting;

			if (!removeQuotes(config.SSID).equals(removeQuotes(info.getSSID())))
				return LevelState.Off;

			switch (info.getSupplicantState()) {
			case SCANNING:
				// case AUTHENTICATING:
			case ASSOCIATING:
			case ASSOCIATED:
			case FOUR_WAY_HANDSHAKE:
			case GROUP_HANDSHAKE:
				return LevelState.Starting;
			case COMPLETED:
				return LevelState.Started;
			}
			// All other supplicant states are considered Off
			return LevelState.Off;
		}
	}

	HotSpot hotSpot = new HotSpot();

	class HotSpot extends Level {
		HotSpot() {
			super("Personal Hotspot");
		}

		@Override
		boolean recover() {
			try {
				if (WifiAdhocControl.isAdhocSupported()) {
					Shell shell = getRootShell();
					adhocControl.stopAdhoc(shell);
					if (wifiApManager.setWifiApEnabled(null, true))
						return true;
				}
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			return false;
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
		LevelState getState() {
			int state = wifiApManager.getWifiApState();
			LevelState ls = convertApState(state);
			if (ls == LevelState.Failed && !entered) {
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
			started = SystemClock.elapsedRealtime();
			if (compareAp(config, wifiApManager.getWifiApConfiguration())) {
				logStatus("Leaving config asis, it already matches");
				state = LevelState.Started;
				return;
			}

			logStatus("Saving user profile (if required)");
			wifiApManager.saveUserProfile();

			state = LevelState.Starting;
			logStatus("Enabling hotspot, with our config");
			if (!wifiApManager.setWifiApEnabled(config, true))
				throw new IOException("Failed to enable Hotspot");
		}

		@Override
		void exit() throws IOException {
			super.exit();
			started = -1;
			if (!wifiApManager.isOurNetwork()) {
				logStatus("Leaving user config asis");
				state = LevelState.Off;
				return;
			}
			logStatus("Loading user profile");
			WifiConfiguration config = wifiApManager.readUserProfile();

			if (config == null) {
				logStatus("No profile found!");
				state = LevelState.Off;
				return;
			}
			// recover config
			state = LevelState.Stopping;
			logStatus("Enabling hotspot, with user config");
			if (!wifiApManager.setWifiApEnabled(config, true))
				throw new IOException("Failed to enable Hotspot");
		}

		@Override
		LevelState getState() {
			wifiApManager.onApStateChanged(wifiApManager.getWifiApState());
			boolean starting = (state == LevelState.Starting || state == LevelState.Off);
			long now = SystemClock.elapsedRealtime();

			if (compareAp(config, wifiApManager.getWifiApConfiguration())) {
				if (starting) {
					logStatus("Leaving config asis, already applied");
					state = LevelState.Started;
				}
			} else {
				if (started == -1) {
					logStatus("Completed config change");
					state = LevelState.Off;
				} else if (now - started > 1000) {
					logStatus("Failed to set config, doesn't match");
					state = LevelState.Failed;
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

		int authA = WifiApControl.getAuthType(a);
		int authB = WifiApControl.getAuthType(b);

		return compare(a.SSID, b.SSID) &&
				authA == authB &&
				(authA == KeyMgmt.NONE || compare(a.preSharedKey,
						b.preSharedKey));
	}

	class AdhocMode extends Level {
		LevelState state = LevelState.Off;
		WifiAdhocNetwork config;

		AdhocMode(WifiAdhocNetwork config) {
			super("Adhoc Wifi " + config.getSSID());
			this.config = config;
		}

		@Override
		LevelState getState() {
			return state;
		}

		@Override
		void enter() throws IOException {
			super.enter();
			if (config == null)
				throw new IOException("Invalid state");

			state = LevelState.Starting;
			Shell shell = getRootShell();

			adhocControl.startAdhoc(shell, config);
			state = LevelState.Started;
		}

		@Override
		void exit() throws IOException {
			super.exit();
			state = LevelState.Stopping;

			Shell shell = getRootShell();
			adhocControl.stopAdhoc(shell);

			state = LevelState.Off;
		}

		@Override
		public boolean equals(Object o) {
			if (o == null)
				return false;
			if (!(o instanceof AdhocMode))
				return false;
			if (o == this)
				return true;
			AdhocMode other = (AdhocMode) o;
			return compare(this.config, other.config);
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
						.append(l.getState().toString());
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
			LevelState state = l.getState();
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
					synchronized (this) {
						this.notifyAll();
					}
					return;
				}
			}

			Level active = currentState.peek();
			LevelState state = active.getState();
			try {
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

		// when we've reached our final destination, close any root shell.
		// TODO wakelock
		if (destState == null) {
			Shell shell = this.rootShell;
			rootShell = null;
			if (shell != null) {
				try {
					shell.close();
				} catch (IOException e) {
					Log.e("WifiControl", e.getMessage(), e);
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
		}

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
		if (isLevelPresent(hotSpot))
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
		return adhocControl.testAdhoc(shell, log);
	}
}
