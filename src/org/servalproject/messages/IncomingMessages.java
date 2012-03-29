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

import org.servalproject.meshms.SimpleMeshMS;
import org.servalproject.provider.MessagesContract;
import org.servalproject.provider.ThreadsContract;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * receives the incoming MeshMS intents and processes them by putting them into
 * the database
 */
public class IncomingMessages extends BroadcastReceiver {

	/*
	 * private class level variables
	 */
	private final String TAG = "IncomingMessages";
	private final boolean V_LOG = true;

	@Override
	public void onReceive(Context context, Intent intent) {

		if (V_LOG) {
			Log.v(TAG, "Intent Received");
		}

		// check to see if this is a complex or simple message
		SimpleMeshMS mSimpleMessage = intent.getParcelableExtra("simple");

		if (mSimpleMessage == null) {
			Log.e(TAG, "missing SimpleMeshMS message");
			return;
		}

		// see if there is already a thread with this recipient
		ContentResolver mContentResolver = context.getContentResolver();

		int mThreadId = getThreadId(mSimpleMessage, mContentResolver);

		if (V_LOG) {
			Log.v(TAG, "Thread ID: " + mThreadId);
		}

		if (mThreadId != -1) {
			int mMessageId = saveMessage(
					mSimpleMessage,
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

	// private method to get the thread id of a message
	private int getThreadId(SimpleMeshMS message,
			ContentResolver contentResolver) {

		int mThreadId = -1;

		// define the content helper variables
		String[] mProjection = new String[1];
		mProjection[0] = ThreadsContract.Table._ID;

		String mSelection = ThreadsContract.Table.PARTICIPANT_PHONE + " = ?";

		String[] mSelectionArgs = new String[1];
		mSelectionArgs[0] = message.getSender();

		// lookup the thread id
		Cursor mCursor = contentResolver.query(
				ThreadsContract.CONTENT_URI,
				mProjection,
				mSelection,
				mSelectionArgs,
				null);

		// check on what was returned
		if (mCursor == null) {
			Log.e(TAG, "a null cursor was returned when looking up Thread info");
			return mThreadId;
		}

		// get a thread id if it exists
		if (mCursor.getCount() > 0) {

			mCursor.moveToFirst();

			mThreadId = mCursor.getInt(
					mCursor.getColumnIndex(ThreadsContract.Table._ID));

		} else {
			// add a new thread
			ContentValues mValues = new ContentValues();

			mValues.put(
					ThreadsContract.Table.PARTICIPANT_PHONE,
					message.getSender());

			Uri mNewRecord = contentResolver.insert(
					ThreadsContract.CONTENT_URI,
					mValues);

			mThreadId = Integer.parseInt(mNewRecord.getLastPathSegment());
		}

		mCursor.close();

		return mThreadId;
	}

	// private method to save the content of the message
	private int saveMessage(SimpleMeshMS message,
			ContentResolver contentResolver, int threadId) {

		int mMessageId = -1;

		// build the list of new values
		ContentValues mValues = new ContentValues();

		mValues.put(MessagesContract.Table.THREAD_ID, threadId);
		mValues.put(MessagesContract.Table.RECIPIENT_PHONE,
				message.getRecipient());
		mValues.put(MessagesContract.Table.SENDER_PHONE, message.getSender());
		mValues.put(MessagesContract.Table.MESSAGE, message.getContent());
		mValues.put(MessagesContract.Table.RECEIVED_TIME,
				message.getTimestamp());

		Uri mNewRecord = contentResolver.insert(
				MessagesContract.CONTENT_URI,
				mValues);

		mMessageId = Integer.parseInt(mNewRecord.getLastPathSegment());

		return mMessageId;

	}
}
