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

import org.servalproject.PreparationWizard;
import org.servalproject.PreparationWizard.Action;
import org.servalproject.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class SettingsScreenActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settingsscreen);

		// Wifi Settings Screen
		Button btnWifiSettings = (Button) this.findViewById(R.id.btnWifiSettings);
		btnWifiSettings.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				SettingsScreenActivity.this.startActivity(new Intent(SettingsScreenActivity.this,
						SetupActivity.class));
			}
		});

		// Log file display
		Button btnLogShow = (Button) this
				.findViewById(R.id.btnLogShow);
		btnLogShow.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				SettingsScreenActivity.this.startActivity(new Intent(
						SettingsScreenActivity.this,
						org.servalproject.LogActivity.class));
			}
		});

		// Accounts Settings Screen
		Button btnAccountsSettings = (Button) this
				.findViewById(R.id.btnAccountsSettings);
		btnAccountsSettings.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				SettingsScreenActivity.this.startActivity(new Intent(
						SettingsScreenActivity.this,
						AccountsSettingsActivity.class));
			}
		});

		// Reset Settings Screen
		Button btnResetWifiSettings = (Button) this
				.findViewById(R.id.btnResetWifi);
		btnResetWifiSettings.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				// Clear out old attempt_ files
				File varDir = new File("/data/data/org.servalproject/var/");
				if (varDir.isDirectory())
					for (File f : varDir.listFiles()) {
						if (!f.getName().startsWith("attempt_"))
							continue;
						f.delete();
					}
				// Re-run wizard
				PreparationWizard.currentAction = Action.NotStarted;
				Intent prepintent = new Intent(SettingsScreenActivity.this,
						PreparationWizard.class);
				prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(prepintent);
			}
		});

	}
}
