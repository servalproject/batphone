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

package org.servalproject.rhizome;

import java.io.File;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.util.Log;
import android.app.Activity;
import android.os.Environment;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;
import android.view.View.OnClickListener;
import android.content.Intent;
import android.content.DialogInterface;

/**
 * Rhizome list activity.  Presents the contents of the Rhizome store as a list of names.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeMain extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(Rhizome.TAG, getClass().getName()+".onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.rhizome_list);
	}

	@Override
	protected void onStart() {
		Log.i(Rhizome.TAG, getClass().getName()+".onStart()");
		super.onStart();
	}

	@Override
	protected void onResume() {
		Log.i(Rhizome.TAG, getClass().getName()+".onResume()");
		setUpUI();
		super.onResume();
	}

	@Override
	protected void onStop() {
		Log.i(Rhizome.TAG, getClass().getName()+".onStop()");
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		Log.i(Rhizome.TAG, getClass().getName()+".onDestroy()");
		super.onDestroy();
	}

	/**
	 * Set up the interface layout.
	 */
	private void setUpUI() {
		setContentView(R.layout.rhizome_main);
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			Button buttonShare = (Button) this.findViewById(R.id.rhizome_share);
			buttonShare.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						FolderPicker shareDialog = new FolderPicker(RhizomeMain.this, android.R.style.Theme, true);
						shareDialog.setOnClickListener(new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface di, int which) {
									if (which == DialogInterface.BUTTON_POSITIVE)
										Rhizome.addFile(((FolderPicker) di).getPath());
								}
							});
						shareDialog.show();
					}
				});
			Button buttonFind = (Button) this.findViewById(R.id.rhizome_find);
			buttonFind.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						RhizomeMain.this.startActivity(new Intent(RhizomeMain.this, RhizomeList.class));
					}
				});
			Button buttonSaved = (Button) this.findViewById(R.id.rhizome_saved);
			buttonSaved.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						RhizomeMain.this.startActivity(new Intent(RhizomeMain.this, RhizomeSaved.class));
					}
				});
		} else {
			// If there is not SD card present, grey out the buttons and the storage display.
			;
		}

	}

}
