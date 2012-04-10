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

import org.servalproject.R;
import org.servalproject.meshms.SimpleMeshMS;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * activity used to send a new message
 */
public class NewMessageActivity extends Activity implements OnClickListener {

	/*
	 * private class level constants
	 */
	// request code for the contact picker activity
	private final int PICK_CONTACT_REQUEST = 1;

	// codes to determine which dialog to show
	private final int DIALOG_RECIPIENT_EMPTY = 0;
	private final int DIALOG_RECIPIENT_INVALID = 1;
	private final int DIALOG_CONTENT_EMPTY = 2;

	private final String TAG = "NewMessageActivity";

	/*
	 * private class level variables
	 */
	private final ContactAccessor contactAccessor = new ContactAccessor();
	private TextView contentLength;
	private String contentLengthTemplate;

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.new_message);

		// capture the click on the button
		Button button = (Button) findViewById(R.id.new_message_ui_btn_lookup_contact);
		button.setOnClickListener(this);

		button = (Button) findViewById(R.id.new_message_ui_btn_send_message);
		button.setOnClickListener(this);

		contentLengthTemplate = getString(R.string.new_message_ui_txt_length);

		contentLength = (TextView) findViewById(R.id.new_message_ui_txt_length);

		TextView content = (TextView) findViewById(R.id.new_message_ui_txt_content);
		content.addTextChangedListener(contentWatcher);

	}

	// keep track of the number of characters remaining in the description
	private final TextWatcher contentWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(Editable arg0) {

		}

		@Override
		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
				int arg3) {

		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {

			// contentLength.setText(Integer.toString(s.length()));
			contentLength.setText(String.format(contentLengthTemplate,
					s.length()));
		}
	};

	/*
	 * (non-Javadoc)
	 *
	 * @see android.view.View.OnClickListener#onClick(android.view.View)
	 */
	@Override
	public void onClick(View v) {
		// work out which button was clicked
		if (v.getId() == R.id.new_message_ui_btn_lookup_contact) {
			// show the standard contact picker activity
			Intent mContactIntent = new Intent(Intent.ACTION_PICK,
					Contacts.CONTENT_URI);
			startActivityForResult(mContactIntent, PICK_CONTACT_REQUEST);
		} else if (v.getId() == R.id.new_message_ui_btn_send_message) {
			// send the new MeshMS message
			sendMessage();
		}

	}

	/*
	 * send a new MeshMS message
	 */
	private void sendMessage() {

		// validate the fields
		TextView mTextView = (TextView) findViewById(R.id.new_message_ui_txt_recipient);
		if (TextUtils.isEmpty(mTextView.getText()) == true) {
			showDialog(DIALOG_RECIPIENT_EMPTY);
			return;
		} else if (TextUtils.isDigitsOnly(mTextView.getText()) == false) {
			showDialog(DIALOG_RECIPIENT_INVALID);
			return;
		}

		String mRecipient = mTextView.getText().toString();

		mTextView = (TextView) findViewById(R.id.new_message_ui_txt_content);

		if (TextUtils.isEmpty(mTextView.getText()) == true) {
			showDialog(DIALOG_CONTENT_EMPTY);
			return;
		}

		String mContent = mTextView.getText().toString();

		// compile a new simple message
		SimpleMeshMS mMessage = new SimpleMeshMS(mRecipient, mContent);

		// send the message
		Intent mMeshMSIntent = new Intent(
				"org.servalproject.meshms.SEND_MESHMS");
		mMeshMSIntent.putExtra("simple", mMessage);
		startService(mMeshMSIntent);

		saveMessage(mMessage);

		// keep the user informed
		Toast.makeText(getApplicationContext(),
				R.string.new_message_ui_toast_sent_successfully,
				Toast.LENGTH_LONG).show();
		finish();

	}

	// save the message
	private void saveMessage(SimpleMeshMS message) {

		// lookup the thread id
		// see if there is already a thread with this recipient
		ContentResolver mContentResolver = getContentResolver();
		int mThreadId = MessageUtils.getThreadId(message, mContentResolver);

		// save the message
		if (mThreadId != -1) {
			int mMessageId = MessageUtils.saveReceivedMessage(
					message,
					mContentResolver,
					mThreadId);

			if (mMessageId != -1) {
				Log.i(TAG, "New message saved with thread '" + mThreadId
						+ "' and message '" + mMessageId + "'");
			}
		} else {
			Log.e(TAG, "unable to save new message");
		}
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
		case DIALOG_RECIPIENT_EMPTY:
			mBuilder.setMessage(R.string.new_message_ui_dialog_recipient_empty)
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

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// check to make sure the contact is returning to us
		// and that the user selected something
		if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
			// load the phone number from the selected contact
			getPhoneNumber(data.getData());
		}
	}

	/*
	 * The following method originally from:
	 * http://developer.android.com/resources
	 * /samples/BusinessCard/src/com/example
	 * /android/businesscard/BusinessCardActivity.html
	 *
	 * Copyright (C) 2009 The Android Open Source Project
	 *
	 * Licensed under the Apache License, Version 2.0 (the "License"); you may
	 * not use this file except in compliance with the License. You may obtain a
	 * copy of the License at
	 *
	 * http://www.apache.org/licenses/LICENSE-2.0
	 *
	 * Unless required by applicable law or agreed to in writing, software
	 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
	 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
	 * License for the specific language governing permissions and limitations
	 * under the License.
	 */

	/**
	 * Load contact information on a background thread.
	 */
	private void getPhoneNumber(Uri contactUri) {

		/*
		 * We should always run database queries on a background thread. The
		 * database may be locked by some process for a long time. If we locked
		 * up the UI thread while waiting for the query to come back, we might
		 * get an "Application Not Responding" dialog.
		 */
		AsyncTask<Uri, Void, ContactInfo> task = new AsyncTask<Uri, Void, ContactInfo>() {

			@Override
			protected ContactInfo doInBackground(Uri... uris) {
				return contactAccessor.loadContact(getContentResolver(),
						uris[0]);
			}

			@Override
			protected void onPostExecute(ContactInfo result) {
				bindView(result);
			}
		};

		task.execute(contactUri);
	}

	/**
	 * Displays contact information: name and phone number.
	 */
	protected void bindView(ContactInfo contactInfo) {
		TextView txtRecipient = (TextView) findViewById(R.id.new_message_ui_txt_recipient);
		txtRecipient.setText(
				contactInfo.getPhoneNumber().replace("-", "").trim()
				);
	}
}
