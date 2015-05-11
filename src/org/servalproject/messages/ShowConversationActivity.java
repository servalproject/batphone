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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
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
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.servaldna.meshms.MeshMSMessage;
import org.servalproject.servaldna.meshms.MeshMSMessageList;
import org.servalproject.ui.SimpleAdapter;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * activity to show a conversation thread
 *
 */
public class ShowConversationActivity extends ListActivity implements OnClickListener, SimpleAdapter.ViewBinder<Object>, IPeerListListener {

	private final String TAG = "ShowConversationActivity";
	private ServalBatPhoneApplication app;
	private Identity identity;
	private Peer recipient;
	// the message text field
	private ListView list;
	private TextView message;
	private SimpleAdapter<Object> adapter;

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

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
        app = ServalBatPhoneApplication.context;
		setContentView(R.layout.show_conversation);
        this.identity = Identity.getMainIdentity();

		message = (TextView) findViewById(R.id.show_conversation_ui_txt_content);
		list = getListView();
		adapter = new SimpleAdapter<Object>(this, this);
		list.setAdapter(adapter);

		// get the thread id from the intent
		Intent mIntent = getIntent();
		String did = null;
		SubscriberId recipientSid = null;

		if (Intent.ACTION_SENDTO.equals(mIntent.getAction())) {
			Uri uri = mIntent.getData();
			Log.v(TAG, "Received " + mIntent.getAction() + " " + uri);
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
			CharSequence messageText = message.getText();
			if (messageText==null || "".equals(messageText.toString()))
				return;
			new AsyncTask<String, Void, Boolean>(){
				@Override
				protected void onPostExecute(Boolean ret) {
					if (ret) {
						message.setText("");
						populateList();
					}
				}

				@Override
				protected Boolean doInBackground(String... args) {
					try {
						app.server.getRestfulClient().meshmsSendMessage(identity.subscriberId, recipient.sid, args[0]);
						return true;
					} catch (Exception e) {
						Log.e(TAG, e.getMessage(), e);
						app.displayToastMessage(e.getMessage());
					}
					return false;
				}
			}.execute(messageText.toString());
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
		new AsyncTask<Void, List<Object>, List<Object>>(){
			@Override
			protected void onPostExecute(List<Object> listItems) {
				if (listItems!=null)
					adapter.setItems(listItems);
			}

			@Override
			protected void onProgressUpdate(List<Object>... values) {
				this.onPostExecute(values[0]);
			}

			@Override
			protected List<Object> doInBackground(Void... voids) {
				try{
					MeshMSMessageList results = app.server.getRestfulClient().meshmsListMessages(identity.subscriberId, recipient.sid);
					MeshMSMessage item;
					LinkedList<Object> listItems = new LinkedList<Object>();
					boolean firstRead=true, firstDelivered=true, firstWindow = true;
					DateFormat df = DateFormat.getDateInstance();
					DateFormat tf = DateFormat.getTimeInstance(DateFormat.SHORT);
					long lastTimestamp = System.currentTimeMillis() / 1000;
					String lastDate = df.format(new Date());


					while((item = results.nextMessage())!=null){
						switch(item.type){
							case MESSAGE_SENT:
								if (item.isDelivered && firstDelivered){
									listItems.addFirst(getString(R.string.meshms_delivered));
									firstDelivered=false;
								}
								break;
							case MESSAGE_RECEIVED:
								if (item.isRead && firstRead){
									listItems.addFirst(getString(R.string.meshms_read));
									firstRead=false;
								}
								break;
							default:
								continue;
						}

						if (item.timestamp!=0){
							String messageDate = df.format(new Date(item.timestamp*1000));
							if (!messageDate.equals(lastDate)){
								// add date row whenever the calendar date changes
								listItems.addFirst("--- "+messageDate+" ---");
							}else if(lastTimestamp - item.timestamp >= 30*60){
								// add time row whenever 30 minutes have passed between messages
								listItems.addFirst("--- "+tf.format(new Date(item.timestamp*1000))+" ---");
							}
							lastDate = messageDate;
							lastTimestamp = item.timestamp;
						}

						listItems.addFirst(item);
						// show the first 10 items quickly
						if (firstWindow && listItems.size()>10) {
							firstWindow = false;
							this.publishProgress(new ArrayList<Object>(listItems));
						}
					}
					return new ArrayList<Object>(listItems);
				}catch(Exception e) {
					Log.e(TAG, e.getMessage(), e);
					app.displayToastMessage(e.getMessage());
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
		this.unregisterReceiver(receiver);
		app.runOnBackgroundThread(new Runnable() {
			@Override
			public void run() {
				app.meshMS.markRead(recipient.sid);
			}
		});
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
		PeerListService.addListener(this);
		populateList();
		super.onResume();
	}

	@Override
	public long getId(int position, Object object) {
		return 0;
	}

	@Override
	public int getViewType(int position, Object object) {
		if (object instanceof MeshMSMessage){
			MeshMSMessage meshMSMessage = (MeshMSMessage) object;
			switch (meshMSMessage.type) {
				case MESSAGE_SENT:
					return 0;
				case MESSAGE_RECEIVED:
					return 1;
				case ACK_RECEIVED:
					return 2;
			}
		}
		return 2;
	}

	@Override
	public void bindView(int position, Object object, View view) {
		TextView messageText = (TextView)view.findViewById(R.id.message_text);
		if (object instanceof MeshMSMessage) {
			MeshMSMessage meshMSMessage = (MeshMSMessage) object;
			messageText.setText(meshMSMessage.text);
		}else
			messageText.setText(object.toString());
	}

	@Override
	public int[] getResourceIds() {
		return new int[]{
			R.layout.show_conversation_item_us,
			R.layout.show_conversation_item_them,
			R.layout.show_conversation_item_status
		};
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isEnabled(Object object) {
		return false;
	}

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
}
