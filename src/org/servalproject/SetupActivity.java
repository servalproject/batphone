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

/**
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *  You should have received a copy of the GNU General Public License along with
 *  this program; if not, see <http://www.gnu.org/licenses/>.
 *  Use this application at your own risk.
 *
 *  Copyright (c) 2009 by Harald Mueller and Seth Lemons.
 */

package org.servalproject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.system.Chipset;
import org.servalproject.system.ChipsetDetection;
import org.servalproject.system.WiFiRadio;
import org.servalproject.system.WifiMode;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.provider.Settings;
import android.util.Log;

public class SetupActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	private ServalBatPhoneApplication application = null;

	public static final String MSG_TAG = "ADHOC -> SetupActivity";
	public static final String AIRPLANE_MODE_TOGGLEABLE_RADIOS = "airplane_mode_toggleable_radios";

	private String currentSSID;
	private String currentChannel;
	private String currentLAN;
	private String currentTransmitPower;

	private EditTextPreference prefSSID;

	private static final int ID_DIALOG_UPDATING = 1;
	private static final int ID_DIALOG_RESTARTING = 2;
	private static final int ID_DIALOG_INVENTING = 3;
	private int currentDialog = 0;

	private WifiApControl apControl;
	private CheckBoxPreference apPref;
	private ListPreference wifiMode;
	private Preference ap_enabled;
	private String apSummaryText;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Init Application
		this.application = (ServalBatPhoneApplication) this.getApplication();

		// Init CurrentSettings
		// PGS 20100613 - MeshPotato compatible settings
		// (Mesh potatoes claim to be on channel 1 when enquired with iwconfig,
		// but iStumbler shows that
		// they seem to be on channel 11 - so we will try defaulting to channel
		// 11.
		this.currentSSID = this.application.settings.getString("ssidpref",
				ServalBatPhoneApplication.DEFAULT_SSID);
		this.currentChannel = this.application.settings.getString(
				"channelpref", ServalBatPhoneApplication.DEFAULT_CHANNEL);
		this.currentLAN = this.application.settings.getString("lannetworkpref",
				ServalBatPhoneApplication.DEFAULT_LANNETWORK);
		this.currentTransmitPower = this.application.settings.getString(
				"txpowerpref", "disabled");

		addPreferencesFromResource(R.layout.setupview);

		// Disable "Transmit power" if not supported
		if (!this.application.isTransmitPowerSupported()) {
			PreferenceGroup wifiGroup = (PreferenceGroup) findPreference("wifiprefs");
			ListPreference txpowerPreference = (ListPreference) findPreference("txpowerpref");
			wifiGroup.removePreference(txpowerPreference);
		}

		Preference sipPrefs = findPreference("sipPrefs");
		sipPrefs.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(SetupActivity.this,
						org.sipdroid.sipua.ui.Settings.class));
				return false;
			}
		});

		ap_enabled = findPreference("ap_enabled");
		apSummaryText = ap_enabled.getSummary().toString();
		ap_enabled.setSummary(apSummaryText.replace("[SSID]", currentSSID));

		// SSID-Validation
		this.prefSSID = (EditTextPreference) findPreference("ssidpref");
		this.prefSSID
				.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						String message = validateSSID(newValue.toString());
						if (!message.equals("")) {
							application.displayToastMessage(message);
							return false;
						}
						return true;
					}
				});

		{
			// add entries to the chipset list based on the detect scripts
			final ListPreference chipsetPref = (ListPreference) findPreference("chipset");
			List<CharSequence> entries = new ArrayList<CharSequence>();
			// entries.add("Automatic");
			final ChipsetDetection detection = ChipsetDetection.getDetection();
			for (Chipset chipset : detection.getDetectedChipsets()) {
				entries.add(chipset.chipset);
			}
			String values[] = entries.toArray(new String[entries.size()]);
			chipsetPref.setEntries(values);
			chipsetPref.setEntryValues(values);
			chipsetPref.setSummary(detection.getChipset());

			chipsetPref
					.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
						@Override
						public boolean onPreferenceChange(
								Preference preference, Object newValue) {
							String value = (String) newValue;
							dialogHandler.sendEmptyMessage(ID_DIALOG_UPDATING);
							boolean ret = false;

							// force a re-test of root permission
							application.coretask.testRootPermission();

							ret = detection.testAndSetChipset(value, true);

							setAvailableWifiModes();

							SetupActivity.this.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									chipsetPref.setSummary(detection
											.getChipset());
								}
							});

							dialogHandler.sendEmptyMessage(0);
							return ret;
						}
					});
		}

		final ContentResolver resolver = getContentResolver();
		final String airplaneModeRadios = Settings.System.getString(resolver,
				Settings.System.AIRPLANE_MODE_RADIOS);
		final String toggleableRadios = Settings.System.getString(resolver,
				AIRPLANE_MODE_TOGGLEABLE_RADIOS);

		setFlightModeCheckBoxes("bluetooth", airplaneModeRadios,
				toggleableRadios);
		setFlightModeCheckBoxes("wifi", airplaneModeRadios, toggleableRadios);

		this.wifiMode = (ListPreference) findPreference("wifi_mode");
		setAvailableWifiModes();

		// check if personal AP is enabled
		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		apControl = WifiApControl.getApControl(wifi);

		apPref = (CheckBoxPreference) findPreference("ap_enabled");
		apPref.setEnabled(apControl != null);

	}

	private void setFlightModeCheckBoxes(String name, String airplaneMode,
			String airplaneToggleable) {
		CheckBoxPreference pref = (CheckBoxPreference) findPreference(name
				+ "_sensitive");
		pref.setChecked(airplaneMode == null || airplaneMode.contains(name));

		pref = (CheckBoxPreference) findPreference(name + "_toggleable");
		pref.setChecked(airplaneToggleable != null
				&& airplaneToggleable.contains(name));
	}

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(WiFiRadio.WIFI_MODE_ACTION)) {
				String mode = intent.getStringExtra(WiFiRadio.EXTRA_NEW_MODE);

				boolean changing = intent.getBooleanExtra(
						WiFiRadio.EXTRA_CHANGING, false);
				boolean changePending = intent.getBooleanExtra(
						WiFiRadio.EXTRA_CHANGE_PENDING, false);
				String ssid = intent
						.getStringExtra(WiFiRadio.EXTRA_CONNECTED_SSID);

				if (!changing && wifiMode.getValue() != mode) {
					ignoreChange = true;
					wifiMode.setValue(mode);
					ignoreChange = false;
				}

				WifiMode m = WifiMode.valueOf(mode);
				wifiMode.setSummary(changing ? "Changing..." : m.getDisplay()
						+ (ssid != null ? " (" + ssid + ")" : "")
						+ (changePending ? " ..." : ""));

				boolean enabled = application.getState() == State.On
						&& !changing;
				wifiMode.setEnabled(enabled);
				wifiMode.setSelectable(enabled);
			}
		}
	};

	private void setAvailableWifiModes() {
		Chipset chipset = null;

		if (ChipsetDetection.getDetection() != null)
			chipset = ChipsetDetection.getDetection().getWifiChipset();
		String values[] = null;
		String display[] = null;
		if (chipset != null && chipset.supportedModes != null) {
			values = new String[chipset.supportedModes.size()];
			display = new String[chipset.supportedModes.size()];

			int i = 0;
			for (WifiMode m : chipset.supportedModes) {
				values[i] = m.toString();
				display[i++] = m.getDisplay();
			}
		}

		wifiMode.setEntryValues(values);
		wifiMode.setEntries(display);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (ServalBatPhoneApplication.terminate_setup) {
			ServalBatPhoneApplication.terminate_setup = false;
			finish();
			return;
		}
		SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();

		if (apControl != null) {
			apPref
					.setChecked(apControl.getWifiApState() == WifiApControl.WIFI_AP_STATE_ENABLED);
		}

		IntentFilter filter = new IntentFilter();
		filter.addAction(WiFiRadio.WIFI_MODE_ACTION);
		this.registerReceiver(receiver, filter);

		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		Log.d(MSG_TAG, "Calling onPause()");
		super.onPause();
		this.unregisterReceiver(receiver);
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case ID_DIALOG_UPDATING:
			return ProgressDialog.show(this, "Updating Configuration",
					"Please wait while updating...", false, false);
		case ID_DIALOG_RESTARTING:
			return ProgressDialog.show(this, "Restarting BatPhone",
					"Please wait while restarting...", false, false);
		case ID_DIALOG_INVENTING:
			return ProgressDialog.show(this,
					"Guessing how to make your wifi work",
					"Please wait while I take a look...", false, false);
		}
		return null;
	}

	private boolean ignoreChange = false;

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (ignoreChange) {
			ignoreChange = false;
			return;
		}
		updateConfiguration(sharedPreferences, key);
	}

	Handler dialogHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == 0) {
				if (currentDialog != 0)
					SetupActivity.this.dismissDialog(currentDialog);
			} else {
				SetupActivity.this.showDialog(msg.what);
			}
			currentDialog = msg.what;
			super.handleMessage(msg);
		}
	};

	private void restartAdhoc() {
		if (application.wifiRadio.getCurrentMode() != WifiMode.Adhoc)
			return;
		dialogHandler.sendEmptyMessage(ID_DIALOG_RESTARTING);
		try {
			application.restartAdhoc();
		} catch (Exception ex) {
			application.displayToastMessage(ex.toString());
		}
		dialogHandler.sendEmptyMessage(0);
	}

	private void updateConfiguration(final SharedPreferences sharedPreferences,
			final String key) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (key.equals("ssidpref")) {
					String newSSID = sharedPreferences.getString("ssidpref",
							ServalBatPhoneApplication.DEFAULT_SSID);
					if (!currentSSID.equals(newSSID)) {
						currentSSID = newSSID;
						restartAdhoc();
						ap_enabled.setSummary(apSummaryText.replace("[SSID]",
								currentSSID));
					}
				} else if (key.equals("instrumentpref")) {
					Instrumentation.setEnabled(sharedPreferences.getBoolean(
							"instrumentpref", false));
				} else if (key.equals("instrument_rec")) {
					try {
						dialogHandler.sendEmptyMessage(ID_DIALOG_RESTARTING);
						application.meshManager.restartDna();
						dialogHandler.sendEmptyMessage(0);
					} catch (Exception e) {
						application.displayToastMessage(e.toString());
					}
				} else if (key.equals("ap_enabled")) {
					boolean enabled = sharedPreferences.getBoolean(
							"ap_enabled", false);
					dialogHandler.sendEmptyMessage(ID_DIALOG_UPDATING);
					try {
						if (application.setApEnabled(enabled))
							application.displayToastMessage("Access point "
									+ (enabled ? "started" : "stopped"));
						else
							application.displayToastMessage("Unable to "
									+ (enabled ? "start" : "stop")
									+ " access point");
					} catch (Exception e) {
						Log.v("BatPhone", e.toString(), e);
						application.displayToastMessage(e.toString());
					}
					dialogHandler.sendEmptyMessage(0);
				} else if (key.equals("channelpref")) {
					String newChannel = sharedPreferences.getString(
							"channelpref", "1");
					if (currentChannel.equals(newChannel) == false) {
						currentChannel = newChannel;
						restartAdhoc();
					}
				} else if (key.equals("wakelockpref")) {
					application.meshManager.wakeLockChanged(sharedPreferences
							.getBoolean("wakelockpref", true));
				} else if (key.equals("txpowerpref")) {
					String transmitPower = sharedPreferences.getString(
							"txpowerpref", "disabled");
					if (transmitPower.equals(currentTransmitPower) == false) {
						restartAdhoc();
						currentTransmitPower = transmitPower;
					}
				} else if (key.startsWith("gateway")) {
					// Any one of the various lan gateway settings.
					// Re-write the included SIP.conf
					String server = sharedPreferences.getString(
							"gatewayserver", "");
					String username = sharedPreferences.getString(
							"gatewayuser", "");
					String password = sharedPreferences.getString(
							"gatewaypass", "");
					Boolean enable = sharedPreferences.getBoolean(
							"gatewayenable", false);

					try {
						File file = new File(
								"/data/data/org.servalproject/asterisk/etc/asterisk/gatewaysip.conf");
						file.createNewFile();
						FileWriter f = new FileWriter(file.getAbsolutePath());
						BufferedWriter out = new BufferedWriter(f);
						if (enable) {
							out
									.write("register => "
											+ username
											+ ":"
											+ password
											+ "@"
											+ server
											+ "/"
											+ username
											+ "\n"
											+ "[dnagateway]\n"
											+ "type=friend\n"
											+ "username="
											+ username
											+ "\n"
											+ "secret="
											+ password
											+ "\n"
											+ "fromuser="
											+ username
											+ "\n"
											+ "host="
											+ server
											+ "\n"
									+ "nat=yes\n"
											+ "dfmtmode=rfc2833\n"
											+ "fromdomain="
											+ server
											+ "\n"
											+ "context=dnagatewayinbound\ninsecure=very\n");
						}
						out.close();
					} catch (Exception e) {
						// Blast -- something went wrong
						Log.e(MSG_TAG,
								"Exception happened while updating DNA Gateway Configuration:"
										+ e);
					}
					// Restart asterisk: restartAdhoc() is an overkill, but will
					// do the trick.
					if (application.wifiRadio.getCurrentMode() == WifiMode.Adhoc)
						application.restartAdhoc();
				} else if (key.equals("lannetworkpref")) {
					String lannetwork = sharedPreferences.getString(
							"lannetworkpref",
							ServalBatPhoneApplication.DEFAULT_LANNETWORK);
					if (!lannetwork.equals(currentLAN)) {
						restartAdhoc();
						currentLAN = lannetwork;
					}
				} else if (key.equals("routingImpl")) {
					try {
						application.wifiRadio.setRouting();
					} catch (Exception e) {
						Log
								.e(
										"BatPhone",
										"Failure while changing routing implementation",
										e);
					}
				} else if (key.equals("wifi_auto")) {
					application.wifiRadio.setAutoCycling(sharedPreferences
							.getBoolean("wifi_auto", true));
				} else if (key.equals("wifi_mode")) {
					try {
						String mode = sharedPreferences.getString("wifi_mode",
								"Off");
						application.wifiRadio.setWiFiMode(WifiMode
								.valueOf(mode));
					} catch (Exception e) {
						application.displayToastMessage(e.toString());
					}
				} else if (key.endsWith("_sensitive")) {
					String radio = key.substring(0, key.indexOf('_'));
					boolean value = sharedPreferences.getBoolean(key, false);

					flightModeFix(Settings.System.AIRPLANE_MODE_RADIOS, radio,
							value);

				} else if (key.endsWith("_toggleable")) {
					String radio = key.substring(0, key.indexOf('_'));
					boolean value = sharedPreferences.getBoolean(key, false);
					flightModeFix(AIRPLANE_MODE_TOGGLEABLE_RADIOS, radio, value);
				}
			}
		}).start();
	}

	private void flightModeFix(String key, String radio, boolean newSetting) {
		final ContentResolver resolver = getContentResolver();
		String value = Settings.System.getString(resolver, key);
		boolean exists = value.contains(radio);

		if (newSetting == exists)
			return;
		if (newSetting)
			value += " " + radio;
		else
			value = value.replace(radio, "");
		Settings.System.putString(resolver, key, value);
	}

	public String validateSSID(String newSSID) {
		String message = "";
		String validChars = "ABCDEFGHIJKLMONPQRSTUVWXYZ"
				+ "abcdefghijklmnopqrstuvwxyz" + "0123456789_.";
		for (int i = 0; i < newSSID.length(); i++) {
			if (!validChars.contains(newSSID.substring(i, i + 1))) {
				message = "SSID contains invalid characters";
			}
		}
		if (newSSID.equals("")) {
			message = "New SSID cannot be empty";
		}
		if (message.length() > 0)
			message += ", not saved.";
		return message;
	}
}
