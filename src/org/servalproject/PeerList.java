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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.servalproject.account.AccountService;
import org.servalproject.batphone.BatPhone;
import org.servalproject.messages.NewMessageActivity;
import org.servalproject.servald.DidResult;
import org.servalproject.servald.ResultCallback;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalDResult;
import org.servalproject.servald.SubscriberId;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class PeerList extends ListActivity {

	Adapter listAdapter;

	boolean displayed = false;

	class Adapter extends ArrayAdapter<Peer> {
		public Adapter(Context context) {
			super(context, R.layout.peer, R.id.Number);
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
							NewMessageActivity.class);
					intent.putExtra("recipient", "sid:" + p.sid.toString());
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
							if (p.contactName == null)
								p.contactName = p.name;

							p.contactId = AccountService.addContact(
									PeerList.this, p.contactName, p.sid, p.did);
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
		listAdapter = new Adapter(this);
		listAdapter.setNotifyOnChange(false);
		this.setListAdapter(listAdapter);

		ListView lv = getListView();

		// TODO Long click listener for more options, eg text message
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Peer p=listAdapter.getItem(position);
				BatPhone.callBySid(p);
			}
		  });
	}

	private class Peer extends DidResult{
		boolean displayed = false;
		int score;
		long contactId = -1;
		String contactName;

		@Override
		public String toString() {
			if (contactName != null && !contactName.equals(""))
				return contactName;
			if (name != null && !name.equals(""))
				return name;
			if (did != null && !did.equals(""))
				return did;
			return sid.abbreviation();
		}
	}

	Map<SubscriberId, Peer> peerMap = new HashMap<SubscriberId, Peer>();
	List<Peer> unresolved = new LinkedList<Peer>();
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
				protected void onProgressUpdate(Peer... values) {
					if (!values[0].displayed) {
						listAdapter.add(values[0]);
						values[0].displayed = true;
					}
					listAdapter.notifyDataSetChanged();
				}

				@Override
				protected void onPostExecute(Void result) {
					searching = false;
					if (displayed)
						handler.postDelayed(refresh, 1000);
				}

				@Override
				protected Void doInBackground(Void... params) {
					Log.v("BatPhone", "Fetching subscriber list");
					ServalD.command(new ResultCallback() {
						@Override
						public boolean result(String value) {
							SubscriberId sid = new SubscriberId(value);
							Peer p = peerMap.get(sid);
							if (p == null) {
								p = new Peer();
								p.sid = sid;
								peerMap.put(sid, p);

								p.contactId = AccountService.getContactId(
										getContentResolver(), sid);

								if (p.contactId >= 0)
									p.contactName = AccountService
											.getContactName(
													getContentResolver(),
													p.contactId);

								publishProgress(p);
								unresolved.add(p);
							}
							return true;
						}
					}, "id", "peers");

					// TODO these queries need to be done in parallel!!!!
					// perhaps internal to servald?

					Iterator<Peer> i = unresolved.iterator();
					while (i.hasNext()) {
						Peer p = i.next();

						Log.v("BatPhone",
								"Fetching details for " + p.sid.toString());

						ServalDResult result = ServalD.command("node", "info",
								p.sid.toString(), "resolvedid");

						StringBuilder sb = new StringBuilder("{");
						for (int j = 0; j < result.outv.length; j++) {
							if (j > 0)
								sb.append(", ");
							sb.append(result.outv[j]);
						}
						sb.append('}');
						Log.v("BatPhone", "Output: " + sb);
						if (result.outv[0].equals("record")
								&& result.outv[3].equals("found")) {
							p.score = Integer.parseInt(result.outv[8]);
							boolean resolved = false;

							if (!result.outv[10].equals("name-not-resolved")) {
								p.name = result.outv[10];
								resolved = true;
							}
							if (!result.outv[5].equals("did-not-resolved")) {
								p.did = result.outv[5];
								resolved = true;
							}

							publishProgress(p);
							if (resolved)
								i.remove();
						}
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
	}

	@Override
	protected void onResume() {
		super.onResume();
		displayed = true;
		refresh.run();
	}

}
