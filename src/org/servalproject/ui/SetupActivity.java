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

package org.servalproject.ui;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.util.Log;

public class SetupActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	public static final String MSG_TAG = "ADHOC -> SetupActivity";
	public static final String AIRPLANE_MODE_TOGGLEABLE_RADIOS = "airplane_mode_toggleable_radios";

	private static final int ID_DIALOG_UPDATING = 1;
	private static final int ID_DIALOG_RESTARTING = 2;
	private int currentDialog = 0;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.layout.setupview);

		final ContentResolver resolver = getContentResolver();
		final String toggleableRadios = Settings.System.getString(resolver,
				AIRPLANE_MODE_TOGGLEABLE_RADIOS);

		setFlightModeCheckBoxes("bluetooth", toggleableRadios);
		setFlightModeCheckBoxes("wifi", toggleableRadios);

	}

	private void setFlightModeCheckBoxes(String name, String airplaneToggleable) {
		CheckBoxPreference pref = (CheckBoxPreference) findPreference(name
				+ "_toggleable");
		pref.setChecked(airplaneToggleable != null
				&& airplaneToggleable.contains(name));
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

		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		Log.d(MSG_TAG, "Calling onPause()");
		super.onPause();
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

	private void updateConfiguration(final SharedPreferences sharedPreferences,
			final String key) {
		if (key.endsWith("_toggleable")) {
			String radio = key.substring(0, key.indexOf('_'));
			boolean value = sharedPreferences.getBoolean(key, false);
			flightModeFix(AIRPLANE_MODE_TOGGLEABLE_RADIOS, radio, value);
		}
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
