package org.servalproject.system;

import java.io.IOException;
import java.util.Stack;

import org.servalproject.ServalBatPhoneApplication;
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

	private final Handler handler;
	private final HandlerThread handlerThread;
	private final ServalBatPhoneApplication app;

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				logStatus("Wifi client is " + wifiClient.getState());

				int state = intent.getIntExtra(
						WifiManager.EXTRA_WIFI_STATE,
						WifiManager.WIFI_STATE_UNKNOWN);

				// if the user starts client mode, make that our destination and
				// try to help them get there.
				if (state == WifiManager.WIFI_STATE_ENABLING) {
					if (!isLevelPresent(wifiClient)) {
						startClientMode();
						return;
					}
				}

				handler.sendEmptyMessage(0);
			}

			if (action.equals(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION)) {
				logStatus("Personal HotSpot is " + wifiClient.getState());

				int state = intent.getIntExtra(
						WifiApControl.EXTRA_WIFI_AP_STATE,
						WifiApControl.WIFI_AP_STATE_FAILED);

				// if the user starts client mode, make that our destination and
				// try to help them get there.
				if (state == WifiApControl.WIFI_AP_STATE_ENABLING) {
					if (!isLevelPresent(hotspot)) {
						startApMode();
						return;
					}
				}

				handler.sendEmptyMessage(0);
			}

			if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
				SupplicantState state = intent
						.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
				logStatus("Supplicant state is " + state);
				handler.sendEmptyMessage(0);
			}
		}
	};

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
			currentState.push(wifiClient);
		}

		switch (wifiApManager.getWifiApState()) {
		case WifiApControl.WIFI_AP_STATE_DISABLING:
		case WifiApControl.WIFI_AP_STATE_ENABLING:
		case WifiApControl.WIFI_AP_STATE_ENABLED:
			currentState.push(hotspot);
		}
	}

	private Stack<Level> currentState;
	Stack<Level> destState;

	enum LevelState {
		Off,
		Starting,
		Started,
		Stopping,
		Failed,
	}

	abstract class Level {
		abstract LevelState getState();

		final String name;

		Level(String name) {
			this.name = name;
		}
		void enter() throws IOException {
			;
		}

		void exit() throws IOException {
			;
		}
	}

	WifiClient wifiClient = new WifiClient();
	class WifiClient extends Level {
		WifiClient() {
			super("Wifi Client");
		}

		@Override
		void enter() throws IOException {
			wifiManager.setWifiEnabled(true);
		}

		@Override
		void exit() throws IOException {
			wifiManager.setWifiEnabled(false);
		}

		@Override
		LevelState getState() {
			int state = wifiManager.getWifiState();
			switch (state) {
			case WifiManager.WIFI_STATE_ENABLING:
				return LevelState.Starting;
			case WifiManager.WIFI_STATE_ENABLED:
				return LevelState.Started;
			case WifiManager.WIFI_STATE_DISABLING:
				return LevelState.Stopping;
			case WifiManager.WIFI_STATE_UNKNOWN:
				return LevelState.Failed;
			}
			return LevelState.Off;
		}
	}

	class WifiClientProfile extends Level {
		WifiClientNetwork network;

		public WifiClientProfile(WifiClientNetwork network) {
			super("ClientProfile " + network.SSID);
			this.network = network;
		}

		@Override
		void enter() throws IOException {
			if (network == null)
				throw new IOException("Invalid state transition");

			if (network.config.networkId <= 0) {
				logStatus("Adding network configuration");
				network.config.networkId = wifiManager
						.addNetwork(network.config);
			}
			// Ideally we'd be able to connect to this network without disabling
			// all others
			// TODO Investigate calling hidden connect method
			logStatus("Enabling network");
			wifiManager.enableNetwork(network.config.networkId, true);
		}

		@Override
		void exit() throws IOException {
			// We don't really need to force a disconnection
			// Either the wifi will be turn off, or another connection will be
			// made.
			network = null;
		}

		@Override
		LevelState getState() {
			if (network == null)
				return LevelState.Off;

			WifiInfo info = wifiManager.getConnectionInfo();
			if (info == null)
				return LevelState.Off;
			if (!info.getSSID().equals(network.SSID))
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
			// everything else is considered Off
			return LevelState.Off;
		}
	}

	HotSpot hotspot = new HotSpot();
	class HotSpot extends Level {
		HotSpot() {
			super("Personal Hotspot");
		}

		@Override
		void enter() throws IOException {
			wifiApManager.setWifiApEnabled(null, true);
		}

		@Override
		void exit() throws IOException {
			wifiApManager.setWifiApEnabled(null, false);
		}

		@Override
		LevelState getState() {
			int state = wifiApManager.getWifiApState();
			switch (state) {
			case WifiApControl.WIFI_AP_STATE_ENABLING:
				return LevelState.Starting;
			case WifiApControl.WIFI_AP_STATE_ENABLED:
				return LevelState.Started;
			case WifiApControl.WIFI_AP_STATE_DISABLING:
				return LevelState.Stopping;
			case WifiApControl.WIFI_AP_STATE_FAILED:
				return LevelState.Failed;
			}
			return LevelState.Off;
		}
	}

	public boolean compare(WifiConfiguration a, WifiConfiguration b) {
		if (a == b)
			return true;
		if (a == null || b == null)
			return false;
		return a.SSID.equals(b.SSID)
				&& a.allowedAuthAlgorithms.equals(b.allowedAuthAlgorithms)
				&& a.allowedGroupCiphers.equals(b.allowedGroupCiphers)
				&& a.allowedKeyManagement.equals(b.allowedKeyManagement)
				&& a.allowedPairwiseCiphers.equals(b.allowedPairwiseCiphers)
				&& a.allowedProtocols.equals(b.allowedProtocols)
				&& a.BSSID.equals(b.BSSID)
				&& a.preSharedKey.equals(b.preSharedKey);
	}

	class HotSpotConfig extends Level {
		WifiConfiguration config;

		HotSpotConfig(WifiConfiguration config) {
			super("Hotspot Configuration " + config.SSID);
			this.config = config;
		}

		@Override
		LevelState getState() {
			if (config == null)
				return LevelState.Off;

			WifiConfiguration current = wifiApManager
					.getWifiApConfiguration();
			return compare(current, config) ? LevelState.Started
					: LevelState.Off;
		}

		@Override
		void enter() throws IOException {
			if (config == null)
				throw new IOException("Invalid state transition");

			wifiApManager.setWifiApConfiguration(config);
		}

		@Override
		void exit() throws IOException {
			config = null;
		}
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
			state = LevelState.Starting;
			logStatus("Updating configuration");
			config.updateConfiguration();
			logStatus("Getting root shell");
			Shell shell = Shell.startRootShell();
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
			state = LevelState.Stopping;

			logStatus("Getting root shell");
			Shell shell = Shell.startRootShell();
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

	private void logStatus(String message) {
		Log.v("WifiControl", message);
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
		for (int i = 0; i < keep; i++) {
			LevelState state = currentState.get(i).getState();
			switch (state) {
			case Off:
				// remove this layer and everything above it
				keep = i - 1;
				break;
			case Started:
				break;
			default:
				// remove everything above this layer
				keep = i;
				break;
			}
		}

		while (true) {
			if (currentState.isEmpty()) {
				if (dest != null && currentState.size() < dest.size()) {
					currentState.push(dest.get(currentState.size()));
					keep = currentState.size();
				} else {
					// we have reached our destination!
					if (dest != null && dest == destState)
						destState = null;
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
					if (currentState.size() > keep)
						currentState.pop();
					else {
						try {
							logStatus("Enter " + active.name);
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
						logStatus("Exit " + active.name);
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
						currentState.push(dest.get(currentState.size()));
						keep = currentState.size();
					} else {
						// we have reached our destination!
						if (dest != null && dest == destState)
							destState = null;
						synchronized (this) {
							this.notifyAll();
						}
						return;
					}
					break;

				case Starting:
				case Stopping:
					logStatus(active.name + " is " + state);
					return;
				}
			} catch (IOException e) {
				Log.e("WifiControl", e.getMessage(), e);
				app.displayToastMessage(e.getMessage());
				state = LevelState.Failed;
			}

			if (state == LevelState.Failed) {
				logStatus(active.name + " has failed");
				currentState.pop();
				// If we have a problem exiting a level, and it's required for
				// our current destination, stop trying to reach it
				if (destState != null && destState.contains(active)) {
					destState = null;
					synchronized (this) {
						this.notifyAll();
					}
				}
			}
		}
	}

	public boolean waitForDestinationState() {
		if (destState == null)
			return true;

		// TODO failed transitions?
		synchronized (this) {
			while (destState != null) {
				try {
					this.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		return true;
	}

	private boolean isLevelPresent(Level l) {
		return currentState.contains(l) ||
				(destState != null && destState.contains(l));

	}

	public void connectAdhoc(WifiAdhocNetwork network) {
		Stack<Level> dest = new Stack<Level>();
		dest.push(adhocMode);
		this.destState = dest;
		handler.sendEmptyMessage(0);
	}

	public void startApMode() {
		if (isLevelPresent(hotspot))
			return;
		Stack<Level> dest = new Stack<Level>();
		dest.push(hotspot);
		this.destState = dest;
		handler.sendEmptyMessage(0);
	}

	public void connectAp(WifiApNetwork network) {
		Stack<Level> dest = new Stack<Level>();
		dest.push(hotspot);
		dest.push(new HotSpotConfig(network.config));
		this.destState = dest;
		handler.sendEmptyMessage(0);
	}

	public void startClientMode() {
		if (isLevelPresent(wifiClient))
			return;
		Stack<Level> dest = new Stack<Level>();
		dest.push(wifiClient);
		this.destState = dest;
		handler.sendEmptyMessage(0);
	}

	public void connectClient(WifiClientNetwork network) {
		if (network.config == null)
			return;

		Stack<Level> dest = new Stack<Level>();
		dest.push(wifiClient);
		dest.push(new WifiClientProfile(network));
		this.destState = dest;
		handler.sendEmptyMessage(0);
	}

	public void off() {
		Stack<Level> dest = new Stack<Level>();
		this.destState = dest;
		handler.sendEmptyMessage(0);
	}
}
