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

import android.app.Dialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
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
	String service;
	RhizomeManifest lastManifest;

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

		Intent intent = this.getIntent();
		if (intent != null) {
			service = intent.getStringExtra("service");
		}
		if (service == null)
			service = RhizomeManifest_File.SERVICE;
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

	class Display {
		final RhizomeManifest manifest;

		Display(RhizomeManifest manifest) {
			this.manifest = manifest;
		}

		@Override
		public String toString() {
			return manifest.getDisplayName();
		}
	}

	/**
	 * Form a list of all files in the Rhizome database.
	 */
	private void listFiles() {
		adapter.clear();
		try {
			RhizomeListResult result = ServalD.rhizomeList(service, null, null,
					-1, -1); // all rows
			for (int i = 0; i != result.list.length; ++i) {
				// TODO is this a result we should hide???

				try {
					adapter.add(new Display(result.toManifest(i)));
				} catch (RhizomeManifestParseException e) {
					Log.e("RhizomeList", e.getMessage(), e);
				}
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
		this.lastManifest = adapter.getItem(position).manifest;
		showDialog(DIALOG_DETAILS_ID);
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
			((RhizomeDetail) dialog).setManifest(lastManifest);
			((RhizomeDetail) dialog).enableSaveOrOpenButton();
			break;
		}
		super.onPrepareDialog(id, dialog, bundle);
	}

}
