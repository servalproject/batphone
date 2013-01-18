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
 * help screens - main screen behaviour
 *
 * @author Romana Challans <romana@servalproject.org>
 */
package org.servalproject.ui.help;

import org.servalproject.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;


public class HelpActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.helpactivity);

		// Help Screen
		Button btnHelpguide = (Button) this.findViewById(R.id.btnHelpguide);
		btnHelpguide.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				HelpActivity.this.startActivity(new Intent(HelpActivity.this,
						HelpGuide.class));
			}
		});

		// Security Screen
		Button btnSecurity = (Button) this.findViewById(R.id.btnSecurity);
		btnSecurity.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				HelpActivity.this.startActivity(new Intent(HelpActivity.this,
						SecurityScreen.class));
			}
		});

		// Accounts and Contacts Screen
		Button btnAccounts = (Button) this.findViewById(R.id.btnAccounts);
		btnAccounts.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				HelpActivity.this.startActivity(new Intent(HelpActivity.this,
						AccountsContactsScreen.class));
			}
		});

		// About Screen
		Button btnAbout = (Button) this.findViewById(R.id.btnAbout);
		btnAbout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				HelpActivity.this.startActivity(new Intent(HelpActivity.this,
						AboutScreen.class));
			}
		});

		// Licences Screen
		Button btnLicences = (Button) this.findViewById(R.id.btnLicences);
		btnLicences.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				HelpActivity.this.startActivity(new Intent(HelpActivity.this,
						LicenceScreen.class));
			}
		});

		// Links Screen
		Button btnLinks = (Button) this.findViewById(R.id.btnLinks);
		btnLinks.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				HelpActivity.this.startActivity(new Intent(HelpActivity.this,
						LinksScreen.class));
			}
		});

	}
}

