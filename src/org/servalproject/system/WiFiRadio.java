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
import java.util.Properties;

import org.servalproject.Instrumentation;
import org.servalproject.Instrumentation.Variable;
import org.servalproject.LogActivity;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.shell.CommandLog;
import org.servalproject.shell.Shell;

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

	private int wifiState = WifiManager.WIFI_STATE_UNKNOWN;
	private int wifiApState = WifiApControl.WIFI_AP_STATE_FAILED;
	private SupplicantState supplicantState = null;
	private WifiInfo wifiInfo = null;

	// WifiManager
	private WifiManager wifiManager;
	private WifiApControl wifiApManager;
	private WifiDirect wifiDirect;

	private AlarmManager alarmManager;
	private ServalBatPhoneApplication app;

	private static final String ALARM = "org.servalproject.WIFI_ALARM";
	public static final String WIFI_MODE_ACTION = "org.servalproject.WIFI_MODE";
	public static final String EXTRA_NEW_MODE = "new_mode";
	public static final String EXTRA_CHANGING = "changing";
	public static final String EXTRA_CHANGE_PENDING = "change_pending";
	public static final String EXTRA_CONNECTED_SSID = "wifi_ssid";

	private static WiFiRadio wifiRadio;

	public static WiFiRadio getWiFiRadio(ServalBatPhoneApplication context,
			WifiMode interfaceMode) {
		if (wifiRadio == null)
			wifiRadio = new WiFiRadio(context, interfaceMode);
		return wifiRadio;
	}

	public static WiFiRadio getWiFiRadio(ServalBatPhoneApplication context) {
		return getWiFiRadio(context, null);
	}

	public String getSSID() {
		switch (currentMode) {
		case Client:
			if (wifiInfo != null)
				return wifiInfo.getSSID();
			break;
		case Ap:
			return wifiApManager.getWifiApConfiguration().SSID;
		case Adhoc:
			return app.getSsid();
		}
		return null;
	}

	private void updateIntent() {
		Intent modeChanged = new Intent(WIFI_MODE_ACTION);

		modeChanged.putExtra(EXTRA_CHANGING, this.changing);
		modeChanged.putExtra(EXTRA_NEW_MODE, currentMode.toString());
		modeChanged.putExtra(EXTRA_CHANGE_PENDING, alarmIntent != null);
		modeChanged.putExtra(EXTRA_CONNECTED_SSID, getSSID());

		app.sendStickyBroadcast(modeChanged);
	}

	void modeChanged(WifiMode newMode, boolean force) {
		if (!force && changing)
			return;

		if (currentMode == newMode && !changing)
			return;

		Log.v("BatPhone", "Wifi mode is now " + newMode);

		if (newMode != WifiMode.Client)
			wifiInfo = null;

		currentMode = newMode;
		PeerListService.clear();
		changing = false;
		this.setSoftLock(false);
		this.updateIntent();
		Instrumentation.valueChanged(Variable.WifiMode, currentMode.ordinal());
	}

	// check what android thinks the wifi radio is doing
	private WifiMode getMode(WifiMode interfaceMode) {
		if (wifiManager.isWifiEnabled())
			return WifiMode.Client;

		if (wifiApManager != null && wifiApManager.isWifiApEnabled())
			return WifiMode.Ap;

		if (this.wifiDirect != null && wifiDirect.isEnabled())
			// note that in later devices wifi direct cannot be controlled
			// directly and may only be enabled when wifi client is enabled
			return WifiMode.Direct;

		if (interfaceMode == null) {
			// Don't bother looking at the real wifi mode if we don't have root
			// permission or we don't support adhoc mode.
			if (!ChipsetDetection.getDetection()
					.isModeSupported(WifiMode.Adhoc))
				return WifiMode.Off;

			String interfaceName = app.coretask.getProp("wifi.interface");
			interfaceMode = WifiMode.getWiFiMode(interfaceName);
		}

		switch (interfaceMode) {
		case Adhoc:
		case Unknown:
		case Off:
			return interfaceMode;

		case Client:
			// wifi module may still be loaded even though wifi is off
			// or the device driver code has been compiled into the kernel
			// and therefore can't be unloaded

			// TODO is there a way to detect the difference?
			return WifiMode.Off;

		}

		return WifiMode.Unsupported;
	}

	// Get the current state based on the systems wifi enabled flags.
	private void checkWifiMode(String source) {
		checkWifiMode(source, null);
	}

	private void checkWifiMode(String source, WifiMode interfaceMode) {
		if (changing)
			return;

		WifiMode newMode = getMode(interfaceMode);

		if (newMode != null && currentMode != newMode) {
			Log.v("Batphone", "Detected mode " + newMode + " from " + source);
			modeChanged(newMode, false);
		}
	}

	private WiFiRadio(ServalBatPhoneApplication context, WifiMode interfaceMode) {
		this.app = context;
		this.alarmManager = (AlarmManager) app
				.getSystemService(Context.ALARM_SERVICE);

		try {
		if (Looper.myLooper() == null)
			Looper.prepare();
		} catch (Exception e) {

		}

		if (WifiDirect.canControl()) {
			wifiDirect = WifiDirect.getInstance(context, this);
		} else
			Log.v("BatPhone", "Wifi direct is not supported");

		// init wifiManager
		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		wifiApManager = WifiApControl.getApControl(wifiManager);

		wifiState = wifiManager.getWifiState();
		if (wifiApManager != null)
			wifiApState = wifiApManager.getWifiApState();

		checkWifiMode("Constructor", interfaceMode);

		try {
			ServalD.setConfig("interfaces.0.prefer_unicast",
					currentMode == WifiMode.Adhoc ? "0" : "1");
		} catch (ServalDFailureException e) {
			Log.e("WiFiRadio", e.getMessage(), e);
		}

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

					checkWifiMode(action);

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

					checkWifiMode(action);

				} else if (action
						.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
					supplicantState = intent
							.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
					Log.v("BatPhone", "Supplicant State: " + supplicantState);

					testClientState();

				} else if (action
						.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {

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

			// skip modes we can't automate
			if (current.sleepTime == 0)
				continue;

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
		for (int i = 0; i < 50; i++) {

			int state = wifiApManager.getWifiApState();
			if (state >= 10)
				state -= 10;

			if (state == newState)
				return;

			if (state == WifiApControl.WIFI_AP_STATE_FAILED
					|| state == WifiApControl.WIFI_AP_STATE_DISABLED)
				break;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		throw new IOException("Failed to control access point mode");
	}

	private void waitForApEnabled(boolean enabled) throws IOException {
		for (int i = 0; i < 50; i++) {
			if (enabled == this.wifiApManager.isWifiApEnabled())
				return;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		throw new IOException("Failed to control access point mode");
	}

	private void startAp() throws IOException {
		int tries = 0;

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

				waitForApEnabled(true);
				LogActivity.logMessage("adhoc", "Starting access-point mode",
						false);

				break;
			} catch (IOException e) {
				if (++tries >= 5) {
					LogActivity.logMessage("adhoc",
							"Starting access-point mode", true);
					throw e;
				}
				Log.e("BatPhone", "Failed", e);
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
		waitForApEnabled(false);
	}

	private void waitForClientState(int newState) throws IOException {
		for (int i = 0; i < 50; i++) {
			int state = this.wifiManager.getWifiState();
			if (state == newState)
				return;
			if (state == WifiManager.WIFI_STATE_UNKNOWN
					|| state == WifiManager.WIFI_STATE_DISABLED)
				break;

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		throw new IOException("Failed to control wifi client mode");
	}

	private void waitForClientEnabled(boolean enabled) throws IOException {
		for (int i = 0; i < 50; i++) {
			if (enabled == this.wifiManager.isWifiEnabled())
				return;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		throw new IOException("Failed to control wifi client mode");
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

		while (true) {
			try {
				if (this.wifiApManager != null
						&& this.wifiApManager.isWifiApEnabled())
					this.wifiApManager.setWifiApEnabled(null, false);

				if (!this.wifiManager.setWifiEnabled(true))
					throw new IOException("Failed to control wifi client mode");

				waitForClientState(WifiManager.WIFI_STATE_ENABLED);
				waitForClientEnabled(true);

				break;
			} catch (IOException e) {
				if (++tries >= 5) {
					LogActivity.logMessage("adhoc",
							"Failed to switch to WiFi client mode: "
									+ e.getMessage(), true);
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
		waitForClientEnabled(false);
	}

	private void waitForDirectEnabled(boolean enabled) throws IOException {
		for (int i = 0; i < 10; i++) {
			if (enabled == this.wifiDirect.isEnabled())
				return;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		throw new IOException("Failed to control wifi direct mode");
	}

	private void updateConfiguration(String ssid) {

		String txpower = app.settings.getString("txpowerpref", "disabled");
		String lannetwork = app.settings.getString("lannetworkpref", null);

		String ipaddr = null;
		int netbits = 8;

		if (lannetwork != null) {
			String[] pieces = lannetwork.split("/");
			if (pieces.length >= 1)
				ipaddr = pieces[0];
			if (pieces.length >= 2)
				netbits = Integer.parseInt(pieces[1]);
		}

		try {
			Properties props = new Properties();
			String adhoc = app.coretask.DATA_FILE_PATH + "/conf/adhoc.conf";

			props.load(new FileInputStream(adhoc));

			props.put("wifi.essid", ssid);
			props.put("ip.network", ipaddr);
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

	public synchronized void testAdhoc(Shell rootShell) throws IOException,
			InterruptedException {
		String ssid = "TestingMesh" + Math.random();
		changing = true;
		if (ssid.length() > 32)
			ssid = ssid.substring(0, 32);

		try {
			// make sure we aren't still in adhoc mode from a previous
			// install/test
			if (currentMode != WifiMode.Off)
				rootShell = stopCurrentMode(rootShell);

			try {
				Log.v("WifiRadio", "Attempting to start adhoc mode");
				startAdhoc(rootShell, ssid);
			} finally {
				Log.v("WifiRadio", "Attempting to stop adhoc mode");
				stopAdhoc(rootShell);
			}
		} finally {
			changing = false;
			checkWifiMode("Adhoc testing", WifiMode.getWiFiMode(rootShell));
		}
	}

	private void startAdhoc(Shell shell, String ssid) throws IOException {
		updateConfiguration(ssid);

		try {
			ServalD.setConfig("interfaces.0.prefer_unicast", "0");
		} catch (ServalDFailureException e) {
			Log.e("WiFiRadio", e.getMessage(), e);
		}

		// Get WiFi in adhoc mode
		CommandLog c = new CommandLog(app.coretask.DATA_FILE_PATH
				+ "/bin/adhoc start 1");
		WifiMode actualMode = null;

		shell.add(c);
		try {
			if (c.exitCode() != 0)
				throw new IOException("Failed to start adhoc mode");
		} catch (InterruptedException e) {
			IOException x = new IOException();
			x.initCause(e);
			throw x;
		}

		String interfaceName = app.coretask.getProp("wifi.interface");
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

		if (actualMode != WifiMode.Adhoc && actualMode != WifiMode.Unknown) {
			throw new IOException(
					"Failed to start Adhoc mode, mode ended up being '"
							+ actualMode + "'");
		}
	}

	private void stopAdhoc(Shell shell) throws IOException {
		WifiMode actualMode = null;

		try {
			if (shell.run(new CommandLog(app.coretask.DATA_FILE_PATH
					+ "/bin/adhoc stop 1")) != 0)
				throw new IllegalStateException("Failed to stop adhoc mode");
		} catch (InterruptedException e) {
			IOException x = new IOException();
			x.initCause(e);
			throw x;
		}

		try {
			ServalD.setConfig("interfaces.0.prefer_unicast", "1");
		} catch (ServalDFailureException e) {
			Log.e("WiFiRadio", e.getMessage(), e);
		}

		String interfaceName = app.coretask.getProp("wifi.interface");
		for (int i = 0; i < 30; i++) {
			actualMode = WifiMode.getWiFiMode(shell, interfaceName);
			// We need to allow unknown for wifi drivers that lack linux
			// wireless extensions
			if (actualMode == WifiMode.Off
					|| actualMode == WifiMode.Unknown)
				break;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Log.e("BatPhone", e.toString(), e);
			}
		}

		Log.v("BatPhone", "iwconfig;\n" + WifiMode.lastIwconfigOutput);

		if ((actualMode != WifiMode.Off) && (actualMode != WifiMode.Unknown)) {
			// Well it looks like we failed to unload the wifi module, or the
			// wifi driver is built into the kernel and can't be shut down
			// we may have broken wifi completely. But instead of jumping to
			// conclusions, lets test wifi client using the API and see if it
			// works.

			try {
				Log.w("BatPhone",
						"Wifi state looks odd after turning off adhoc.");
				Log.w("BatPhone",
						"I'm going to toggle wifi client on/off to make sure I haven't broken anything");

				this.startClient();
				this.stopClient();

			} catch (IOException e) {

				// Oh dear, this looks bad.

				LogActivity
						.logMessage(
								"adhoc",
								"Failed to stop adhoc mode, mode ended up being "
										+ actualMode
										+ " when I tried to turn it off, *and* android wifi appears to be broken",
								false);
				throw new IllegalStateException(
						"Failed to stop Adhoc mode, mode ended up being '"
								+ actualMode + "' instead of '" + WifiMode.Off
								+ "'", e);
			}
		}
	}

	private synchronized Shell stopCurrentMode(Shell shell) throws IOException {
		Log.v("BatPhone", "Stopping " + currentMode);
		switch (currentMode) {
		case Ap:
			stopAp();
			break;
		case Direct:
			if (wifiDirect == null || !WifiDirect.canControl())
				throw new IOException();
			wifiDirect.stop();
			waitForDirectEnabled(false);
			break;
		case Off:
			break;
		case Adhoc:
			if (shell == null)
				shell = Shell.startRootShell();
			stopAdhoc(shell);
			break;
		case Client:
			stopClient();
			break;
		}
		PeerListService.clear();
		return shell;
	}

	private synchronized void switchWiFiMode(WifiMode newMode)
			throws IOException {

		// Sometimes we REALLY want to make sure the wifi is off, like
		// during installation.
		if (newMode == currentMode)
			return;

		Shell shell = null;
		try {
			// stop paying attention to broadcast receivers while forcing a mode
			// change
			changing = true;

			releaseAlarm();
			updateIntent();

			shell = stopCurrentMode(shell);

			Log.v("BatPhone", "Setting mode to " + newMode);
			switch (newMode) {
			case Ap:
				startAp();
				break;
			case Direct:
				if (wifiDirect == null || !WifiDirect.canControl())
					throw new IOException();
				wifiDirect.start();
				waitForDirectEnabled(true);
				break;
			case Adhoc:
				if (shell == null)
					shell = Shell.startRootShell();
				try {
					String ssid = app.getSsid();

					LogActivity.logErase("adhoc");
					startAdhoc(shell, ssid);
				} catch (IOException e) {
					Log.v("BatPhone",
									"Start Adhoc failed, attempting to stop again before reporting error");
					try {
						stopAdhoc(shell);
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
			updateIntent();
			checkWifiMode("Mode change failure");
			throw e;
		} finally {
			try {
				if (shell != null)
					shell.waitFor();
			} catch (InterruptedException e) {
				IOException x = new IOException();
				x.initCause(e);
				throw x;
			}
		}
	}

	public void screenTurnedOff() {
		// Some chipsets turn on a broadcast packet filter when the screen
		// goes off. So we need to stop and start the wifi driver when that
		// happens (since the filter starting is edge-triggered).
		// XXX - We could also support edify scripts to access these modes with
		// the filter disabled, but this will do for now.
		if (false) {
			checkWifiMode("Screen Off");
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
