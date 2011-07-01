package org.servalproject.system;

import java.io.IOException;
import java.util.ArrayList;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.WifiApControl;
import org.servalproject.batman.Batman;
import org.servalproject.batman.Olsr;
import org.servalproject.batman.PeerFinder;
import org.servalproject.batman.PeerParser;
import org.servalproject.batman.PeerRecord;
import org.servalproject.batman.Routing;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WiFiRadio {

	private WifiMode currentMode, lastActiveMode;
	private PendingIntent alarmIntent;
	private boolean changing = false;
	private boolean autoCycling = false;
	private boolean modeLocked = false;

	private int wifiState = WifiManager.WIFI_STATE_UNKNOWN;
	private int wifiApState = WifiApControl.WIFI_AP_STATE_FAILED;
	private SupplicantState supplicantState = null;
	private Routing routingImp;

	// WifiManager
	private WifiManager wifiManager;
	private WifiApControl wifiApManager;
	private AlarmManager alarmManager;
	private ServalBatPhoneApplication app;

	private static final String ALARM = "org.servalproject.WIFI_ALARM";
	public static final String WIFI_MODE_ACTION = "org.servalproject.WIFI_MODE";
	public static final String EXTRA_NEW_MODE = "new_mode";

	private static WiFiRadio wifiRadio;

	public static WiFiRadio getWiFiRadio(ServalBatPhoneApplication context) {
		if (wifiRadio == null)
			wifiRadio = new WiFiRadio(context);
		return wifiRadio;
	}

	private void modeChanged(WifiMode newMode, boolean force) {
		if (!force && changing)
			return;

		if (currentMode == newMode)
			return;

		Log.v("BatPhone", "Wifi mode is now "
				+ (newMode == null ? "null" : newMode));

		if (newMode == WifiMode.Client || newMode == WifiMode.Ap) {
			if (peerFinder == null) {
				peerFinder = new PeerFinder(app);
				peerFinder.start();
			}
		} else if (peerFinder != null) {
			peerFinder.interrupt();
			peerFinder = null;
		}

		Intent modeChanged = new Intent(WIFI_MODE_ACTION);
		modeChanged.putExtra("new_mode",
				(newMode == null ? null : newMode.toString()));
		app.sendStickyBroadcast(modeChanged);
		currentMode = newMode;
		if (newMode != null)
			lastActiveMode = currentMode;
		changing = false;
	}

	// translate wifi state int values to WifiMode enum.
	private void checkWifiMode() {

		if (wifiManager.isWifiEnabled()) {
			modeChanged(WifiMode.Client, false);
			return;
		}
		if (wifiApManager != null && wifiApManager.isWifiApEnabled()) {
			modeChanged(WifiMode.Ap, false);
			return;
		}

		if (currentMode != WifiMode.Adhoc) {
			modeChanged(null, false);
		}
	}

	private WiFiRadio(ServalBatPhoneApplication context) {
		this.app = context;
		this.alarmManager = (AlarmManager) app
				.getSystemService(Context.ALARM_SERVICE);

		createRoutingImp();

		// init wifiManager
		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		wifiApManager = WifiApControl.getApControl(wifiManager);

		wifiState = wifiManager.getWifiState();
		if (wifiApManager != null)
			wifiApState = wifiApManager.getWifiApState();

		checkWifiMode();

		String adhocStatus = app.coretask.getProp("adhoc.status");

		if (currentMode == null && app.coretask.isNatEnabled()
				&& adhocStatus.equals("running")) {
			// looks like the application force closed and
			// restarted, check that everything we require is still
			// running.
			currentMode = WifiMode.Adhoc;
			Log.v("BatPhone", "Detected adhoc mode already running");
		}

		if (app.settings.getBoolean("meshRunning", false)) {
			try {
				this.startCycling();
			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}
		}

		// receive wifi state broadcasts.
		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		filter.addAction(ALARM);

		app.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();

				if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {

					wifiState = intent.getIntExtra(
							WifiManager.EXTRA_WIFI_STATE,
							WifiManager.WIFI_STATE_UNKNOWN);
					Log.v("BatPhone", "new client state: " + wifiState);

					// if the user tries to enable wifi, and we're running adhoc
					// their attempt will fail, but we can finish it for them
					if (!changing
							&& wifiState == WifiManager.WIFI_STATE_ENABLING
							&& currentMode == WifiMode.Adhoc)
						setWiFiModeAsync(WifiMode.Client);

					checkWifiMode();

				} else if (action
						.equals(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION)) {

					wifiApState = intent.getIntExtra(
							WifiApControl.EXTRA_WIFI_AP_STATE,
							WifiApControl.WIFI_AP_STATE_FAILED);
					Log.v("BatPhone", "new AP state: " + wifiApState);

					// if the user tries to enable AP, and we're running adhoc
					// their attempt will fail, but we can finish it for them
					if (!changing
							&& wifiApState == WifiApControl.WIFI_AP_STATE_ENABLING
							&& currentMode == WifiMode.Adhoc)
						setWiFiModeAsync(WifiMode.Ap);

					checkWifiMode();

				} else if (action
						.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
					supplicantState = intent
							.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
					Log.v("BatPhone", "Supplicant State: " + supplicantState);

					testClientState();

				} else if (action.equals(ALARM)) {
					Log.v("BatPhone", "Alarm firing...");

					nextMode();
				}
			}
		}, filter);

	}

	private void createRoutingImp() {
		String routing = app.settings.getString("routingImpl", "batman");
		if (routing.equals("batman")) {
			Log.v("BatPhone", "Using batman routing");
			this.routingImp = new Batman(app.coretask);
		} else if (routing.equals("olsr")) {
			Log.v("BatPhone", "Using olsr routing");
			this.routingImp = new Olsr(app.coretask);
		} else
			Log.e("BatPhone", "Unknown routing implementation " + routing);
	}

	public void setRouting() throws IOException {
		boolean running = (routingImp == null ? false : routingImp.isRunning());

		if (running)
			routingImp.stop();

		createRoutingImp();

		if (running)
			routingImp.start();
	}

	private PeerFinder peerFinder;

	private PeerParser getPeerParser() {
		if (currentMode == WifiMode.Adhoc)
			return routingImp;
		return peerFinder;
	}

	public ArrayList<PeerRecord> getPeers() throws IOException {
		PeerParser parser = getPeerParser();
		if (parser == null)
			return null;
		return parser.getPeerList();
	}

	public int getPeerCount() throws IOException {
		PeerParser parser = getPeerParser();
		if (parser == null)
			return 1;
		return parser.getPeerCount();
	}

	public void setWiFiMode(WifiMode newMode) throws IOException {
		if (!ChipsetDetection.getDetection().isModeSupported(newMode))
			throw new IOException("Wifi mode " + newMode + " is not supported");

		releaseControl();

		switchWiFiMode(newMode);
	}

	private void setWiFiModeAsync(final WifiMode newMode) {
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					setWiFiMode(newMode);
				} catch (Exception e) {
					Log.v("BatPhone", e.toString(), e);
				}
			}
		};
		t.start();
	}

	private void releaseAlarm() {
		// kill the current alarm if there is one
		if (alarmIntent != null) {
			alarmManager.cancel(alarmIntent);
			alarmIntent = null;
			Log.v("BatPhone", "Cancelled alarm");
		}
	}

	private void setAlarm() {
		// create a new alarm to wake up the phone and switch modes.
		// TODO add percentage of randomness to timer

		releaseAlarm();
		alarmIntent = PendingIntent.getBroadcast(app, 0, new Intent(ALARM),
				PendingIntent.FLAG_UPDATE_CURRENT);

		int timer = currentMode.sleepTime * 1000;
		Log.v("BatPhone", "Set alarm for " + timer + "ms");
		alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
				+ timer, alarmIntent);
	}

	private void testClientState() {
		if (autoCycling && supplicantState != null) {
			// lock the mode if we start associating with any known
			// AP.
			switch (supplicantState) {
			case ASSOCIATED:
			case ASSOCIATING:
			case COMPLETED:
			case FOUR_WAY_HANDSHAKE:
			case GROUP_HANDSHAKE:
				if (!modeLocked)
					lockMode();
				break;
			default:
				if (modeLocked) {
					try {
						startCycling();
					} catch (IOException e) {
						Log.e("BatPhone", e.toString(), e);
					}
				}
				break;
			}
		}
	}

	private WifiMode findNextMode(WifiMode current) {
		while (true) {
			current = WifiMode.nextMode(current);
			if (ChipsetDetection.getDetection().isModeSupported(current))
				return current;
		}
	}

	private void nextMode() {
		if (modeLocked || !autoCycling)
			return;

		try {
			this.switchWiFiMode(findNextMode(lastActiveMode));
			setAlarm();
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}

	public void releaseControl() {
		releaseAlarm();
		modeLocked = false;
		autoCycling = false;
	}

	public void lockMode() {
		// ignore over enthusiastic callers
		if (modeLocked || !autoCycling)
			return;

		releaseAlarm();
		modeLocked = true;
		Log.v("BatPhone", "Locked mode to " + currentMode);
	}

	public void startCycling() throws IOException {
		// ignore over enthusiastic callers
		if (autoCycling && !modeLocked)
			return;

		Log.v("BatPhone", "Cycling modes");

		autoCycling = true;
		modeLocked = false;

		if (this.currentMode == null)
			nextMode();
		else {
			setAlarm();

			if (currentMode == WifiMode.Client) {
				testNetwork();
				testClientState();
			}
		}
	}

	public WifiMode getCurrentMode() {
		return currentMode;
	}

	public boolean isCycling() {
		return autoCycling;
	}

	private void waitForApState(int newState) throws IOException {
		while (true) {
			int state = wifiApManager.getWifiApState();
			if (state == newState)
				return;
			if (state == WifiManager.WIFI_STATE_UNKNOWN)
				throw new IOException(
						"Failed to control access point mode");
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}

	private void startAp() throws IOException {
		WifiConfiguration netConfig = new WifiConfiguration();
		netConfig.SSID = "BatPhone Installation";
		netConfig.allowedAuthAlgorithms
				.set(WifiConfiguration.AuthAlgorithm.OPEN);
		if (this.wifiManager.isWifiEnabled())
			this.wifiManager.setWifiEnabled(false);
		if (!this.wifiApManager.setWifiApEnabled(netConfig, true))
			throw new IOException("Failed to control access point mode");
		waitForApState(WifiManager.WIFI_STATE_ENABLED);
	}

	private void stopAp() throws IOException {
		if (!this.wifiApManager.setWifiApEnabled(null, false))
			throw new IOException("Failed to control access point mode");
		waitForApState(WifiManager.WIFI_STATE_DISABLED);
	}

	private void waitForClientState(int newState) throws IOException {
		while (true) {
			int state = this.wifiManager.getWifiState();
			if (state == newState)
				return;
			if (state == WifiManager.WIFI_STATE_UNKNOWN)
				throw new IOException(
						"Failed to control wifi client mode");

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}

	private boolean hasNetwork(String ssid) {
		for (WifiConfiguration network : wifiManager.getConfiguredNetworks()) {
			if (network.SSID.equals(ssid))
				return true;
		}
		return false;
	}

	private void testNetwork() {
		if (hasNetwork("BatPhone Installation"))
			return;
		WifiConfiguration netConfig = new WifiConfiguration();
		netConfig.SSID = "BatPhone Installation";
		netConfig.allowedAuthAlgorithms
				.set(WifiConfiguration.AuthAlgorithm.OPEN);
		wifiManager.addNetwork(netConfig);
	}

	private void startClient() throws IOException {
		testNetwork();
		if (this.wifiApManager != null && this.wifiApManager.isWifiApEnabled())
			this.wifiApManager.setWifiApEnabled(null, false);
		if (!this.wifiManager.setWifiEnabled(true))
			throw new IOException("Failed to control wifi client mode");
		waitForClientState(WifiManager.WIFI_STATE_ENABLED);
	}

	private void stopClient() throws IOException {
		if (!this.wifiManager.setWifiEnabled(false))
			throw new IOException("Failed to control wifi client mode");
		waitForClientState(WifiManager.WIFI_STATE_DISABLED);
	}

	private void startAdhoc() throws IOException {
		if (routingImp == null)
			throw new IllegalStateException("No routing protocol configured");

		// Get WiFi in adhoc mode and batmand running
		if (app.coretask.runRootCommand(app.coretask.DATA_FILE_PATH
				+ "/bin/adhoc start 1") != 0)
			throw new IOException("Failed to start adhoc mode");

		if (!routingImp.isRunning()) {
			Log.v("BatPhone", "Starting routing engine");
			routingImp.start();
		}
	}

	private void stopAdhoc() throws IOException {
		if (routingImp != null) {
			Log.v("BatPhone", "Stopping routing engine");
			this.routingImp.stop();
		}

		if (app.coretask.runRootCommand(app.coretask.DATA_FILE_PATH
				+ "/bin/adhoc stop 1") != 0)
			throw new IOException("Failed to stop adhoc mode");
	}

	private synchronized void switchWiFiMode(WifiMode newMode)
			throws IOException {
		if (newMode == currentMode)
			return;

		try {
			// stop paying attention to broadcast receivers while forcing a mode
			// change
			changing = true;

			if (currentMode != null) {
				Log.v("BatPhone", "Stopping " + currentMode);
				switch (currentMode) {
				case Ap:
					stopAp();
					break;
				case Adhoc:
					stopAdhoc();
					break;
				case Client:
					stopClient();
					break;
				}
			}

			if (newMode != null) {
				Log.v("BatPhone", "Starting " + newMode);
				switch (newMode) {
				case Ap:
					startAp();
					break;
				case Adhoc:
					startAdhoc();
					break;
				case Client:
					startClient();
					break;
				}
			}

			modeChanged(newMode, true);
		} catch (IOException e) {
			// if something went wrong, try to work out what the mode currently
			// is.
			changing = false;
			checkWifiMode();
			throw e;
		}
	}
}
