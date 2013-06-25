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

import java.io.IOException;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.account.AccountService;
import org.servalproject.meshms.IncomingMeshMS;
import org.servalproject.meshms.OutgoingMeshMS;
import org.servalproject.meshms.SimpleMeshMS;
import org.servalproject.provider.MessagesContract;
import org.servalproject.servald.Identity;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.SubscriberId;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

/**
 * activity to show a conversation thread
 *
 */
public class ShowConversationActivity extends ListActivity {

	/*
	 * private class level constants
	 */
	private final boolean V_LOG = true;
	private final String TAG = "ShowConversationActivity";
	private ShowConversationListAdapter mDataAdapter;

	/*
	 * private class level variables
	 */
	private int threadId = -1;

	private Peer recipient;
	protected final static int DIALOG_RECIPIENT_INVALID = 1;
	private final static int DIALOG_CONTENT_EMPTY = 2;

	private InputMethodManager imm;

	// the message text field
	private TextView message;

	BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(IncomingMeshMS.NEW_MESSAGES)) {
				refreshMessageList();
			}
		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		if (V_LOG) {
			Log.v(TAG, "on create called");
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.show_conversation);

		message = (TextView) findViewById(R.id.show_conversation_ui_txt_content);

		imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

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

		threadId = mIntent.getIntExtra("threadId", -1);

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

			if (threadId == -1) {
				// see if there is an existing conversation thread for this
				// recipient
				threadId = MessageUtils.getThreadId(recipientSid,
						getContentResolver());
			}

		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			ServalBatPhoneApplication.context.displayToastMessage(e
					.getMessage());
			finish();
		}

		Button sendButton = (Button) findViewById(R.id.show_message_ui_btn_send_message);
		sendButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if (recipient == null || recipient.sid == null) {
					showDialog(DIALOG_RECIPIENT_INVALID);
				} else if (message.getText() == null
						|| "".equals(message.getText())) {
					showDialog(DIALOG_CONTENT_EMPTY);
				} else {
					sendMessage(recipient, message);
				}
			}

		});

		Button deleteButton = (Button) findViewById(R.id.delete);
		deleteButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
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
			}

		});

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

	private void sendMessage(Peer recipient, final TextView text) {
		// send the message
		try {
			Identity main = Identity.getMainIdentity();
			SimpleMeshMS meshMs = new SimpleMeshMS(
					main.subscriberId,
					recipient.sid,
					main.getDid(),
					recipient.did,
					System.currentTimeMillis(),
					text.getText().toString()
					);

			OutgoingMeshMS.processSimpleMessage(meshMs);
			saveMessage(meshMs);

			message.setText("");
			refreshMessageList();

		} catch (Exception e) {
			Log.e("BatPhone", e.getMessage(), e);
			ServalBatPhoneApplication.context.displayToastMessage(e
					.getMessage());
		}
	}

	private void refreshMessageList() {
		// refresh the message list
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				populateList();
			}
		});
	}

	// save the message
	private void saveMessage(SimpleMeshMS message) throws IOException {
		ContentResolver contentResolver = getContentResolver();
		// save the message
		threadId = MessageUtils.saveSentMessage(message, contentResolver,
				threadId);
	}

	/*
	 * get the required data and populate the cursor
	 */
	private void populateList() {

		if (V_LOG) {
			Log.v(TAG, "get cursor called, current threadID = " + threadId);
		}

		// get a content resolver
		ContentResolver mContentResolver = getApplicationContext()
				.getContentResolver();

		Uri mUri = MessagesContract.CONTENT_URI;

		String mSelection = MessagesContract.Table.THREAD_ID + " = ?";
		String[] mSelectionArgs = new String[1];
		mSelectionArgs[0] = Integer.toString(threadId);

		String mOrderBy = MessagesContract.Table.RECEIVED_TIME + " ASC";

		Cursor cursor = mContentResolver.query(
				mUri,
				null,
				mSelection,
				mSelectionArgs,
				mOrderBy);

		// zero length arrays required by list adapter constructor,
		// manual matching to views & columns will occur in the bindView
		// method
		String[] mColumnNames = new String[0];
		int[] mLayoutElements = new int[0];

		if (mDataAdapter == null) {
			mDataAdapter = new ShowConversationListAdapter(
					this,
					R.layout.show_conversation_item_us,
					cursor,
					mColumnNames,
					mLayoutElements);

			setListAdapter(mDataAdapter);
		} else {
			mDataAdapter.changeCursor(cursor);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {

		if (V_LOG) {
			Log.v(TAG, "on pause called");
		}

		this.unregisterReceiver(receiver);
		if (mDataAdapter != null) {
			mDataAdapter.changeCursor(null);
		}
		MessageUtils.markThreadRead(this.getContentResolver(), threadId);
		IncomingMeshMS.updateNotification(this);
		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onResume()
	 */
	@Override
	public void onResume() {

		if (V_LOG) {
			Log.v(TAG, "on resume called");
		}
		IntentFilter filter = new IntentFilter();
		filter.addAction(IncomingMeshMS.NEW_MESSAGES);
		this.registerReceiver(receiver, filter);
		// get the data
		refreshMessageList();
		super.onResume();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.ListActivity#onDestroy()
	 */
	@Override
	public void onDestroy() {

		if (V_LOG) {
			Log.v(TAG, "on destroy called");
		}

		super.onDestroy();
	}

	/*
	 * dialog related methods
	 */

	/*
	 * callback method used to construct the required dialog (non-Javadoc)
	 *
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {

		// create the required alert dialog
		AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
		AlertDialog mDialog = null;

		switch (id) {
		case DIALOG_RECIPIENT_INVALID:
			mBuilder.setMessage(
					R.string.new_message_ui_dialog_recipient_invalid)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			mDialog = mBuilder.create();
			break;

		case DIALOG_CONTENT_EMPTY:
			mBuilder.setMessage(R.string.new_message_ui_dialog_content_empty)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			mDialog = mBuilder.create();

			break;
		default:
			mDialog = null;
		}

		return mDialog;
	}

}
