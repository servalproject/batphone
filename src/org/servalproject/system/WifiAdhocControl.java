package org.servalproject.system;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.shell.CommandLog;
import org.servalproject.shell.Shell;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;

public class WifiAdhocControl {
	private final WifiControl control;
	private final ServalBatPhoneApplication app;
	private final ChipsetDetection detection;
	private static final String TAG = "AdhocControl";
	private int state = ADHOC_STATE_DISABLED;
	private WifiAdhocNetwork config;

	public static final String ADHOC_STATE_CHANGED_ACTION = "org.servalproject.ADHOC_STATE_CHANGED_ACTION";
	public static final String EXTRA_SSID = "extra_ssid";
	public static final String EXTRA_STATE = "extra_state";
	public static final String EXTRA_PREVIOUS_STATE = "extra_previous_state";

	public static final int ADHOC_STATE_DISABLED = 0;
	public static final int ADHOC_STATE_ENABLING = 1;
	public static final int ADHOC_STATE_ENABLED = 2;
	public static final int ADHOC_STATE_DISABLING = 3;
	public static final int ADHOC_STATE_ERROR = 4;

	private Map<String, WifiAdhocNetwork> adhocNetworks = new HashMap<String, WifiAdhocNetwork>();

	private void readProfile(String name) throws UnknownHostException {
		String prefsName = "adhoc_" + name;
		SharedPreferences prefs = app.getSharedPreferences(prefsName,
				0);
		WifiAdhocNetwork network = WifiAdhocNetwork
				.getAdhocNetwork(prefs, name);
		adhocNetworks.put(network.SSID, network);
	}

	private void readProfiles() {
		String profiles = this.app.settings.getString("adhoc_profiles", null);
		if (profiles != null) {
			String names[] = profiles.split(",");
			for (String name : names) {
				try {
					if (name != null && !name.equals(""))
						readProfile(name);
				} catch (UnknownHostException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
		}
		if (adhocNetworks.isEmpty()) {
			try {
				String name = "default";
				String prefsName = "adhoc_" + name;
				PreferenceManager.setDefaultValues(app, prefsName, 0,
						R.xml.adhoc_settings, true);

				Editor ed = app.settings.edit();
				ed.putString("adhoc_profiles", name);
				ed.commit();

				// copy old defaults / user values?
				SharedPreferences prefs = app.getSharedPreferences(
						"adhoc_default",
						0);
				ed = prefs.edit();
				for (String key : prefs.getAll().keySet()) {
					String value = app.settings.getString(key, null);
					if (value != null)
						ed.putString(key, value);
				}
				ed.commit();

				readProfile(name);

			} catch (UnknownHostException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}

	WifiAdhocControl(WifiControl control) {
		this.control = control;
		this.app = control.app;
		this.detection = ChipsetDetection.getDetection();

		readProfiles();

		if (isAdhocSupported() &&
				app.coretask.getProp("adhoc.status").equals("running")) {
			WifiAdhocNetwork network = getNetwork(app.settings.getString(
					ADHOC_PROFILE, null));
			if (network == null) {
				// TODO?
			}
			this.updateState(ADHOC_STATE_ENABLED, network);
		} else {
			this.updateState(ADHOC_STATE_DISABLED, null);
		}
	}

	public WifiAdhocNetwork getNetwork(String SSID) {
		return adhocNetworks.get(SSID);
	}

	public Collection<WifiAdhocNetwork> getNetworks() {
		return adhocNetworks.values();
	}

	public static String stateString(int state) {
		switch (state) {
		case ADHOC_STATE_DISABLED:
			return "Disabled";
		case ADHOC_STATE_ENABLING:
			return "Enabling";
		case ADHOC_STATE_ENABLED:
			return "Enabled";
		case ADHOC_STATE_DISABLING:
			return "Disabling";
		}
		return "Error";
	}

	public int getState() {
		return this.state;
	}

	public WifiAdhocNetwork getConfig() {
		return config;
	}

	static final String ADHOC_PROFILE = "active_adhoc_profile";
	private void updateState(int newState, WifiAdhocNetwork newConfig) {
		int oldState = 0;
		WifiAdhocNetwork oldConfig;

		oldState = this.state;
		oldConfig = this.config;
		this.state = newState;
		this.config = newConfig;

		if (newConfig != null)
			newConfig.setNetworkState(newState);

		if (newConfig != oldConfig && oldConfig != null)
			oldConfig.setNetworkState(ADHOC_STATE_DISABLED);

		Intent modeChanged = new Intent(ADHOC_STATE_CHANGED_ACTION);

		modeChanged.putExtra(EXTRA_SSID, config == null ? null : config.SSID);
		modeChanged.putExtra(EXTRA_STATE, newState);
		modeChanged.putExtra(EXTRA_PREVIOUS_STATE, oldState);

		app.sendStickyBroadcast(modeChanged);
		Editor ed = app.settings.edit();
		ed.putString(ADHOC_PROFILE, newConfig == null ? null
				: newConfig.SSID);
		ed.commit();
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
					"Failed to control Adhoc mode, mode ended up being '"
							+ actualMode + "'");
		}
	}

	public static boolean isAdhocSupported() {
		return ChipsetDetection.getDetection().isModeSupported(WifiMode.Adhoc);
	}

	synchronized void startAdhoc(Shell shell, WifiAdhocNetwork config)
			throws IOException {

		if (!isAdhocSupported()) {
			updateState(ADHOC_STATE_ERROR, config);
			return;
		}

		updateState(ADHOC_STATE_ENABLING, config);

		try {
			control.logStatus("Updating configuration");
			config.updateConfiguration();

			try {
				control.logStatus("Running adhoc start");
				shell.run(new CommandLog(app.coretask.DATA_FILE_PATH
						+ "/bin/adhoc start 1"));
			} catch (InterruptedException e) {
				IOException x = new IOException();
				x.initCause(e);
				throw x;
			}

			control.logStatus("Waiting for adhoc mode to start");
			waitForMode(shell, WifiMode.Adhoc);
			updateState(ADHOC_STATE_ENABLED, config);
		} catch (IOException e) {
			updateState(ADHOC_STATE_ERROR, config);
			throw e;
		}
	}

	synchronized void stopAdhoc(Shell shell) throws IOException {
		if (!isAdhocSupported()) {
			updateState(ADHOC_STATE_ERROR, config);
			return;
		}

		updateState(ADHOC_STATE_DISABLING, this.config);
		try {
			try {
				control.logStatus("Running adhoc stop");
				if (shell.run(new CommandLog(app.coretask.DATA_FILE_PATH
						+ "/bin/adhoc stop 1")) != 0)
					throw new IOException("Failed to stop adhoc mode");
			} catch (InterruptedException e) {
				IOException x = new IOException();
				x.initCause(e);
				throw x;
			}

			control.logStatus("Waiting for wifi to turn off");
			waitForMode(shell, WifiMode.Off);
			updateState(ADHOC_STATE_DISABLED, null);
		} catch (IOException e) {
			updateState(ADHOC_STATE_ERROR, this.config);
			throw e;
		}
	}

	private boolean testAdhoc(Chipset chipset, Shell shell) throws IOException,
			UnknownHostException {
		detection.setChipset(chipset);
		if (!chipset.supportedModes.contains(WifiMode.Adhoc))
			return false;

		WifiAdhocNetwork config = WifiAdhocNetwork.getTestNetwork();

		IOException exception = null;

		try {
			startAdhoc(shell, config);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
			// if starting fails, remember the exception
			exception = e;
		}

		try {
			stopAdhoc(shell);
		} catch (IOException e) {
			// if stopping fails, abort the test completely
			if (exception != null) {
				Throwable cause = e;
				while (cause.getCause() != null)
					cause = cause.getCause();
				cause.initCause(exception);
			}

			throw e;
		}

		// fail if starting failed
		return exception == null;
	}

	public boolean testAdhoc(Shell shell, LogOutput log) {
		boolean ret = false;
		log.log("Scanning for known android hardware");

		if (detection.getDetectedChipsets().size() == 0) {
			log.log("Hardware is unknown, scanning for wifi modules");

			detection.inventSupport();
		}

		for (Chipset c : detection.getDetectedChipsets()) {
			log.log("Testing - " + c.chipset);

			try {
				if (testAdhoc(c, shell)) {
					ret = true;
					log.log("Found support for " + c.chipset);
					break;
				}
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		if (!ret) {
			detection.setChipset(null);
			log.log("No adhoc support found");
		}
		Editor ed = app.settings.edit();
		ed.putString("detectedChipset", ret ? detection.getChipset()
				: "UnKnown");
		ed.commit();

		return ret;
	}
}
