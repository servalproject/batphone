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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeDetail;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDInterfaceError;

import android.os.Bundle;
import android.util.Log;
import android.app.ListActivity;
import android.app.Dialog;
import android.view.View;
import android.widget.ListView;
import android.content.DialogInterface;
import android.widget.ArrayAdapter;

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
		File savedDir = Rhizome.getSaveDirectory();
		try {
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
		}
		catch (SecurityException e) {
			Log.w("cannot read " + savedDir, e);
			fNames = new String[0];
		}
	}

	@Override
	protected void onListItemClick(ListView listview, View view, int position, long id) {
		String name = fNames[position];
		String manifestname = ".manifest." + name;
		File manifestfile = new File(Rhizome.getSaveDirectory(), manifestname);
		try {
			FileInputStream mfis = new FileInputStream(manifestfile);
			if (manifestfile.length() <= RhizomeManifest.MAX_MANIFEST_BYTES) {
				byte[] manifestbytes = new byte[(int) manifestfile.length()];
				mfis.read(manifestbytes);
				mfis.close();
				RhizomeManifest m = RhizomeManifest.fromByteArray(manifestbytes);
				Bundle b = new Bundle();
				b.putParcelable("manifestBundle", m.asBundle());
				showDialog(DIALOG_DETAILS_ID, b);
			} else
				Log.e(Rhizome.TAG, "file " + manifestfile + " is too large");
		}
		catch (IOException e) {
			Log.e(Rhizome.TAG, "cannot read " + manifestfile, e);
		}
		catch (RhizomeManifestParseException e) {
			Log.e(Rhizome.TAG, "file " + manifestfile, e);
		}
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
				((RhizomeDetail) dialog).setManifest(new RhizomeManifest((Bundle) bundle.getParcelable("manifestBundle"), null));
				((RhizomeDetail) dialog).enableOpenButton();
				((RhizomeDetail) dialog).enableDeleteButton();
				((RhizomeDetail) dialog).setOnDismissListener(this);
			}
			catch (RhizomeManifestParseException e) {
				Log.e(Rhizome.TAG, "bad manifest bundle", e);
				((RhizomeDetail) dialog).setManifest(null);
				((RhizomeDetail) dialog).disableOpenButton();
				((RhizomeDetail) dialog).disableDeleteButton();
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
