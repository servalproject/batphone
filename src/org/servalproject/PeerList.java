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

import org.servalproject.account.AccountService;
import org.servalproject.batphone.BatPhone;
import org.servalproject.messages.ShowConversationActivity;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.SubscriberId;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 *
 * @author jeremy
 *
 *         Peer List fetches a list of known peers from the PeerListService.
 *         When a peer is received from the service this activity will attempt
 *         to resolve the peer by calling ServalD in an async task.
 */
public class PeerList extends ListActivity {

	Adapter listAdapter;

	boolean displayed = false;

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

	class Adapter extends ArrayAdapter<Peer> {
		public Adapter(Context context) {
			super(context, R.layout.peer, R.id.Number, peers);
		}

		@Override
		public View getView(final int position, View convertView,
				ViewGroup parent) {
			View ret = super.getView(position, convertView, parent);
			View chat = ret.findViewById(R.id.chat);
			Peer p = listAdapter.getItem(position);

			TextView displaySid = (TextView) ret.findViewById(R.id.sid);
			displaySid.setText(p.sid.abbreviation());

			chat.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Peer p = listAdapter.getItem(position);

					// Send MeshMS by SID
					Intent intent = new Intent(
							ServalBatPhoneApplication.context,
							ShowConversationActivity.class);
					intent.putExtra("recipient", p.sid.toString());
					PeerList.this.startActivity(intent);
				}
			});

			View contact = ret.findViewById(R.id.add_contact);
			if (p.contactId >= 0) {
				contact.setVisibility(View.INVISIBLE);
			} else {
				contact.setVisibility(View.VISIBLE);
				contact.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Peer p = listAdapter.getItem(position);

						// Create contact if required

						if (p.contactId == -1) {
							if ("".equals(p.getContactName()))
								p.setContactName(p.name);

							p.contactId = AccountService.addContact(
									PeerList.this, p.getContactName(), p.sid,
									p.did);
						}
						v.setVisibility(View.INVISIBLE);

						// now display/edit contact

						// Work out how to get the contact id from here, and
						// then open it for editing.

						// Intent intent = new Intent(Intent.ACTION_VIEW,
						// Uri.parse(
						// "content://contacts/people/" + p.contactId));
						// PeerList.this.startActivity(intent);
					}
				});
			}

			return ret;
		}

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		handler = new Handler();

		Intent intent = getIntent();
		if (intent != null) {
			if (PICK_PEER_INTENT.equals(intent.getAction())) {
				returnResult = true;
			}
		}

		listAdapter = new Adapter(this);
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
						returnIntent.putExtra(SID, p.sid.toString());
						returnIntent.putExtra(CONTACT_ID, p.contactId);
						returnIntent.putExtra(DID, p.did);
						returnIntent.putExtra(NAME, p.name);
						returnIntent.putExtra(RESOLVED,
								p.cacheUntil > SystemClock.elapsedRealtime());
						setResult(Activity.RESULT_OK, returnIntent);
						finish();
					} else if (!p.sid.isBroadcast()) {
						Log.i(TAG, "calling selected peer " + p);
						BatPhone.callBySid(p.sid);
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

	private IPeerListListener listener = new IPeerListListener() {
		@Override
		public void peerChanged(final Peer p) {

			if (p.cacheUntil <= SystemClock.elapsedRealtime()) {
				unresolved.put(p.sid, p);
				handler.post(refresh);
			}

			// if we haven't seen recent active network confirmation for the
			// existence of this peer, don't add to the UI
			if (!p.stillAlive())
				return;

			int pos = peers.indexOf(p);
			if (pos < 0) {
				peers.add(p);
			}
			Collections.sort(peers, new PeerComparator());
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					listAdapter.notifyDataSetChanged();
				}
			});
		}
	};

	ConcurrentMap<SubscriberId, Peer> unresolved = new ConcurrentHashMap<SubscriberId, Peer>();
	private Handler handler;

	private boolean searching = false;

	private Runnable refresh = new Runnable() {
		@Override
		public void run() {
			handler.removeCallbacks(refresh);
			if (searching || (!displayed))
				return;
			searching = true;

			new AsyncTask<Void, Peer, Void>() {
				@Override
				protected void onPostExecute(Void result) {
					searching = false;
				}

				@Override
				protected Void doInBackground(Void... params) {

					for (Peer p : unresolved.values()) {
						PeerListService.resolve(p);
						unresolved.remove(p.sid);
					}

					return null;
				}
			}.execute();
		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		displayed = false;
		handler.removeCallbacks(refresh);
		PeerListService.removeListener(listener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		displayed = true;
		PeerListService.peerCount(this);
		PeerListService.addListener(this, listener);
	}

}
