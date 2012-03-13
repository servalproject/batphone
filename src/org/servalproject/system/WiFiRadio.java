/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.servalproject.system;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import org.servalproject.Instrumentation;
import org.servalproject.Instrumentation.Variable;
import org.servalproject.LogActivity;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.batman.Batman;
import org.servalproject.batman.None;
import org.servalproject.batman.Olsr;
import org.servalproject.batman.PeerFinder;
import org.servalproject.batman.PeerParser;
import org.servalproject.batman.PeerRecord;
import org.servalproject.batman.Routing;
import org.servalproject.dna.Dna;
import org.servalproject.rhizome.PeerWatcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Looper;
import android.util.Log;

public class WiFiRadio {

	private WifiMode currentMode = WifiMode.Off;
	private PendingIntent alarmIntent;

	private boolean changing = false;

	// does the user want us to automatically control wifi modes?
	private boolean autoCycling = false;

	// a lock on the wifi mode based on finding peers
	private boolean softLock = false;

	// a lock on the wifi mode based on user intervention
	private boolean hardLock = false;

	// how many cycles has it been since we saw a peer?
	private int cyclesSincePeer = 0;

	/** The thread that looks for updates */
	private PeerWatcher pWatcher;

	private int wifiState = WifiManager.WIFI_STATE_UNKNOWN;
	private int wifiApState = WifiApControl.WIFI_AP_STATE_FAILED;
	private SupplicantState supplicantState = null;
	private WifiInfo wifiInfo = null;
	private Routing routingImp;

	// WifiManager
	private WifiManager wifiManager;
	private WifiApControl wifiApManager;
	private AlarmManager alarmManager;
	private ServalBatPhoneApplication app;

	private static final String ALARM = "org.servalproject.WIFI_ALARM";
	public static final String WIFI_MODE_ACTION = "org.servalproject.WIFI_MODE";
	public static final String EXTRA_NEW_MODE = "new_mode";
	public static final String EXTRA_CHANGING = "changing";
	public static final String EXTRA_CHANGE_PENDING = "change_pending";
	public static final String EXTRA_CONNECTED_SSID = "wifi_ssid";

	private static WiFiRadio wifiRadio;

	public static WiFiRadio getWiFiRadio(ServalBatPhoneApplication context) {
		if (wifiRadio == null)
			wifiRadio = new WiFiRadio(context);
		return wifiRadio;
	}

	private void updateIntent() {
		Intent modeChanged = new Intent(WIFI_MODE_ACTION);

		modeChanged.putExtra(EXTRA_CHANGING, this.changing);
		modeChanged.putExtra(EXTRA_NEW_MODE, currentMode.toString());
		modeChanged.putExtra(EXTRA_CHANGE_PENDING, alarmIntent != null);

		switch (currentMode) {
		case Client:
			if (wifiInfo != null)
				modeChanged.putExtra(EXTRA_CONNECTED_SSID, wifiInfo.getSSID());
			break;
		case Ap: {
			WifiConfiguration config = wifiApManager.getWifiApConfiguration();
			modeChanged.putExtra(EXTRA_CONNECTED_SSID, config.SSID);
			break;
		}
		case Adhoc:
			modeChanged.putExtra(EXTRA_CONNECTED_SSID, app.getSsid());
		}

		app.sendStickyBroadcast(modeChanged);
	}

	private void modeChanged(WifiMode newMode, boolean force) {
		if (!force && changing)
			return;

		if (currentMode == newMode && !changing)
			return;

		Log.v("BatPhone", "Wifi mode is now " + newMode);

		if (peerFinder != null) {
			peerFinder.close();
			peerFinder = null;
		}

		Dna.clearBroadcastAddresses();

		if (newMode == WifiMode.Client || newMode == WifiMode.Ap) {
			peerFinder = new PeerFinder(app);
			peerFinder.start();
		}

		if (newMode != WifiMode.Client)
			wifiInfo = null;

		currentMode = newMode;
		changing = false;
		this.setSoftLock(false);
		this.updateIntent();
		Instrumentation.valueChanged(Variable.WifiMode, currentMode.ordinal());

		if (newMode != WifiMode.Off) {
			// Wifi On, so turn Rhizome on
			// Launch the updater thread with the peer list object
			if (pWatcher == null) {
				pWatcher = new PeerWatcher();
				pWatcher.start();
			} else
				pWatcher.interrupt();
		} else {
			// Wifi Off, so turn Rhizome off
			// apparently .stop() is deprecated. Ogh
			// if (pWatcher != null)
			// pWatcher.stop();
		}

	}

	// Get the current state based on the systems wifi enabled flags.
	private void checkWifiMode() {

		if (wifiManager.isWifiEnabled()) {
			modeChanged(WifiMode.Client, false);
			return;
		}

		if (wifiApManager != null && wifiApManager.isWifiApEnabled()) {
			modeChanged(WifiMode.Ap, false);
			return;
		}

		String interfaceName = app.coretask.getProp("wifi.interface");
		WifiMode actualMode = WifiMode.getWiFiMode(interfaceName);
		switch (actualMode) {
		case Adhoc:
		case Unknown:
		case Off:
			modeChanged(actualMode, false);
			break;
		default:
			modeChanged(WifiMode.Unsupported, false);
		}
	}

	private WiFiRadio(ServalBatPhoneApplication context) {
		this.app = context;
		this.alarmManager = (AlarmManager) app
				.getSystemService(Context.ALARM_SERVICE);

		createRoutingImp();

		try {
		if (Looper.myLooper() == null)
			Looper.prepare();
		} catch (Exception e) {

		}

		// init wifiManager
		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		wifiApManager = WifiApControl.getApControl(wifiManager);

		wifiState = wifiManager.getWifiState();
		if (wifiApManager != null)
			wifiApState = wifiApManager.getWifiApState();

		checkWifiMode();

		// receive wifi state broadcasts.
		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
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

					// make sure we release any soft lock
					if (wifiState != WifiManager.WIFI_STATE_ENABLED
							&& supplicantState != null) {
						supplicantState = null;
						Log.v("BatPhone", "Supplicant State: null");
						testClientState();
					}

					// if the user tries to enable wifi, and we're running adhoc
					// their attempt will fail, but we can finish it for them
					if (!changing
							&& wifiState == WifiManager.WIFI_STATE_ENABLING
							&& currentMode == WifiMode.Adhoc)
						setWiFiModeAsync(WifiMode.Client);

					checkWifiMode();

				} else if (action
						.equals(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION)) {

					int newState = intent.getIntExtra(
							WifiApControl.EXTRA_WIFI_AP_STATE,
							WifiApControl.WIFI_AP_STATE_FAILED);

					if (newState >= 10)
						newState -= 10;
					wifiApState = newState;
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

					if (peerFinder != null)
						peerFinder.clear();

					testClientState();

				} else if (action
						.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {

					if (peerFinder != null)
						peerFinder.clear();

					testClientState();

				} else if (action.equals(ALARM)) {
					// TODO WAKE LOCK!!!!?
					Log.v("BatPhone", "Alarm firing...");

					// We really shouldn't do this in the main UI thread.
					new Thread() {
						@Override
						public void run() {
							nextMode();
						}
					}.start();
				}
			}
		}, filter);

		// setup autocycling based on the preference, do this last so we have a
		// change of receiving the current wifi status before we set our alarm
		setAutoCycling(app.settings.getBoolean("wifi_auto", true));

	}

	public void setAutoCycling(boolean autoCycling) {
		this.autoCycling = autoCycling;
		checkAlarm();
	}

	public void setSoftLock(boolean enabled) {
		softLock = enabled;

		// don't turn off on the next cycle if we have found peers on this one.
		if (enabled) {
			cyclesSincePeer = 0;
		}

		checkAlarm();
	}

	public void setHardLock(boolean enabled) {
		hardLock = enabled;
		checkAlarm();
	}

	private void createRoutingImp() {
		String routing = app.settings.getString("routingImpl", "batman");
		if (routing.equals("batman")) {
			Log.v("BatPhone", "Using batman routing");
			this.routingImp = new Batman(app.coretask);
		} else if (routing.equals("olsr")) {
			Log.v("BatPhone", "Using olsr routing");
			this.routingImp = new Olsr(app.coretask);
		} else if (routing.equals("none")) {
			this.routingImp = new None(app.coretask);
		} else {
			Log.e("BatPhone", "Unknown routing implementation " + routing);
			this.routingImp = null;
		}
	}

	public void setRouting() throws IOException {
		boolean running = (routingImp == null ? false : routingImp.isRunning());

		if (running)
			routingImp.stop();

		createRoutingImp();

		if (running && routingImp != null)
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

	public int getPeerCount() {
		int ret = 1;
		PeerParser parser = getPeerParser();
		if (parser != null)
			try {
				ret = parser.getPeerCount();
			} catch (IOException e) {
				Log.e("BatPhone", e.toString());
			}

		// probably the wrong place for this, as it depends on being called
		// frequently, but this doesn't really belong in StatusNotification...
		if (currentMode == WifiMode.Adhoc)
			setSoftLock(ret > 1);

		return ret;
	}

	public void setWiFiMode(WifiMode newMode) throws IOException {
		if (newMode == null)
			newMode = WifiMode.Off;

		if (!ChipsetDetection.getDetection().isModeSupported(newMode))
			throw new IOException("Wifi mode " + newMode + " is not supported");

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
			updateIntent();
		}
	}

	// scale the cycling times consistently by a random amount of up to 30%
	private double scale = Math.random() * 0.3;

	public void checkAlarm() {
		if (changing || app.getState() != State.On || softLock || hardLock
				|| !autoCycling) {
			releaseAlarm();
		} else {
			if (alarmIntent == null) {
				alarmIntent = PendingIntent.getBroadcast(app, 0, new Intent(
						ALARM), PendingIntent.FLAG_UPDATE_CURRENT);

				int timer = currentMode.sleepTime * 1000
						* Math.min(cyclesSincePeer + 1, 4);

				// increase the timer randomly by up to 15%
				timer += (int) (timer * scale);

				Log.v("BatPhone", "Set alarm for " + timer + "ms");
				alarmManager.set(AlarmManager.RTC_WAKEUP, System
						.currentTimeMillis()
						+ timer, alarmIntent);
				updateIntent();
			}
		}
	}

	private void testClientState() {
		if (currentMode != WifiMode.Client) {
			if (wifiInfo != null) {
				wifiInfo = null;
				updateIntent();
			}
			return;
		}

		if (supplicantState != null) {
			// lock the mode if we start associating with any known
			// AP.
			switch (supplicantState) {
			case ASSOCIATED:
			case ASSOCIATING:
			case COMPLETED:
			case FOUR_WAY_HANDSHAKE:
			case GROUP_HANDSHAKE:
				setSoftLock(true);
				Dna.clearBroadcastAddresses();
				wifiInfo = wifiManager.getConnectionInfo();
				updateIntent();
				return;
			}
		}

		setSoftLock(false);
		updateIntent();
	}

	private WifiMode findNextMode(WifiMode current) {
		while (true) {
			current = WifiMode.nextMode(current);

			if (current == WifiMode.Off) {
				cyclesSincePeer++;
				// skip turning off for the first couple of cycles
				if (cyclesSincePeer <= 2)
					continue;
			}

			if (ChipsetDetection.getDetection().isModeSupported(current))
				return current;
		}
	}

	private void nextMode() {
		if (app.getState() != State.On || softLock || hardLock || !autoCycling)
			return;

		try {
			this.switchWiFiMode(findNextMode(currentMode));
		} catch (Exception e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}

	// make sure the radio is on
	public void turnOn() throws IOException {

		// force more rapid cycling initially.
		cyclesSincePeer = 0;

		if (currentMode == WifiMode.Off)
			this.switchWiFiMode(findNextMode(currentMode));
		else
			wifiRadio.checkAlarm();

		if (currentMode == WifiMode.Client)
			testNetwork();
	}

	public WifiMode getCurrentMode() {
		return currentMode;
	}

	private void waitForApState(int newState) throws IOException {
		while (true) {
			int state = wifiApManager.getWifiApState();
			if (state >= 10)
				state -= 10;

			if (state == newState)
				return;
			if (state == WifiApControl.WIFI_AP_STATE_FAILED
					|| state == WifiApControl.WIFI_AP_STATE_DISABLED)
				throw new IOException("Failed to control access point mode");
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}

	private void startAp() throws IOException {
		int tries = 0;

		LogActivity.logErase("adhoc");

		WifiConfiguration netConfig = new WifiConfiguration();
		netConfig.SSID = app.getSsid();
		netConfig.allowedAuthAlgorithms
				.set(WifiConfiguration.AuthAlgorithm.OPEN);

		while (true) {
			try {
				if (this.wifiManager.isWifiEnabled())
					this.wifiManager.setWifiEnabled(false);
				if (!this.wifiApManager.setWifiApEnabled(netConfig, true))
					throw new IOException("Failed to control access point mode");
				waitForApState(WifiApControl.WIFI_AP_STATE_ENABLED);
				LogActivity.logMessage("adhoc", "Starting access-point mode",
						false);

				break;
			} catch (IOException e) {
				if (++tries >= 5) {
					LogActivity.logMessage("adhoc",
							"Starting access-point mode", true);
					throw e;
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
				}
			}
		}
	}

	private void stopAp() throws IOException {
		if (!this.wifiApManager.setWifiApEnabled(null, false))
			throw new IOException("Failed to control access point mode");
		waitForApState(WifiApControl.WIFI_AP_STATE_DISABLED);
		LogActivity.logMessage("adhoc", "Stopped access-point mode", false);

	}

	private void waitForClientState(int newState) throws IOException {
		while (true) {
			int state = this.wifiManager.getWifiState();
			if (state == newState)
				return;
			if (state == WifiManager.WIFI_STATE_UNKNOWN
					|| state == WifiManager.WIFI_STATE_DISABLED)
				throw new IOException("Failed to control wifi client mode");

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}

	private boolean hasNetwork(String ssid) {
		for (WifiConfiguration network : wifiManager.getConfiguredNetworks()) {
			if (network.SSID.equals(ssid)
					|| network.SSID.equals("\"" + ssid + "\""))
				return true;
		}
		return false;
	}

	private void testNetwork() {
		String ssid = app.getSsid();
		if (hasNetwork(ssid))
			return;
		WifiConfiguration netConfig = new WifiConfiguration();
		netConfig.SSID = ssid;
		netConfig.allowedAuthAlgorithms
				.set(WifiConfiguration.AuthAlgorithm.OPEN);
		int id = wifiManager.addNetwork(netConfig);
		if (id == -1) {
			netConfig.SSID = "\"" + netConfig.SSID + "\"";
			id = wifiManager.addNetwork(netConfig);
		}

		if (id == -1)
			Log
					.v("BatPhone", "Failed to add network configuration for "
							+ ssid);
		else {
			Log.v("BatPhone", "Added network " + id + " for " + ssid);
			wifiManager.enableNetwork(id, false);
		}
	}

	private void startClient() throws IOException {
		int tries = 0;

		LogActivity.logErase("adhoc");

		while (true) {
			try {
				if (this.wifiApManager != null
						&& this.wifiApManager.isWifiApEnabled())
					this.wifiApManager.setWifiApEnabled(null, false);

				if (!this.wifiManager.setWifiEnabled(true))
					throw new IOException("Failed to control wifi client mode");

				waitForClientState(WifiManager.WIFI_STATE_ENABLED);
				LogActivity.logMessage("adhoc",
						"Switching to WiFi client mode", false);

				break;
			} catch (IOException e) {
				if (++tries >= 5) {
					LogActivity.logMessage("adhoc",
							"Switching to WiFi client mode", true);
					throw e;

				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
				}
			}
		}
		testNetwork();
	}

	private void stopClient() throws IOException {
		if (!this.wifiManager.setWifiEnabled(false))
			throw new IOException("Failed to control wifi client mode");
		waitForClientState(WifiManager.WIFI_STATE_DISABLED);
		LogActivity.logMessage("adhoc", "Stopped client mode", false);
	}

	private void updateConfiguration(String ssid) {

		String txpower = app.settings.getString("txpowerpref", "disabled");
		String lannetwork = app.settings.getString("lannetworkpref",
				ServalBatPhoneApplication.DEFAULT_LANNETWORK);

		String[] pieces = lannetwork.split("/");
		String ipaddr = pieces[0];

		try {
			Properties props = new Properties();
			String adhoc = app.coretask.DATA_FILE_PATH + "/conf/adhoc.conf";

			props.load(new FileInputStream(adhoc));

			props.put("wifi.essid", ssid);
			props.put("ip.network", ipaddr);
			int netbits = 8;
			if (pieces.length > 1)
				netbits = Integer.parseInt(pieces[1]);
			props.put("ip.netmask", app.netSizeToMask(netbits));
			props.put("ip.gateway", ipaddr);
			props.put("wifi.interface", app.coretask.getProp("wifi.interface"));
			props.put("wifi.txpower", txpower);

			props.store(new FileOutputStream(adhoc), null);
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}

		String find[] = new String[] { "WiFiAdhoc", "dot11DesiredSSID",
				"dot11DesiredChannel", "dot11DesiredBSSType", "dot11PowerMode" };
		String replace[] = new String[] {
				"1",
				app.settings.getString("ssidpref",
						ServalBatPhoneApplication.DEFAULT_SSID),
				app.settings.getString("channelpref",
						ServalBatPhoneApplication.DEFAULT_CHANNEL), "0", "1" };

		app
				.replaceInFile("/system/etc/wifi/tiwlan.ini",
						app.coretask.DATA_FILE_PATH + "/conf/tiwlan.ini", find,
						replace);
	}

	public synchronized void testAdhoc() throws IOException {
		// make sure we aren't still in adhoc mode from a previous install /
		// test
		String interfaceName = app.coretask.getProp("wifi.interface");
		if (WifiMode.getWiFiMode(interfaceName) != WifiMode.Off)
			setWiFiMode(WifiMode.Off);

		try {
			String ssid = "TestingMesh" + Math.random();
			if (ssid.length() > 32)
				ssid = ssid.substring(0, 32);
			startAdhoc(ssid);
		} finally {
			stopAdhoc();
		}
	}

	private void startAdhoc(String ssid) throws IOException {
		LogActivity.logErase("adhoc");
		updateConfiguration(ssid);

		// Get WiFi in adhoc mode and batmand running
		String cmd = app.coretask.DATA_FILE_PATH + "/bin/adhoc start 1";
		LogActivity.logMessage("adhoc", "About to run " + cmd, false);
		if (app.coretask.runRootCommand(cmd) != 0) {
			LogActivity.logMessage("adhoc", "Executing '" + cmd + "'", true);
			throw new IOException("Failed to start adhoc mode");
		}

		WifiMode actualMode = null;
		String interfaceName = app.coretask.getProp("wifi.interface");
		for (int i = 0; i < 30; i++) {
			actualMode = WifiMode.getWiFiMode(interfaceName);
			// We need to allow unknown for wifi drivers that lack linux
			// wireless extensions
			if (actualMode == WifiMode.Adhoc || actualMode == WifiMode.Unknown)
				break;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Log.e("BatPhone", e.toString(), e);
			}
		}

		if (actualMode != WifiMode.Adhoc && actualMode != WifiMode.Unknown) {
			Log.v("BatPhone", "iwconfig;\n" + WifiMode.lastIwconfigOutput);
			throw new IOException(
					"Failed to start Adhoc mode, mode ended up being '"
							+ actualMode + "'");
		}
	}

	private void stopAdhoc() throws IOException {
		if (app.coretask.runRootCommand(app.coretask.DATA_FILE_PATH
				+ "/bin/adhoc stop 1") != 0) {
			LogActivity.logMessage("adhoc", "'adhoc stop 1' return code != 0",
					false);
			throw new IllegalStateException("Failed to stop adhoc mode");
		}

		WifiMode actualMode = null;
		String interfaceName = app.coretask.getProp("wifi.interface");
		for (int i = 0; i < 30; i++) {
			actualMode = WifiMode.getWiFiMode(interfaceName);
			// We need to allow unknown for wifi drivers that lack linux
			// wireless extensions
			if (actualMode == WifiMode.Off || actualMode == WifiMode.Unknown)
				break;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Log.e("BatPhone", e.toString(), e);
			}
		}

		if ((actualMode != WifiMode.Off) && (actualMode != WifiMode.Unknown)) {
			LogActivity.logMessage("adhoc",
							"Failed to stop adhoc mode, mode ended up being "
									+ actualMode + " instead of '"
									+ WifiMode.Off + "'", false);
			throw new IllegalStateException(
					"Failed to stop Adhoc mode, mode ended up being '"
						+ actualMode + "' instead of '" + WifiMode.Off + "'");
		}
	}

	private synchronized void switchWiFiMode(WifiMode newMode)
			throws IOException {

		// Sometimes we REALLY want to make sure the wifi is off, like
		// during installation.
		if (newMode == currentMode)
			return;

		try {
			// stop paying attention to broadcast receivers while forcing a mode
			// change
			changing = true;

			releaseAlarm();
			updateIntent();

			Log.v("BatPhone", "Stopping " + currentMode);
			switch (currentMode) {
			case Ap:
				stopAp();
				break;
			case Off:
				break;
			case Adhoc:
				if (routingImp != null) {
					Log.v("BatPhone", "Stopping routing engine");
					LogActivity.logMessage("adhoc",
							"Calling routingImp.stop()", false);
					this.routingImp.stop();
				}

				stopAdhoc();
				break;
			case Client:
				stopClient();
				break;
			}

			Log.v("BatPhone", "Setting mode to " + newMode);
			switch (newMode) {
			case Ap:
				startAp();
				break;
			case Adhoc:
				try {
					if (routingImp == null)
						throw new IllegalStateException(
								"No routing protocol configured");

					String ssid = app.getSsid();
					startAdhoc(ssid);

					if (!routingImp.isRunning()) {
						Log.v("BatPhone", "Starting routing engine");
						routingImp.start();
					}
				} catch (IOException e) {
					Log.v("BatPhone",
									"Start Adhoc failed, attempting to stop again before reporting error");
					try {
						stopAdhoc();
					} catch (IOException x) {
						Log.w("BatPhone", x);
					}
					throw e;
				}
				break;
			case Client:
				startClient();
				break;
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

	public void screenTurnedOff() {
		// Some chipsets turn on a broadcast packet filter when the screen
		// goes off. So we need to stop and start the wifi driver when that
		// happens (since the filter starting is edge-triggered).
		// XXX - We could also support edify scripts to access these modes with
		// the filter disabled, but this will do for now.
		if (false) {
			checkWifiMode();
			WifiMode m = this.currentMode;
			switch (m) {
			case Ap:
			case Client:
				try {
					switchWiFiMode(WifiMode.Off);
				} catch (IOException e) {
				}
				try {
					switchWiFiMode(m);
				} catch (IOException e) {
				}
				break;
			}
		}
	}
}
