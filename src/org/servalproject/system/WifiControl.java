package org.servalproject.system;

import java.io.IOException;
import java.util.List;
import java.util.Stack;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.shell.Command;
import org.servalproject.shell.CommandLog;
import org.servalproject.shell.Shell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

public class WifiControl {
	public final WifiManager wifiManager;
	public final WifiApControl wifiApManager;
	private static final String TAG = "WifiControl";
	private final Handler handler;
	private final HandlerThread handlerThread;
	private final ServalBatPhoneApplication app;
	private Shell rootShell;

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

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				int oldState = intent.getIntExtra(
						WifiManager.EXTRA_PREVIOUS_WIFI_STATE,
						-1);
				int state = intent.getIntExtra(
						WifiManager.EXTRA_WIFI_STATE,
						-1);

				logStatus("Received intent, Wifi client has changed from "
						+ convertWifiState(oldState)
						+ " to " + convertWifiState(state));

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

			if (action.equals(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION)) {
				int oldState = intent.getIntExtra(
						WifiApControl.EXTRA_PREVIOUS_WIFI_AP_STATE, -1);
				int state = intent.getIntExtra(
						WifiApControl.EXTRA_WIFI_AP_STATE, -1);

				logStatus("Received intent, Personal HotSpot has changed from "
						+ convertApState(oldState) + " to "
						+ convertApState(state));

				// if the user starts client mode, make that our destination and
				// try to help them get there.
				if (state == WifiApControl.WIFI_AP_STATE_ENABLING) {
					if (!(isLevelClassPresent(HotSpot.class, currentState) || isLevelClassPresent(
							HotSpot.class, destState))) {
						logStatus("Making sure we start hotspot");
						Stack<Level> dest = new Stack<Level>();
						dest.push(new HotSpot(null));
						replaceDestination(dest, null,
								CompletionReason.Cancelled);
						return;
					}
				}

				triggerTransition();
			}

			if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
				SupplicantState state = intent
						.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
				logStatus("Supplicant state is " + state);
				triggerTransition();
			}
		}
	};

	private void triggerTransition() {
		handler.removeMessages(0);
		handler.sendEmptyMessage(0);
	}

	WifiControl(Context context) {
		app = ServalBatPhoneApplication.context;
		currentState = new Stack<Level>();

		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		wifiApManager = WifiApControl.getApControl(wifiManager);

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

		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		context.registerReceiver(receiver, filter);

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
					HotSpot hotspot = new HotSpot(null);
					logStatus("Setting current state to " + hotspot.name);
					hotspot.entered = true;
					currentState.push(hotspot);
				}
			}
		}

		if (!currentState.isEmpty())
			triggerTransition();
	}

	private Stack<Level> currentState;
	private Stack<Level> destState;
	private Completion completion;

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

	class HotSpot extends Level {
		final WifiConfiguration config;

		HotSpot(WifiConfiguration config) {
			super("Personal Hotspot " + (config == null ? "" : config.SSID));
			this.config = config;
		}

		@Override
		void enter() throws IOException {
			super.enter();
			logStatus("Enabling hotspot");
			if (!wifiApManager.setWifiApEnabled(config, true))
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
			LevelState ls = convertApState(wifiApManager.getWifiApState());
			if (ls == LevelState.Failed && !entered) {
				ls = LevelState.Off;
			}
			if (entered && ls == LevelState.Off) {
				ls = LevelState.Starting;
			}

			if (ls != LevelState.Off && config != null) {
				WifiConfiguration current = wifiApManager
						.getWifiApConfiguration();
				if (!compare(current, config)) {
					ls = LevelState.Off;
				}
			}
			return ls;
		}
	}

	private boolean compare(Object a, Object b) {
		if (a == b)
			return true;
		if (a == null || b == null)
			return false;
		return a.equals(b);
	}

	public boolean compare(WifiConfiguration a, WifiConfiguration b) {
		if (a == b)
			return true;
		if (a == null || b == null)
			return false;

		/*
		 * allowedAuthAlgorithms && allowedKeyManagement don't match with
		 * hotspot comparisons, do we care??
		 */
		return compare(a.SSID, b.SSID)
				// && compare(a.allowedAuthAlgorithms, b.allowedAuthAlgorithms)
				&& compare(a.allowedGroupCiphers, b.allowedGroupCiphers)
				// && compare(a.allowedKeyManagement, b.allowedKeyManagement)
				&& compare(a.allowedPairwiseCiphers, b.allowedPairwiseCiphers)
				&& compare(a.allowedProtocols, b.allowedProtocols)
				&& compare(a.BSSID, b.BSSID)
				&& compare(a.preSharedKey, b.preSharedKey);
	}

	AdhocMode adhocMode;

	class AdhocMode extends Level {
		AdhocMode() {
			super("Adhoc Wifi");
		}

		LevelState state = LevelState.Off;
		WifiAdhocNetwork config;

		@Override
		LevelState getState() {
			return state;
		}

		@Override
		void enter() throws IOException {
			super.enter();
			state = LevelState.Starting;
			logStatus("Updating configuration");
			config.updateConfiguration();

			Shell shell = getRootShell();

			try {
				logStatus("Running adhoc start");
				shell.run(new CommandLog(app.coretask.DATA_FILE_PATH
						+ "/bin/adhoc start 1"));
			} catch (InterruptedException e) {
				IOException x = new IOException();
				x.initCause(e);
				throw x;
			}

			logStatus("Waiting for adhoc mode to start");
			waitForMode(shell, WifiMode.Adhoc);
			state = LevelState.Started;
		}

		private void waitForMode(Shell shell, WifiMode mode) throws IOException {
			String interfaceName = app.coretask.getProp("wifi.interface");
			WifiMode actualMode = null;

			for (int i = 0; i < 50; i++) {
				actualMode = WifiMode.getWiFiMode(shell, interfaceName);

				// We need to allow unknown for wifi drivers that lack linux
				// wireless extensions
				if (actualMode == WifiMode.Adhoc
						|| actualMode == WifiMode.Unknown)
					break;
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					Log.e("BatPhone", e.toString(), e);
				}
			}

			Log.v("BatPhone", "iwconfig;\n" + WifiMode.lastIwconfigOutput);

			if (actualMode != mode && actualMode != WifiMode.Unknown) {
				throw new IOException(
						"Failed to start Adhoc mode, mode ended up being '"
								+ actualMode + "'");
			}
		}

		@Override
		void exit() throws IOException {
			super.exit();
			state = LevelState.Stopping;

			Shell shell = getRootShell();

			try {
				logStatus("Running adhoc stop");
				if (shell.run(new CommandLog(app.coretask.DATA_FILE_PATH
						+ "/bin/adhoc stop 1")) != 0)
					throw new IOException("Failed to stop adhoc mode");
			} catch (InterruptedException e) {
				IOException x = new IOException();
				x.initCause(e);
				throw x;
			}

			logStatus("Waiting for wifi to turn off");
			waitForMode(shell, WifiMode.Off);

			state = LevelState.Off;
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

	private void logStatus(String message) {
		Log.v(TAG, message);
	}

	private void logState(Stack<Level> state) {
		StringBuilder sb = new StringBuilder("State");
		for (int i = 0; i < state.size(); i++) {
			Level l = state.get(i);
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
								handler.sendEmptyMessageDelayed(0, 1000);
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
							handler.sendEmptyMessageDelayed(0, 1000);
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
					}
					break;

				case Starting:
				case Stopping:
					return;
				}
			} catch (IOException e) {
				Log.e("WifiControl", e.getMessage(), e);
				app.displayToastMessage(e.getMessage());
				state = LevelState.Failed;
			}

			if (state == LevelState.Failed) {
				logStatus("Removing " + active.name + " due to failure");
				currentState.pop();
				// If we have a problem exiting a level, and it's required for
				// our current destination, stop trying to reach it
				if (destState != null && destState.contains(active))
					replaceDestination(null, null, CompletionReason.Failure);
			}
		}

		// when we've reached our final destination, close any root shell.
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
		Stack<Level> dest = new Stack<Level>();
		dest.push(adhocMode);
		replaceDestination(dest, completion, CompletionReason.Cancelled);
	}

	public void connectAp(WifiConfiguration config, Completion completion) {
		Stack<Level> dest = new Stack<Level>();
		dest.push(new HotSpot(config));
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
}
