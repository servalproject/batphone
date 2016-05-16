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
 * @author Romana Challans <romana@servalproject.org>
 */

package org.servalproject.ui;

import java.io.File;

import org.servalproject.LogActivity;
import org.servalproject.PreparationWizard;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewParent;

public class SettingsScreenActivity extends Activity implements OnClickListener {

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.btnWifiSettings:
			startActivity(new Intent(this, SetupActivity.class));
			break;
		case R.id.btnLogShow:
			startActivity(new Intent(this, LogActivity.class));
			break;
		case R.id.btnAccountsSettings:// Accounts Settings Screen
			startActivity(new Intent(this, AccountsSettingsActivity.class));
			break;
		case R.id.btnResetWifi:// Reset Wi-fi Settings Screen
			// Clear out old attempt_ files
			File varDir = new File(
					ServalBatPhoneApplication.context.coretask.DATA_FILE_PATH
							+
							"/var/");
			if (varDir.isDirectory())
				for (File f : varDir.listFiles()) {
					if (!f.getName().startsWith("attempt_"))
						continue;
					f.delete();
				}
			// Re-run wizard
			Intent prepintent = new Intent(SettingsScreenActivity.this,
					PreparationWizard.class);
			prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(prepintent);
			break;
		case R.id.btnMMSSettings:// Notification Sound Settings Screen
			startActivity(new Intent(this, SettingsMeshMSScreenActivity.class));
			break;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settingsscreen);

		if (Build.VERSION.SDK_INT >=17){
			// hide flight mode settings tweaks as we can no longer modify them
			View wifiSettings = (View)this.findViewById(R.id.btnWifiSettings).getParent();
			wifiSettings.setVisibility(View.GONE);
		}

		this.findViewById(R.id.btnWifiSettings).setOnClickListener(this);
		this.findViewById(R.id.btnLogShow).setOnClickListener(this);
		this.findViewById(R.id.btnAccountsSettings).setOnClickListener(this);
		this.findViewById(R.id.btnResetWifi).setOnClickListener(this);
		this.findViewById(R.id.btnMMSSettings).setOnClickListener(this);
	}

}
