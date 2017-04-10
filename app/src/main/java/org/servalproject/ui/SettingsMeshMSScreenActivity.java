/* Copyright (C) 2012 The Serval Project
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
 * Settings - main settings screen
 *
 * @author BluCalculator <blucalculator@gmail.com>
 */

package org.servalproject.ui;

import org.servalproject.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;

public class SettingsMeshMSScreenActivity extends Activity implements
		OnClickListener {
	private final int RINGTONE_PICKER_ACTIVITY = 1;
	private SharedPreferences mSharedPreferences = null;
	private SharedPreferences.Editor mPreferenceEditor = null;

	@Override
	public void onClick(View v) {
		switch(v.getId()) {
		case R.id.btnNotifSound:
			Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
					RingtoneManager.TYPE_NOTIFICATION);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
					"Select MeshMS Tone");
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
			String current = mSharedPreferences.getString(
					"meshms_notification_sound", null);
			if (current != null) {
				intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
						current);
			}
			Uri def = RingtoneManager.getActualDefaultRingtoneUri(this,
					RingtoneManager.TYPE_NOTIFICATION);
			if (def != null) {
				intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, def);
			}
			startActivityForResult(intent, RINGTONE_PICKER_ACTIVITY);
			break;
		default:
			break;
		}
	}
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settingsmeshmsscreen);

		mSharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		mPreferenceEditor = mSharedPreferences.edit();
		// Notification Sound Settings
		this.findViewById(R.id.btnNotifSound).setOnClickListener(this);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case RINGTONE_PICKER_ACTIVITY:// Notification sound
			switch (resultCode) {
			case RESULT_OK:
				Uri uri = data
						.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
				if (uri != null) {
					mPreferenceEditor.putString("meshms_notification_sound",
							uri.toString());
				} else {
					mPreferenceEditor.putString("meshms_notification_sound",
							null);
				}
				mPreferenceEditor.commit();
				break;
			case RESULT_CANCELED:
				// Do nothing
				break;
			}
		}
	}
}
