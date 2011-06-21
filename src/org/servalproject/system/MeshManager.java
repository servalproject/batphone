package org.servalproject.system;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.SimpleWebServer;
import org.servalproject.StatusNotification;
import org.servalproject.batman.Batman;
import org.servalproject.batman.Olsr;
import org.servalproject.batman.Routing;
import org.servalproject.system.WiFiRadio.WifiMode;
import org.sipdroid.sipua.SipdroidEngine;
import org.zoolu.net.IpAddress;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.util.Log;

public class MeshManager extends BroadcastReceiver {
	private ServalBatPhoneApplication app;
	private PowerManager.WakeLock wakeLock = null;
	private Routing routingImp;
	private boolean enabled = false;

	private WiFiRadio.WifiMode currentMode = null;
	private WiFiRadio.WifiMode mode = null;

	private StatusNotification statusNotification;
	private SimpleWebServer webServer;

	public MeshManager(ServalBatPhoneApplication app) {
		this.app = app;
		this.statusNotification = new StatusNotification(app);

		createRoutingImp();

		IntentFilter filter = new IntentFilter();
		filter.addAction(WiFiRadio.WIFI_MODE_ACTION);
		app.registerReceiver(this, filter);
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

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (action.equals(WiFiRadio.WIFI_MODE_ACTION)) {
			String newMode = intent.getStringExtra(WiFiRadio.EXTRA_NEW_MODE);
			if (newMode == null) {
				mode = null;
			} else {
				mode = WiFiRadio.WifiMode.valueOf(newMode);
			}
			Log.v("BatPhone", "Mode is now " + (mode == null ? "null" : mode));

			if (enabled) {
				new Thread() {
					@Override
					public void run() {
						modeChanged();
					}
				}.start();
			}
		}
	}

	private void wakeLockOn() {
		if (enabled && app.settings.getBoolean("wakelockpref", true)) {
			if (this.wakeLock == null) {
				PowerManager powerManager = (PowerManager) app
						.getSystemService(Context.POWER_SERVICE);
				wakeLock = powerManager.newWakeLock(
						PowerManager.PARTIAL_WAKE_LOCK, "ADHOC_WAKE_LOCK");
			}
			this.wakeLock.acquire();
		}
	}

	private void wakeLockOff() {
		if (this.wakeLock != null && this.wakeLock.isHeld()) {
			this.wakeLock.release();
		}
	}

	public void wakeLockChanged(boolean lockValue) {
		if (lockValue)
			wakeLockOn();
		else
			wakeLockOff();
	}

	public void setEnabled(final boolean enabled) {
		if (this.enabled == enabled)
			return;

		this.enabled = enabled;
		wakeLockOn();

		new Thread() {
			@Override
			public void run() {
				modeChanged();
			}
		}.start();
	}

	private synchronized void modeChanged() {
		WifiMode newMode = mode;

		// if the software is disabled, or the radio has cycled to sleeping,
		// turn off.
		if (!enabled || newMode == WifiMode.Sleep)
			newMode = null;

		if (newMode == currentMode)
			return;

		if (newMode == null) {
			try {
				if (routingImp != null) {
					Log.v("BatPhone", "Stopping routing engine");
					this.routingImp.stop();
				}

				this.statusNotification.hideStatusNotification();
				SipdroidEngine.getEngine().halt();

				if (webServer != null) {
					webServer.interrupt();
					webServer = null;
				}

			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}
			if (!enabled)
				wakeLockOff();
		} else {

			wakeLockOn();

			try {
				if (currentMode == WifiMode.Adhoc) {
					if (routingImp != null && routingImp.isRunning()) {
						Log.v("BatPhone", "Stopping routing engine");
						routingImp.stop();
					}
				}

				if (newMode == WifiMode.Adhoc) {
					if (routingImp == null)
						throw new IllegalStateException(
								"No routing protocol configured");
					if (!routingImp.isRunning()) {
						Log.v("BatPhone", "Starting routing engine");
						routingImp.start();
					}
				}

				startDna();

				if (!app.coretask.isProcessRunning("sbin/asterisk")) {
					Log.v("BatPhone", "Starting asterisk");
					app.coretask.runCommand(app.coretask.DATA_FILE_PATH
							+ "/asterisk/sbin/asterisk");
				}

				IpAddress.localIpAddress = Inet4Address.getLocalHost()
						.getHostAddress();

				if (!SipdroidEngine.isRegistered()) {
					Log.v("BatPhone", "Starting SIP client");
					SipdroidEngine.getEngine().StartEngine();
				}

				statusNotification.showStatusNotification();

				if (webServer == null)
					webServer = new SimpleWebServer(new File(
							app.coretask.DATA_FILE_PATH + "/htdocs"), 8080);

			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}

		}

		currentMode = newMode;
	}

	public void restartDna() throws Exception {
		app.coretask.killProcess("bin/dna", false);
		startDna();
	}

	public void startDna() throws IOException {
		if (!app.coretask.isProcessRunning("bin/dna")) {
			Log.v("BatPhone", "Starting DNA");
			boolean instrumentation = app.settings.getBoolean("instrument_rec",
					false);
			Boolean gateway = app.settings.getBoolean("gatewayenable", false);

			app.coretask.runCommand(app.coretask.DATA_FILE_PATH
					+ "/bin/dna "
					+ (instrumentation ? "-L "
							+ app.getStorageFolder().getAbsolutePath()
							+ "/instrumentation.log " : "")
					+ (gateway ? "-G yes_please " : "") + "-S 1 -f "
					+ app.coretask.DATA_FILE_PATH + "/var/hlr.dat");
		}
	}

}
