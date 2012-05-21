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

import java.io.InputStream;

import org.servalproject.meshms.SimpleMeshMS;
import org.servalproject.provider.MessagesContract;
import org.servalproject.provider.ThreadsContract;
import org.servalproject.servald.Identities;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

/**
 * various utility methods for dealing with messages
 */
public class MessageUtils {

	/**
	 * lookup the conversation thread id
	 *
	 * @param message
	 *            the SimpleMeshMS object representing the message
	 * @param contentResolver
	 *            a content resolver to use to access the DB
	 * @return the id number of the thread
	 */
	public static int getThreadId(SimpleMeshMS message,
			ContentResolver contentResolver) {

		int mThreadId = -1;

		// define the content helper variables
		String[] mProjection = new String[1];
		mProjection[0] = ThreadsContract.Table._ID;

		String mSelection = ThreadsContract.Table.PARTICIPANT_PHONE + " = ?";

		String[] mSelectionArgs = new String[1];
		if (message.sender.equals(Identities.getCurrentIdentity()))
			mSelectionArgs[0] = message.recipient.toString();
		else
			mSelectionArgs[0] = message.sender.toString();

		// lookup the thread id
		Cursor mCursor = contentResolver.query(
				ThreadsContract.CONTENT_URI,
				mProjection,
				mSelection,
				mSelectionArgs,
				null);

		// check on what was returned
		if (mCursor == null) {
			Log.e("MessageUtils",
					"a null cursor was returned when looking up Thread info");
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

			if (message.sender.equals(Identities.getCurrentIdentity()))
				mValues.put(ThreadsContract.Table.PARTICIPANT_PHONE, message.recipient.toString());
			else
				mValues.put(ThreadsContract.Table.PARTICIPANT_PHONE, message.sender.toString());

			Uri mNewRecord = contentResolver.insert(
					ThreadsContract.CONTENT_URI,
					mValues);

			mThreadId = Integer.parseInt(mNewRecord.getLastPathSegment());
		}

		mCursor.close();

		return mThreadId;
	}

	/**
	 * save the content of a received message
	 *
	 * @param message
	 *            the SimpleMeshMS object representing the message
	 * @param contentResolver
	 *            a content resolver to use to access the DB
	 * @param threadId
	 *            the id of the thread to which this conversation belongs
	 * @return the id of the newly created message record
	 */
	public static int saveReceivedMessage(SimpleMeshMS message,
			ContentResolver contentResolver, int threadId) {

		int mMessageId = -1;

		// build the list of new values
		ContentValues mValues = new ContentValues();

		mValues.put(MessagesContract.Table.THREAD_ID, threadId);
		mValues.put(MessagesContract.Table.RECIPIENT_PHONE, message.recipient.toString());
		mValues.put(MessagesContract.Table.SENDER_PHONE, message.sender.toString());
		mValues.put(MessagesContract.Table.MESSAGE, message.content);
		mValues.put(MessagesContract.Table.RECEIVED_TIME, message.timestamp);

		Uri mNewRecord = contentResolver.insert(
				MessagesContract.CONTENT_URI,
				mValues);

		mMessageId = Integer.parseInt(mNewRecord.getLastPathSegment());

		return mMessageId;
	}

	/**
	 * save the content of a sent message
	 *
	 * @param message
	 *            the SimpleMeshMS object representing the message
	 * @param contentResolver
	 *            a content resolver to use to access the DB
	 * @param threadId
	 *            the id of the thread to which this conversation belongs
	 * @return the id of the newly created message record
	 */
	public static int saveSentMessage(SimpleMeshMS message,
			ContentResolver contentResolver, int threadId) {

		int mMessageId = -1;

		// build the list of new values
		ContentValues mValues = new ContentValues();

		mValues.put(MessagesContract.Table.THREAD_ID, threadId);
		mValues.put(MessagesContract.Table.RECIPIENT_PHONE, message.recipient.toString());
		mValues.put(MessagesContract.Table.SENDER_PHONE, message.sender.toString());
		mValues.put(MessagesContract.Table.MESSAGE, message.content);
		mValues.put(MessagesContract.Table.SENT_TIME, message.timestamp);

		Uri mNewRecord = contentResolver.insert(
				MessagesContract.CONTENT_URI,
				mValues);

		mMessageId = Integer.parseInt(mNewRecord.getLastPathSegment());

		return mMessageId;
	}

	/**
	 * method used to lookup the id of a contact photo id
	 *
	 * @param context
	 *            a context object used to get a content resolver
	 * @param phoneNumber
	 *            the phone number of the contact
	 *
	 * @return the id of the contact
	 */
	public static long lookupPhotoId(Context context, String phoneNumber) {

		long mPhotoId = -1;

		Uri mLookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
				Uri.encode(phoneNumber));

		String[] mProjection = new String[2];
		mProjection[0] = PhoneLookup._ID;
		mProjection[1] = PhoneLookup.PHOTO_ID;

		Cursor mCursor = context.getContentResolver().query(
				mLookupUri,
				mProjection,
				null,
				null,
				null);

		if (mCursor.getCount() > 0) {
			mCursor.moveToFirst();

			mPhotoId = mCursor.getLong(mCursor
					.getColumnIndex(PhoneLookup._ID));

			mCursor.close();
		}

		return mPhotoId;
	}

	/**
	 * retrieve the contact photo given a contact id
	 *
	 * @param context
	 *            a context object used to get a content resolver
	 * @param id
	 *            the id number of contact
	 *
	 * @return the bitmap of the photo or null
	 */
	public static Bitmap loadContactPhoto(Context context, long id) {
		Uri uri = ContentUris.withAppendedId(
				ContactsContract.Contacts.CONTENT_URI, id);

		InputStream input = ContactsContract.Contacts
				.openContactPhotoInputStream(context.getContentResolver(), uri);
		if (input == null) {
			return null;
		}
		return BitmapFactory.decodeStream(input);
	}

}
