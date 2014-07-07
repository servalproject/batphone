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
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.MeshMS;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Identity;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servaldna.meshms.MeshMSConversation;
import org.servalproject.servaldna.meshms.MeshMSConversationList;
import org.servalproject.ui.SimpleAdapter;

import java.util.List;

/**
 * main activity to display the list of messages
 */
public class MessagesListActivity extends ListActivity implements
		OnItemClickListener, IPeerListListener, SimpleAdapter.ViewBinder<MeshMSConversation> {

	private ServalBatPhoneApplication app;
	private final String TAG = "MessagesListActivity";
	private Identity identity;

	private SimpleAdapter<MeshMSConversation> adapter;

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(MeshMS.NEW_MESSAGES))
				populateList();
		}

	};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = ServalBatPhoneApplication.context;
		this.identity = Identity.getMainIdentity();
		setContentView(R.layout.messages_list);
		getListView().setOnItemClickListener(this);

		adapter = new SimpleAdapter<MeshMSConversation>(this, this);
		setListAdapter(adapter);
	}

	/*
	 * get the required data and populate the cursor
	 */
	private void populateList() {
		if (!app.isMainThread())
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					populateList();
				}
			});

		new AsyncTask<Void, Void, List<MeshMSConversation>>() {
			@Override
			protected void onPostExecute(List<MeshMSConversation> meshMSConversations) {
				if (meshMSConversations!=null)
					adapter.setItems(meshMSConversations);
			}

			@Override
			protected List<MeshMSConversation> doInBackground(Void... voids) {
				try{
					MeshMSConversationList conversations = app.server.getRestfulClient().meshmsListConversations(identity.subscriberId);
					return conversations.toList();
				} catch (Exception e) {
					app.displayToastMessage(e.getMessage());
					Log.e(TAG, e.getMessage(), e);
				}
				return null;
			}
		}.execute();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		PeerListService.removeListener(this);

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
		PeerListService.addListener(this);
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
			Intent mIntent = new Intent(this,
					org.servalproject.messages.ShowConversationActivity.class);
			mIntent.putExtra("recipient", adapter.getItem(position).theirSid.toHex().toUpperCase());
			startActivity(mIntent);
		}catch (Exception e){
			Log.e(TAG, e.getMessage(), e);
		}
	}

	@Override
	public void peerChanged(Peer p) {
		// force the list to re-bind everything

		if (!app.isMainThread()) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					adapter.notifyDataSetChanged();
				}
			});
			return;
		}

		adapter.notifyDataSetChanged();
	}

	@Override
	public long getId(int position, MeshMSConversation meshMSConversation) {
		return meshMSConversation._id;
	}

	@Override
	public int getViewType(int position, MeshMSConversation meshMSConversation) {
		return 0;
	}

	@Override
	public void bindView(int position, MeshMSConversation meshMSConversation, View view) {
		Peer p = PeerListService.getPeer(meshMSConversation.theirSid);

		TextView name = (TextView)view.findViewById(R.id.Name);
		name.setText(p.toString());

		TextView displaySid = (TextView) view.findViewById(R.id.sid);
		displaySid.setText(p.getSubscriberId().abbreviation());

		TextView displayNumber = (TextView) view.findViewById(R.id.Number);
		displayNumber.setText(p.getDid());

		Bitmap photo = null;
		ImageView image = (ImageView) view.findViewById(R.id.messages_list_item_image);
		if (p.contactId != -1)
			photo = MessageUtils.loadContactPhoto(this, p.contactId);

		// use photo if found else use default image
		if (photo != null) {
			image.setImageBitmap(photo);
		} else {
			image.setImageResource(R.drawable.ic_contact_picture);
		}
		name.setTypeface(null, meshMSConversation.isRead?Typeface.NORMAL:Typeface.BOLD);
	}

	@Override
	public int[] getResourceIds() {
		return new int[]{R.layout.messages_list_item};
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isEnabled(MeshMSConversation meshMSConversation) {
		return true;
	}
}
