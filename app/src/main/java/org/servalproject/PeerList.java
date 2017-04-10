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
package org.servalproject;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import org.servalproject.batphone.CallHandler;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerComparator;
import org.servalproject.servald.PeerListService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 *
 *         Peer List fetches a list of known peers from the PeerListService.
 *         When a peer is received from the service this activity will attempt
 *         to resolve the peer by calling ServalD in an async task.
 */
public class PeerList extends ListActivity {

	private PeerListAdapter<Peer> listAdapter;

	private boolean displayed = false;
	private static final String TAG = "PeerList";

	public static final String PICK_PEER_INTENT = "org.servalproject.PICK_FROM_PEER_LIST";

	public static final String CONTACT_NAME = "org.servalproject.PeerList.contactName";
	public static final String CONTACT_ID = "org.servalproject.PeerList.contactId";
	public static final String DID = "org.servalproject.PeerList.did";
	public static final String SID = "org.servalproject.PeerList.sid";
	public static final String NAME = "org.servalproject.PeerList.name";
	public static final String RESOLVED = "org.servalproject.PeerList.resolved";

	private boolean returnResult = false;

	private List<Peer> peers = new ArrayList<Peer>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if (intent != null) {
			if (PICK_PEER_INTENT.equals(intent.getAction())) {
				returnResult = true;
			}
		}

		listAdapter = new PeerListAdapter<Peer>(this, peers);
		listAdapter.setNotifyOnChange(false);
		this.setListAdapter(listAdapter);

		ListView lv = getListView();

		// TODO Long click listener for more options, eg text message
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				try {
					Peer p = listAdapter.getItem(position);
					if (returnResult) {
						Log.i(TAG, "returning selected peer " + p);
						Intent returnIntent = new Intent();
						returnIntent.putExtra(
								CONTACT_NAME,
								p.getContactName());
						returnIntent.putExtra(SID, p.sid.toHex());
						returnIntent.putExtra(CONTACT_ID, p.contactId);
						returnIntent.putExtra(DID, p.did);
						returnIntent.putExtra(NAME, p.name);
						returnIntent.putExtra(RESOLVED,
								p.cacheUntil > SystemClock.elapsedRealtime());
						setResult(Activity.RESULT_OK, returnIntent);
						finish();
					} else {
						Log.i(TAG, "calling selected peer " + p);
						CallHandler.dial(p);
					}
				} catch (Exception e) {
					ServalBatPhoneApplication.context.displayToastMessage(e
							.getMessage());
					Log.e("BatPhone", e.getMessage(), e);
				}
			}
		});

	}

	@Override
	protected void onNewIntent(Intent intent) {
		// TODO Auto-generated method stub
		super.onNewIntent(intent);
		if (intent != null) {
			if (PICK_PEER_INTENT.equals(intent.getAction())) {
				returnResult = true;
			}
		}
	}

	private void peerUpdated(Peer p) {
		if (!peers.contains(p)){
			if (!p.isReachable())
				return;
			peers.add(p);
		}
		Collections.sort(peers, new PeerComparator());
		listAdapter.notifyDataSetChanged();
	}

	private IPeerListListener listener = new IPeerListListener() {
		@Override
		public void peerChanged(final Peer p) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					peerUpdated(p);
				};

			});
		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		PeerListService.removeListener(listener);
		displayed = false;
		peers.clear();
		listAdapter.notifyDataSetChanged();
	}

	@Override
	protected void onResume() {
		super.onResume();
		displayed = true;

		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				PeerListService.addListener(listener);
				return null;
			}

		}.execute();
	}

}
