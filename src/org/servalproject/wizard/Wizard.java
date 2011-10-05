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

package org.servalproject.wizard;

import org.servalproject.Main;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Wizard extends Activity {

	@Override
	public void onBackPressed() {
		// Don't let the user go back until they've finished.
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (ServalBatPhoneApplication.context.dragonsAccepted == false) {
			setContentView(R.layout.wizard);

			Button b = (Button) this.findViewById(R.id.btnwizard);
			b.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {

					LayoutInflater li = LayoutInflater.from(Wizard.this);
					View view = li.inflate(R.layout.warning_dialog, null);

					AlertDialog.Builder alert = new AlertDialog.Builder(
							Wizard.this);
					alert.setView(view);
					alert.setPositiveButton("I Agree",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
										int whichButton) {
									ServalBatPhoneApplication.context
											.setDragonsAccepted(true);
									startActivity(new Intent(Wizard.this,
											Instructions.class));
							}
						});
					alert.setNegativeButton("Cancel", null);
					alert.show();

				}
			});
		} else {
			// The user has accepted the dragons, so go straight to the main
			// activity provided that they have a primary number set.
			if (ServalBatPhoneApplication.context.getPrimaryNumber() != null) {
				startActivity(new Intent(Wizard.this, Main.class));
			} else {
				// No primary number, so go through the process from the beginning.
				ServalBatPhoneApplication.context
				.setDragonsAccepted(false);
				startActivity(new Intent(Wizard.this,
						Instructions.class));
			}
		}
	}

}
