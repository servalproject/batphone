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

import org.servalproject.R;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalD.RhizomeListResult;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDInterfaceError;

import android.R.drawable;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * Rhizome list activity.  Presents the contents of the Rhizome store as a list of names.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeList extends ListActivity {

	static final int DIALOG_DETAILS_ID = 0;

	private static final int MENU_REFRESH = 0;

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Rhizome.ACTION_RECIEVE_FILE)) {
				listFiles();
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(Rhizome.TAG, getClass().getName()+".onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.rhizome_list);
		adapter = new ArrayAdapter<Display>(this, R.layout.rhizome_list_item);
		adapter.setNotifyOnChange(false);
		setListAdapter(adapter);
	}

	@Override
	protected void onResume() {
		Log.i(Rhizome.TAG, getClass().getName()+".onResume()");
		IntentFilter filter = new IntentFilter();
		filter.addAction(Rhizome.ACTION_RECIEVE_FILE);
		this.registerReceiver(receiver, filter);
		listFiles();
		super.onResume();
	}

	@Override
	protected void onPause() {
		this.unregisterReceiver(receiver);
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean supRetVal = super.onCreateOptionsMenu(menu);
		SubMenu m;

		m = menu.addSubMenu(0, MENU_REFRESH, 0,
				"Refresh list");
		m.setIcon(drawable.ic_input_add);

		return supRetVal;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		boolean supRetVal = super.onOptionsItemSelected(menuItem);
		switch (menuItem.getItemId()) {
		case MENU_REFRESH:
			listFiles();
			break;
		}
		return supRetVal;
	}

	class Display {
		final String name;
		final Bundle bundle;

		Display(String name, Bundle bundle) {
			this.name = name;
			this.bundle = bundle;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	/**
	 * Form a list of all files in the Rhizome database.
	 */
	private void listFiles() {
		adapter.clear();
		try {
			RhizomeListResult result = ServalD.rhizomeList(RhizomeManifest_File.SERVICE, null, null, -1, -1); // all rows
			//Log.i(Rhizome.TAG, "list=" + Arrays.deepToString(result.list));
			int servicecol;
			int namecol;
			int idcol;
			int datecol;
			int filesizecol;
			int versioncol;
			try {
				servicecol = result.columns.get("service");
				namecol = result.columns.get("name");
				idcol = result.columns.get("id");
				datecol = result.columns.get("date");
				filesizecol = result.columns.get("filesize");
				versioncol = result.columns.get("version");
			}
			catch (NullPointerException e) {
				throw new ServalDInterfaceError("missing column", result);
			}
			for (int i = 0; i != result.list.length; ++i) {
				String name = result.list[i][namecol];

				// is this a file we should hide???

				Bundle b = new Bundle();
				b.putString("service", result.list[i][servicecol]);
				b.putString("name", result.list[i][namecol]);
				b.putString("id", result.list[i][idcol]);
				b.putString("date", "" + Long.parseLong(result.list[i][datecol]));
				b.putString("filesize", "" + Long.parseLong(result.list[i][filesizecol]));
				b.putString("version", "" + Long.parseLong(result.list[i][versioncol]));
				adapter.add(new Display(name, b));
			}
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
		}
		catch (ServalDInterfaceError e) {
			Log.e(Rhizome.TAG, "servald interface problem", e);
		}
		catch (IllegalArgumentException e) {
			Log.e(Rhizome.TAG, "servald interface problem", e);
		}
		adapter.notifyDataSetChanged();
	}

	ArrayAdapter<Display> adapter;

	@Override
	protected void onListItemClick(ListView listview, View view, int position, long id) {
		showDialog(DIALOG_DETAILS_ID, adapter.getItem(position).bundle);
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
