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

import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import java.io.File;
import java.util.LinkedList;

/**
 * Rhizome "saved" activity.  Presents the contents of rhizome's 'saved' directory, which contains
 * files that have been extracted from rhizome.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeSaved extends ListActivity implements DialogInterface.OnDismissListener {

	static final int DIALOG_DETAILS_ID = 0;

	/** The list of file names */
	private String[] fNames = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(Rhizome.TAG, getClass().getName()+".onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.rhizome_saved);
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

	/** Populate the display with a list of all the saved files.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	private void setUpUI() {
		listFiles();
		setListAdapter(new ArrayAdapter<String>(this, R.layout.rhizome_list_item, fNames));
	}

	/**
	 * Form a list of all saved rhizome files.  A saved file is a file in the Rhizome 'saved'
	 * directory, named "foo", with an accompanying file named ".manifest.foo" which contains a
	 * valid Rhizome manifest that matches "foo".
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	private void listFiles() {
		try {
			File savedDir = Rhizome.getSaveDirectory();
			LinkedList<String> names = new LinkedList<String>();
			if (savedDir.isDirectory()) {
				String[] filenames = savedDir.list();
				for (String filename: filenames) {
					if (filename.startsWith(".manifest.") && filename.length() > 10) {
						File payloadfile = new File(savedDir, filename.substring(10));
						if (payloadfile.isFile()) {
							// Could check here that manifest is valid, just to be sure.
							names.add(payloadfile.getName());
						}
					}
				}
			}
			fNames = names.toArray(new String[0]);
		} catch (Exception e) {
			Log.e(Rhizome.TAG, e.toString(), e);
			ServalBatPhoneApplication.context.displayToastMessage(e
					.getMessage());
			fNames = new String[0];
		}
	}

	@Override
	protected void onListItemClick(ListView listview, View view, int position, long id) {
		String name = fNames[position];
		String manifestname = ".manifest." + name;
		Bundle b = new Bundle();
		b.putString("name", name);
		b.putString("manifestname", manifestname);
		showDialog(DIALOG_DETAILS_ID, b);
	}

	@Override
	protected Dialog onCreateDialog(int id, Bundle bundle) {
		switch (id) {
		case DIALOG_DETAILS_ID:
			return new RhizomeDetail(this);
		}
		return super.onCreateDialog(id, bundle);
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
		switch (id) {
		case DIALOG_DETAILS_ID:
			try {
				String manifestName = bundle.getString("manifestname");
				String name = bundle.getString("name");
				RhizomeDetail detail = (RhizomeDetail) dialog;
				File saveDirectory = Rhizome.getSaveDirectory();

				detail.setBundleFiles(
						new File(saveDirectory, manifestName), new File(
								saveDirectory, name));
				detail.setOnDismissListener(this);
			} catch (Exception e) {
				Log.e(Rhizome.TAG, e.toString(), e);
				dialog.dismiss();
			}

			break;
		}
		super.onPrepareDialog(id, dialog, bundle);
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		if (dialog instanceof RhizomeDetail && ((RhizomeDetail) dialog).deleteButtonClicked()) {
			setUpUI();
		}
	}

}
