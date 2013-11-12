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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.servalproject.batphone.CallHandler;
import org.servalproject.servald.AbstractId.InvalidHexException;
import org.servalproject.servald.AbstractJniResults;
import org.servalproject.servald.IPeer;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerComparator;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.SubscriberId;

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

/**
 *
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 *
 *         Peer List fetches a list of known peers from the PeerListService.
 *         When a peer is received from the service this activity will attempt
 *         to resolve the peer by calling ServalD in an async task.
 */
public class PeerList extends ListActivity {

	PeerListAdapter listAdapter;

	private boolean displayed = false;
	private static final String TAG = "PeerList";

	public static final String PICK_PEER_INTENT = "org.servalproject.PICK_FROM_PEER_LIST";

	public static final String CONTACT_NAME = "org.servalproject.PeerList.contactName";
	public static final String CONTACT_ID = "org.servalproject.PeerList.contactId";
	public static final String DID = "org.servalproject.PeerList.did";
	public static final String SID = "org.servalproject.PeerList.sid";
	public static final String NAME = "org.servalproject.PeerList.name";
	public static final String PROFILE_DATA = "org.servalproject.PeerList.profileData";
	public static final String RESOLVED = "org.servalproject.PeerList.resolved";

	private boolean returnResult = false;

	List<IPeer> peers = new ArrayList<IPeer>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if (intent != null) {
			if (PICK_PEER_INTENT.equals(intent.getAction())) {
				returnResult = true;
			}
		}

		listAdapter = new PeerListAdapter(this, peers);
		listAdapter.setNotifyOnChange(false);
		this.setListAdapter(listAdapter);

		ListView lv = getListView();

		// TODO Long click listener for more options, eg text message
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				try {
					Peer p = (Peer) listAdapter.getItem(position);
					if (returnResult) {
						Log.i(TAG, "returning selected peer " + p);
						Intent returnIntent = new Intent();
						returnIntent.putExtra(
								CONTACT_NAME,
								p.getContactName());
						returnIntent.putExtra(SID, p.sid.toString());
						returnIntent.putExtra(CONTACT_ID, p.contactId);
						returnIntent.putExtra(DID, p.did);
						returnIntent.putExtra(NAME, p.name);
						returnIntent.putExtra(PROFILE_DATA, p.profileData);
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

	private void peerUpdated(IPeer p) {
		if (!peers.contains(p))
			peers.add(p);
		Collections.sort(peers, new PeerComparator());
		listAdapter.notifyDataSetChanged();
	}

	private IPeerListListener listener = new IPeerListListener() {
		@Override
		public void peerChanged(final Peer p) {

			// if we haven't seen recent active network confirmation for the
			// existence of this peer, don't add to the UI
			if (p.sid.isBroadcast() || !p.stillAlive())
				return;

			if (p.cacheUntil <= SystemClock.elapsedRealtime())
				resolve(p);

			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					peerUpdated(p);
				};

			});
		}
	};

	ConcurrentMap<SubscriberId, Peer> unresolved = new ConcurrentHashMap<SubscriberId, Peer>();

	private boolean searching = false;

	private void search() {
		if (searching)
			return;
		searching = true;

		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				while (!unresolved.isEmpty()) {
					for (Peer p : unresolved.values()) {
						PeerListService.resolve(p);
						unresolved.remove(p.sid);
					}
				}
				searching = false;
				return null;
			}
		}.execute();
	}

	private synchronized void resolve(Peer p) {
		if (!displayed)
			return;

		unresolved.put(p.sid, p);
		search();
	}

	@Override
	protected void onPause() {
		super.onPause();
		PeerListService.removeListener(listener);
		Control.peerList = null;
		displayed = false;
		unresolved.clear();
		peers.clear();
		listAdapter.notifyDataSetChanged();
	}

	public void monitorConnected() {
		this.refresh();
	}

	private synchronized void refresh() {
		final long now = SystemClock.elapsedRealtime();
		ServalD.peers(new AbstractJniResults() {

			@Override
			public void putBlob(byte[] val) {
				try {
					if (!displayed)
						return;

					String value = new String(val);
					SubscriberId sid = new SubscriberId(value);
					PeerListService.peerReachable(getContentResolver(),
							sid, true);

					final Peer p = PeerListService.getPeer(
							getContentResolver(), sid);
					p.lastSeen = now;

					if (p.cacheUntil <= SystemClock.elapsedRealtime())
						unresolved.put(p.sid, p);

					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							peerUpdated(p);
						};

					});

				} catch (InvalidHexException e) {
					Log.e(TAG, e.toString(), e);
				}
			}
		});

		if (!displayed)
			return;

		for (Peer p : PeerListService.peers.values()) {
			if (p.lastSeen < now)
				PeerListService.peerReachable(getContentResolver(),
						p.sid, false);
		}

		if (!unresolved.isEmpty())
			search();
	}

	@Override
	protected void onResume() {
		super.onResume();
		displayed = true;
		Control.peerList = this;

		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				refresh();
				PeerListService.addListener(PeerList.this, listener);
				return null;
			}

		}.execute();
	}

}
