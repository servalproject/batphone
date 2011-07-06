package org.servalproject.system;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.SimpleWebServer;
import org.servalproject.StatusNotification;
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
	private boolean enabled = false;
	private boolean radioOn = false;
	private boolean softwareRunning = false;

	private StatusNotification statusNotification;
	private SimpleWebServer webServer;

	public MeshManager(ServalBatPhoneApplication app) {
		this.app = app;
		this.statusNotification = new StatusNotification(app);

		IntentFilter filter = new IntentFilter();
		filter.addAction(WiFiRadio.WIFI_MODE_ACTION);
		app.registerReceiver(this, filter);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (action.equals(WiFiRadio.WIFI_MODE_ACTION)) {
			String newMode = intent.getStringExtra(WiFiRadio.EXTRA_NEW_MODE);
			radioOn = !(newMode == null || newMode.equals("Off"));

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
		boolean wifiOn = radioOn;

		// if the software is disabled, or the radio has cycled to sleeping,
		// make sure everything is turned off.
		if (!enabled)
			wifiOn = false;

		statusNotification.updateNow();
		if (wifiOn == softwareRunning)
			return;

		if (wifiOn) {
			wakeLockOn();

			try {
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

		} else {
			try {
				this.statusNotification.hideStatusNotification();

				stopDna();

				if (SipdroidEngine.isRegistered()) {
					Log.v("BatPhone", "Halting SIP client");
					SipdroidEngine.getEngine().halt();
				}

				if (app.coretask.isProcessRunning("sbin/asterisk")) {
					Log.v("BatPhone", "Stopping asterisk");
					app.coretask.killProcess("sbin/asterisk", false);
				}

				if (webServer != null) {
					webServer.interrupt();
					webServer = null;
				}

			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}

			if (!enabled)
				wakeLockOff();
		}
		softwareRunning = wifiOn;
	}

	public void stopDna() throws IOException {
		if (app.coretask.isProcessRunning("sbin/dna")) {
			Log.v("BatPhone", "Stopping dna");
			app.coretask.killProcess("sbin/dna", false);
		}
	}

	public void restartDna() throws IOException {
		stopDna();
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
