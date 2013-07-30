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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
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
import org.servalproject.servald.Identity;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.SubscriberId;

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
	/*		case R.id.delete:
				AlertDialog.Builder b = new AlertDialog.Builder(
						ShowConversationActivity.this);
				b.setMessage("Do you want to delete this entire thread?");
				b.setNegativeButton("Cancel", null);
				b.setPositiveButton("Ok",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
												int which) {
								try {
									MessageUtils
											.deleteThread(
													ShowConversationActivity.this,
													threadId);
									ShowConversationActivity.this.finish();
								} catch (Exception e) {
									Log.e("BatPhone", e.getMessage(), e);
									ServalBatPhoneApplication.context
											.displayToastMessage(e.getMessage());
								}
							}

						});
				b.show();
				break;*/
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
        app = ServalBatPhoneApplication.context;
		setContentView(R.layout.show_conversation);
        this.identity = Identity.getMainIdentity();

		message = (TextView) findViewById(R.id.show_conversation_ui_txt_content);

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

			retrieveRecipient(getContentResolver(), recipientSid);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			ServalBatPhoneApplication.context.displayToastMessage(e
					.getMessage());
			finish();
		}

        findViewById(R.id.show_message_ui_btn_send_message).setOnClickListener(this);
        findViewById(R.id.delete).setOnClickListener(this);

		this.getListView().setStackFromBottom(true);
		this.getListView().setTranscriptMode(
				ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);

	}

	protected void retrieveRecipient(final ContentResolver resolver,
			final SubscriberId recipientSid) {

		recipient = PeerListService.getPeer(getContentResolver(),
				recipientSid);
		final TextView recipientView = (TextView) findViewById(R.id.show_conversation_ui_recipient);
		recipientView.setText(recipient.toString());

		if (recipient.cacheUntil < SystemClock.elapsedRealtime()) {
			new AsyncTask<Void, Peer, Void>() {
				@Override
				protected void onPostExecute(Void result) {
					recipientView.setText(recipient.toString());
				}

				@Override
				protected Void doInBackground(Void... params) {
					Log.v("BatPhone", "Resolving recipient");
					PeerListService.resolve(recipient);
					return null;
				}
			}.execute();
		}
	}

	private void sendMessage() {
		// send the message
		try {
			CharSequence messageText = message.getText();
			if (messageText==null || "".equals(messageText.toString()))
				return;
			ServalD.sendMessage(identity.subscriberId, recipient.sid, messageText.toString());

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
						return 2;
					}

					@Override
					public int getItemViewType(int position) {
						try {
							Cursor cursor = this.getCursor();
							cursor.moveToPosition(position);
							int senderCol = cursor.getColumnIndexOrThrow("sender");
							SubscriberId sid = new SubscriberId(cursor.getBlob(senderCol));
							if (identity.subscriberId.equals(sid)){
								return 0;
							}else{
								return 1;
							}
						} catch (Exception e) {
							Log.e(TAG, e.getMessage(), e);
						}
						return IGNORE_ITEM_VIEW_TYPE;
					}

					@Override
					public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
						View ret=null;
						try{
							int senderCol = cursor.getColumnIndexOrThrow("sender");
							SubscriberId sid = new SubscriberId(cursor.getBlob(senderCol));
							LayoutInflater inflater = LayoutInflater.from(context);
							if (identity.subscriberId.equals(sid)){
								ret=inflater.inflate(
										R.layout.show_conversation_item_us, viewGroup,
										false);
							}else{
								ret=inflater.inflate(
										R.layout.show_conversation_item_them, viewGroup,
										false);
							}
						}catch (Exception e){
							Log.e(TAG, e.getMessage(), e);
						}
						return ret;
					}

					@Override
					public void bindView(View view, Context context, Cursor cursor) {
						try{
							int statusCol = cursor.getColumnIndexOrThrow("status");
							int messageCol = cursor.getColumnIndexOrThrow("message");

							String status = cursor.getString(statusCol);
							String message = cursor.getString(messageCol);

							TextView messageText = (TextView)view.findViewById(R.id.message_text);
							messageText.setText(message);
							TextView statusText = (TextView)view.findViewById(R.id.status);
							statusText.setText(status);
						}catch (Exception e){
							Log.e(TAG, e.getMessage(), e);
						}
					}
				};
				setListAdapter(mDataAdapter);
			}else{
				mDataAdapter.changeCursor(cursor);
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
		super.onResume();
	}
}
