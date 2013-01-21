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
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.BundleId;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalD.RhizomeExtractManifestResult;

import android.R.drawable;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.os.AsyncTask;
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
	String service;
	int clickPosition;

	private static final int MENU_REFRESH = 0;

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Rhizome.ACTION_RECEIVE_FILE)) {
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
		filter.addAction(Rhizome.ACTION_RECEIVE_FILE);
		filter.addDataScheme("content");
		try {
			filter.addDataType("*/*");
		} catch (MalformedMimeTypeException e) {
			Log.e("RhizomeList", e.toString(), e);
		}
		this.registerReceiver(receiver, filter, Rhizome.RECEIVE_PERMISSION,
				null);
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
		final BundleId id;
		final String name;
		final boolean authoredHere;

		Display(BundleId id, String name, boolean authoredHere) {
			this.id = id;
			this.name = name;
			this.authoredHere = authoredHere;
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
		new AsyncTask<Void, Display, Void>() {
			private boolean first = true;

			@Override
			protected Void doInBackground(Void... params) {
				ServalD.rhizomeListAsync(service, null,
						null, null, -1, -1, new ServalD.ManifestResult() {
							@Override
							public void manifest(BundleId id, String name,
									long fileSize, boolean fromHere) {
								if (fileSize == 0 || name.startsWith(".")
										|| name.endsWith(".smapp")
										|| name.endsWith(".smapl")
										|| name.startsWith("smaps-photo-")) {
									return;
								}
								publishProgress(new Display(id, name, fromHere));
							}
						});
				return null;
			}

			@Override
			protected void onProgressUpdate(Display... value) {
				if (first) {
					adapter.clear();
					first = false;
				}
				adapter.add(value[0]);
				adapter.notifyDataSetChanged();
			}

		}.execute();
	}

	ArrayAdapter<Display> adapter;

	@Override
	protected void onListItemClick(ListView listview, View view, int position, long id) {
		this.clickPosition = position;
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
			try {
				RhizomeDetail detail = (RhizomeDetail) dialog;
				Display display = adapter.getItem(clickPosition);
				RhizomeExtractManifestResult result = ServalD
						.rhizomeExtractManifest(display.id, null);
				detail.setManifest(result.manifest);
				detail.enableSaveOrOpenButton();
				detail.disableUnshareButton();
				if (!result._readOnly)
					detail.enableUnshareButton();
			} catch (Exception e) {
				Log.e(Rhizome.TAG, e.getMessage(), e);
				ServalBatPhoneApplication.context.displayToastMessage(e
						.getMessage());
				dialog.dismiss();
			}
			break;
		}
		super.onPrepareDialog(id, dialog, bundle);
	}

}
