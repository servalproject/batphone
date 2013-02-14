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
 * Settings - Accounts Settings screen
 *
 * @author Romana Challans <romana@servalproject.org>
 */

package org.servalproject.ui;

import org.servalproject.R;
import org.servalproject.servald.Identity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class AccountsSettingsActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.accountssetting);

		// Accounts Settings Screen
		Button btnphoneReset = (Button) this.findViewById(R.id.btnphoneReset);
		btnphoneReset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				AccountsSettingsActivity.this.startActivity(new Intent(
						AccountsSettingsActivity.this,
						org.servalproject.wizard.SetPhoneNumber.class));

			}
		});

		// Get account information for display
		Identity accountID = Identity.getMainIdentity();

		// Set Textviews and blank strings
		TextView acPN = (TextView) this.findViewById(R.id.acphonenumber);
		TextView acSID = (TextView) this.findViewById(R.id.acsid);
		TextView acNAME = (TextView) this.findViewById(R.id.acname);

		String PNid = "";
		String SIDid = "";
		String NMid = "";

		// assign values

		if (accountID.getDid() != null)
			PNid = accountID.getDid();
		else
			PNid = ("There is no phone number to display");

		if (accountID.subscriberId.abbreviation() != null)
			SIDid = accountID.subscriberId.abbreviation();
		else
			SIDid = ("There is no ServalID to display");

		if (accountID.getName() != null)
			NMid = accountID.getName();
		else
			NMid = ("There is no name to display");

		// set values to display
		acPN.setText(PNid); // Phone number
		acSID.setText(SIDid); // Serval ID
		acNAME.setText(NMid); // Name

	}
}
