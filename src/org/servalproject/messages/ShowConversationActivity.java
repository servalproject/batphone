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
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.account.AccountService;
import org.servalproject.rhizome.MeshMS;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Identity;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalD;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.SubscriberId;

/**
 * activity to show a conversation thread
 *
 */
public class ShowConversationActivity extends ListActivity implements OnClickListener {

	private final String TAG = "ShowConversationActivity";
	private ServalBatPhoneApplication app;
	private Identity identity;
	private Peer recipient;
	// the message text field
	private ListView list;
	private TextView message;
	private CursorAdapter mDataAdapter;

	BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(MeshMS.NEW_MESSAGES)) {
				populateList();
			}
		}

	};

	@Override
	public void onClick(View view) {
		switch (view.getId()){
			case R.id.show_message_ui_btn_send_message:
				sendMessage();
				break;
		}
	}

	private IPeerListListener peerListener = new IPeerListListener(){
		@Override
		public void peerChanged(Peer p) {
			if (p == recipient){
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						TextView recipientView = (TextView) findViewById(R.id.show_conversation_ui_recipient);
						recipientView.setText(recipient.toString());
					}
				});
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
        app = ServalBatPhoneApplication.context;
		setContentView(R.layout.show_conversation);
        this.identity = Identity.getMainIdentity();

		message = (TextView) findViewById(R.id.show_conversation_ui_txt_content);
		list = getListView();

		// get the thread id from the intent
		Intent mIntent = getIntent();
		String did = null;
		SubscriberId recipientSid = null;

		if (Intent.ACTION_SENDTO.equals(mIntent.getAction())) {
			Uri uri = mIntent.getData();
			Log.v(TAG, "Received " + mIntent.getAction() + " " + uri.toString());
			if (uri != null) {
				if (uri.getScheme().equals("sms")
						|| uri.getScheme().equals("smsto")) {
					did = uri.getSchemeSpecificPart();
					did = did.trim();
					if (did.endsWith(","))
						did = did.substring(0, did.length() - 1).trim();
					if (did.indexOf("<") > 0)
						did = did.substring(did.indexOf("<") + 1,
								did.indexOf(">")).trim();

					Log.v(TAG, "Parsed did " + did);
				}
			}
		}

		try {

			{
				String recipientSidString = mIntent.getStringExtra("recipient");
				if (recipientSidString != null)
					recipientSid = new SubscriberId(recipientSidString);
			}

			if (recipientSid == null && did != null) {
				// lookup the sid from the contacts database
				long contactId = AccountService.getContactId(
						getContentResolver(), did);
				if (contactId >= 0)
					recipientSid = AccountService.getContactSid(
							getContentResolver(),
							contactId);

				if (recipientSid == null) {
					// TODO scan the network first and only complain when you
					// attempt to send?
					throw new UnsupportedOperationException(
							"Subscriber id not found for phone number " + did);
				}
			}

			if (recipientSid == null)
				throw new UnsupportedOperationException(
						"No Subscriber id found");

			recipient = PeerListService.getPeer(recipientSid);
			TextView recipientView = (TextView) findViewById(R.id.show_conversation_ui_recipient);
			recipientView.setText(recipient.toString());

		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			ServalBatPhoneApplication.context.displayToastMessage(e
					.getMessage());
			finish();
		}

        findViewById(R.id.show_message_ui_btn_send_message).setOnClickListener(this);

		list.setStackFromBottom(true);
		list.setTranscriptMode(
				ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);

	}

	private void sendMessage() {
		// send the message
		try {
			CharSequence messageText = message.getText();
			if (messageText==null || "".equals(messageText.toString()))
				return;
			ServalDCommand.sendMessage(identity.subscriberId, recipient.sid, messageText.toString());

			message.setText("");
			populateList();

		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			ServalBatPhoneApplication.context.displayToastMessage(e
					.getMessage());
		}
	}

	/*
	 * get the required data and populate the cursor
	 */
	private void populateList() {
		if (!app.isMainThread()) {
			// refresh the message list
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					populateList();
				}
			});
			return;
		}
		try{
			Cursor cursor = ServalD.listMessages(identity.subscriberId, recipient.sid);
			cursor = new FlipCursor(cursor);
			if (mDataAdapter==null){
				mDataAdapter = new CursorAdapter(this, cursor, false) {

					public int getViewTypeCount() {
						return 3;
					}

					@Override
					public int getItemViewType(int position) {
						try {
							Cursor cursor = this.getCursor();
							cursor.moveToPosition(position);
							int typeCol = cursor.getColumnIndexOrThrow("type");
							String type = cursor.getString(typeCol);
							if (type.indexOf('>')>=0)
								return 0;
							if (type.indexOf('<')>=0)
								return 1;
							return 2;
						} catch (Exception e) {
							Log.e(TAG, e.getMessage(), e);
						}
						return IGNORE_ITEM_VIEW_TYPE;
					}

					@Override
					public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
						View ret=null;
						try{
							LayoutInflater inflater = LayoutInflater.from(context);
							int typeCol = cursor.getColumnIndexOrThrow("type");
							String type = cursor.getString(typeCol);
							if (type.indexOf('>')>=0)
								ret=inflater.inflate(
										R.layout.show_conversation_item_us, viewGroup,
										false);
							else if (type.indexOf('<')>=0)
								ret=inflater.inflate(
										R.layout.show_conversation_item_them, viewGroup,
										false);
							else
								ret=inflater.inflate(
										R.layout.show_conversation_item_status, viewGroup,
										false);
						}catch (Exception e){
							Log.e(TAG, e.getMessage(), e);
						}
						return ret;
					}

					@Override
					public void bindView(View view, Context context, Cursor cursor) {
						try{
							int messageCol = cursor.getColumnIndexOrThrow("message");

							String message = cursor.getString(messageCol);

							TextView messageText = (TextView)view.findViewById(R.id.message_text);
							messageText.setText(message);
						}catch (Exception e){
							Log.e(TAG, e.getMessage(), e);
						}
					}
				};
				setListAdapter(mDataAdapter);
			}else{
				mDataAdapter.changeCursor(cursor);
				mDataAdapter.notifyDataSetChanged();
			}
		}catch(Exception e){
			Log.e(TAG, e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		PeerListService.removeListener(this.peerListener);
		this.unregisterReceiver(receiver);
		if (mDataAdapter != null) {
			mDataAdapter.changeCursor(null);
		}
        app.meshMS.markRead(recipient.sid);
		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onResume()
	 */
	@Override
	public void onResume() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(MeshMS.NEW_MESSAGES);
		this.registerReceiver(receiver, filter);
		// get the data
		populateList();
		PeerListService.addListener(this.peerListener);
		super.onResume();
	}
}
