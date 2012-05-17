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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;

import org.servalproject.R;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeDetail;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalD.RhizomeListResult;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDInterfaceError;

import android.os.Bundle;
import android.util.Log;
import android.app.ListActivity;
import android.app.Dialog;
import android.view.View;
import android.widget.ListView;
import android.widget.ArrayAdapter;

/**
 * Rhizome list activity.  Presents the contents of the Rhizome store as a list of names.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeList extends ListActivity {

	static final int DIALOG_DETAILS_ID = 0;

	/** The list of file names */
	private String[] fNames = null;

	/** The list of data bundles */
	private Bundle[] fBundles = null;

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
	 * Form a list of all files in the Rhizome database.
	 */
	private void listFiles() {
		try {
			RhizomeListResult result = ServalD.rhizomeList(RhizomeManifest_File.SERVICE, null, null, -1, -1); // all rows
			//Log.i(Rhizome.TAG, "list=" + Arrays.deepToString(result.list));
			int servicecol;
			int namecol;
			int manifestidcol;
			int datecol;
			int lengthcol;
			int versioncol;
			try {
				servicecol = result.columns.get("service");
				namecol = result.columns.get("name");
				manifestidcol = result.columns.get("manifestid");
				datecol = result.columns.get("date");
				lengthcol = result.columns.get("length");
				versioncol = result.columns.get("version");
			}
			catch (NullPointerException e) {
				throw new ServalDInterfaceError("missing column", result);
			}
			fNames = new String[result.list.length];
			fBundles = new Bundle[result.list.length];
			for (int i = 0; i != result.list.length; ++i) {
				fNames[i] = result.list[i][namecol];
				Bundle b = new Bundle();
				b.putString("service", result.list[i][servicecol]);
				b.putString("name", result.list[i][namecol]);
				b.putString("id", result.list[i][manifestidcol]);
				b.putString("date", "" + Long.parseLong(result.list[i][datecol]));
				b.putString("filesize", "" + Long.parseLong(result.list[i][lengthcol]));
				b.putString("version", "" + Long.parseLong(result.list[i][versioncol]));
				fBundles[i] = b;
			}
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
			fNames = new String[0];
			fBundles = null;
		}
		catch (ServalDInterfaceError e) {
			Log.e(Rhizome.TAG, "servald interface problem", e);
			fNames = new String[0];
			fBundles = null;
		}
		catch (IllegalArgumentException e) {
			Log.e(Rhizome.TAG, "servald interface problem", e);
			fNames = new String[0];
			fBundles = null;
		}
	}

	/**
	 * Set up the interface based on the list of files.
	 */
	private void setUpUI() {
		listFiles();
		setListAdapter(new ArrayAdapter<String>(this, R.layout.rhizome_list_item, fNames));
	}

	@Override
	protected void onListItemClick(ListView listview, View view, int position, long id) {
		showDialog(DIALOG_DETAILS_ID, fBundles[position]);
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
				((RhizomeDetail) dialog).setManifest(RhizomeManifest_File.fromBundle(bundle, null));
				((RhizomeDetail) dialog).enableSaveOrOpenButton();
			}
			catch (RhizomeManifestParseException e) {
				Log.e(Rhizome.TAG, "cannot instantiate manifest object", e);
				((RhizomeDetail) dialog).setManifest(null);
				((RhizomeDetail) dialog).disableSaveButton();
				((RhizomeDetail) dialog).disableOpenButton();
			}
			break;
		}
		super.onPrepareDialog(id, dialog, bundle);
	}

}
