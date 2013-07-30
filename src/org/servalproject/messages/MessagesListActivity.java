/*
 * Copyright (C) 2012 The Serval Project
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
package org.servalproject.messages;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.TextView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.MeshMS;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Identity;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.SubscriberId;

/**
 * main activity to display the list of messages
 */
public class MessagesListActivity extends ListActivity implements
		OnItemClickListener, IPeerListListener {

	private ServalBatPhoneApplication app;
	private final String TAG = "MessagesListActivity";
	private Identity identity;
	private Cursor cursor;

	private CursorAdapter listAdapter;

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(MeshMS.NEW_MESSAGES)) {
				populateList();
			}
		}

	};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = ServalBatPhoneApplication.context;
		this.identity = Identity.getMainIdentity();
		setContentView(R.layout.messages_list);
		getListView().setOnItemClickListener(this);

		listAdapter = new CursorAdapter(this, null) {
			@Override
			public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
				LayoutInflater inflater = LayoutInflater.from(context);
				return inflater.inflate(R.layout.messages_list_item, viewGroup, false);
			}

			@Override
			public void bindView(View view, Context context, Cursor cursor) {
				try{
					int statusCol = cursor.getColumnIndexOrThrow("read");
					int recipientCol = cursor.getColumnIndexOrThrow("recipient");

					String status = cursor.getString(statusCol);
					SubscriberId recipient = new SubscriberId(cursor.getBlob(recipientCol));
					Peer p = PeerListService.getPeer(MessagesListActivity.this.getContentResolver(), recipient);
					if (p.cacheUntil <= SystemClock.elapsedRealtime())
						PeerListService.resolveAsync(p);
					TextView messageText = (TextView)view.findViewById(R.id.messages_list_item_title);
					messageText.setText(p.getDisplayName());
				}catch (Exception e){
					Log.e(TAG, e.getMessage(), e);
				}
			}
		};
	}

	/*
	 * get the required data and populate the cursor
	 */
	private void populateList() {

		if (cursor != null) {
			cursor.close();
			cursor = null;
		}

		try {
			cursor = ServalD.listConversations(identity.subscriberId);
			this.listAdapter.changeCursor(cursor);
			setListAdapter(listAdapter);
		} catch (ServalDFailureException e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		// play nice and close the cursor
		if (cursor != null) {
			cursor.close();
			cursor = null;
		}
		PeerListService.addListener(this, this);

		// unbind service
		this.unregisterReceiver(receiver);

		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onResume()
	 */
	@Override
	public void onResume() {
		populateList();

		IntentFilter filter = new IntentFilter();
		filter.addAction(MeshMS.NEW_MESSAGES);
		this.registerReceiver(receiver, filter);
		super.onResume();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {

		try{
			// work out the id of the item
			if (cursor.moveToPosition(position)) {

				int recipientCol = cursor.getColumnIndexOrThrow("recipient");
				SubscriberId recipient = new SubscriberId(cursor.getBlob(recipientCol));

				Intent mIntent = new Intent(this,
						org.servalproject.messages.ShowConversationActivity.class);
				mIntent.putExtra("recipient", recipient.toHex().toUpperCase());
				startActivity(mIntent);

			}
		}catch (Exception e){
			Log.e(TAG, e.getMessage(), e);
		}
	}

	@Override
	public void peerChanged(Peer p) {
		if (!app.isMainThread()) {
			// refresh the message list
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					listAdapter.notifyDataSetChanged();
				}
			});
			return;
		}

		listAdapter.notifyDataSetChanged();
	}
}
