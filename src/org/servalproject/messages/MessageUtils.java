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
import java.io.InputStream;

import org.servalproject.meshms.SimpleMeshMS;
import org.servalproject.provider.MessagesContract;
import org.servalproject.provider.ThreadsContract;
import org.servalproject.servald.Identity;
import org.servalproject.servald.SubscriberId;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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
	 * Get a thread ID for an existing sender - recipient conversation. If none
	 * found, return -1
	 *
	 * @param sender
	 * @param recipient
	 * @param contentResolver
	 * @return threadId
	 */
	public static int getThreadId(SubscriberId recipient,
			ContentResolver contentResolver) {
		int mThreadId = -1;

		// define the content helper variables
		String[] mProjection = new String[1];
		mProjection[0] = ThreadsContract.Table._ID;

		String mSelection = ThreadsContract.Table.PARTICIPANT_PHONE + " = ?";

		String[] mSelectionArgs = new String[1];
		mSelectionArgs[0] = recipient.toString();

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

		}
		mCursor.close();

		return mThreadId;
	}

	/**
	 * lookup the conversation thread id
	 *
	 * @param message
	 *            the SimpleMeshMS object representing the message
	 * @param contentResolver
	 *            a content resolver to use to access the DB
	 * @return the id number of the thread
	 * @throws IOException
	 */
	private static int getThreadId(SimpleMeshMS message,
			ContentResolver contentResolver) throws IOException {

		SubscriberId recipient;
		Identity main = Identity.getMainIdentity();

		if (main != null && message.recipient.equals(main.subscriberId))
			recipient = message.sender;
		else
			recipient = message.recipient;

		int mThreadId = getThreadId(recipient, contentResolver);

		// int mThreadId = -1;
		//
		// // define the content helper variables
		// String[] mProjection = new String[1];
		// mProjection[0] = ThreadsContract.Table._ID;
		//
		// String mSelection = ThreadsContract.Table.PARTICIPANT_PHONE + " = ?";
		//
		// String[] mSelectionArgs = new String[1];
		// if (message.sender.equals(Identities.getCurrentIdentity()))
		// mSelectionArgs[0] = message.recipient.toString();
		// else
		// mSelectionArgs[0] = message.sender.toString();
		//
		// // lookup the thread id
		// Cursor mCursor = contentResolver.query(
		// ThreadsContract.CONTENT_URI,
		// mProjection,
		// mSelection,
		// mSelectionArgs,
		// null);
		//
		// // check on what was returned
		// if (mCursor == null) {
		// Log.e("MessageUtils",
		// "a null cursor was returned when looking up Thread info");
		// return mThreadId;
		// }
		//
		// // get a thread id if it exists
		// if (mCursor.getCount() > 0) {
		//
		// mCursor.moveToFirst();
		//
		// mThreadId = mCursor.getInt(
		// mCursor.getColumnIndex(ThreadsContract.Table._ID));
		//
		// }

		if (mThreadId == -1) {
			// add a new thread
			ContentValues mValues = new ContentValues();

			mValues.put(ThreadsContract.Table.PARTICIPANT_PHONE,
					recipient.toString());

			Uri mNewRecord = contentResolver.insert(
					ThreadsContract.CONTENT_URI,
					mValues);
			if (mNewRecord == null)
				throw new IOException("Insert failed to return thread id");
			mThreadId = Integer.parseInt(mNewRecord.getLastPathSegment());
		}

		return mThreadId;
	}

	public static int deleteThread(Context context, int threadId) {
		ContentResolver resolver = context.getContentResolver();
		// TODO use a query batch?
		int deleted = resolver.delete(
				MessagesContract.CONTENT_URI,
				MessagesContract.Table.THREAD_ID + " = ?",
				new String[] {
					Integer.toString(threadId)
				});
		deleted += resolver.delete(
				ThreadsContract.CONTENT_URI,
				ThreadsContract.Table._ID + " = ?",
				new String[] {
					Integer.toString(threadId)
				});
		return deleted;
	}

	/**
	 * save the content of a received message
	 *
	 * @param message
	 *            the object representing the message
	 * @param contentResolver
	 *            a content resolver to use to access the DB
	 * @return int array int[0] = thread Id, int[1] = the id of the newly
	 *         created message record
	 * @throws IOException
	 */
	public static int[] saveReceivedMessage(SimpleMeshMS message,
			ContentResolver contentResolver) throws IOException {

		int threadId = getThreadId(message, contentResolver);

		int messageId = -1;

		// build the list of new values
		ContentValues mValues = new ContentValues();

		mValues.put(MessagesContract.Table.THREAD_ID, threadId);
		mValues.put(MessagesContract.Table.RECIPIENT_PHONE, message.recipient.toString());
		mValues.put(MessagesContract.Table.SENDER_PHONE, message.sender.toString());
		mValues.put(MessagesContract.Table.MESSAGE, message.content);
		mValues.put(MessagesContract.Table.SENT_TIME, message.timestamp);
		mValues.put(MessagesContract.Table.RECEIVED_TIME,
				System.currentTimeMillis());

		Uri mNewRecord = contentResolver.insert(
				MessagesContract.CONTENT_URI,
				mValues);

		if (mNewRecord == null)
			throw new IOException("Insert failed to return uri");
		messageId = Integer.parseInt(mNewRecord.getLastPathSegment());

		int[] result = new int[] {
				threadId, messageId
		};

		return result;
	}

	public static int countUnseenMessages(ContentResolver contentResolver){
		Cursor result = contentResolver.query(MessagesContract.CONTENT_URI,
				new String[] {
					"count(*) as count"
				},
				"(" + MessagesContract.Table.NEW + " = 1)", null, null);
		if (result == null)
			return 0;
		try {
			if (!result.moveToFirst())
				return 0;
			return result.getInt(0);
		} finally {
			result.close();
		}
	}

	public static void markThreadRead(ContentResolver contentResolver, int threadId){
		ContentValues values = new ContentValues();
		values.put(MessagesContract.Table.READ, 1);
		values.put(MessagesContract.Table.NEW, 0);
		contentResolver.update(MessagesContract.CONTENT_URI, values,
				"(" + MessagesContract.Table.THREAD_ID + " = ? ) " +
						"and (" + MessagesContract.Table.READ + " = 0 )",
				new String[] {
			Integer.toString(threadId)
		});
	}

	/**
	 * save the content of a sent message
	 *
	 * @param message
	 *            the SimpleMeshMS object representing the message
	 * @param contentResolver
	 *            a content resolver to use to access the DB
	 * @param threadId
	 *            the id of the thread to which this conversation belongs. If
	 *            threadId == -1, a new thread ID will be generated.
	 * @return the id of the newly created message record
	 * @throws IOException
	 */
	public static int saveSentMessage(SimpleMeshMS message,
			ContentResolver contentResolver, int threadId) throws IOException {

		if (threadId == -1)
			threadId = getThreadId(message, contentResolver);

		// build the list of new values
		ContentValues mValues = new ContentValues();

		mValues.put(MessagesContract.Table.THREAD_ID, threadId);
		mValues.put(MessagesContract.Table.RECIPIENT_PHONE, message.recipient.toString());
		mValues.put(MessagesContract.Table.SENDER_PHONE, message.sender.toString());
		mValues.put(MessagesContract.Table.MESSAGE, message.content);
		mValues.put(MessagesContract.Table.SENT_TIME, message.timestamp);
		mValues.put(MessagesContract.Table.RECEIVED_TIME, message.timestamp);
		mValues.put(MessagesContract.Table.READ, 1);
		mValues.put(MessagesContract.Table.NEW, 0);

		if (contentResolver.insert(
				MessagesContract.CONTENT_URI,
				mValues) == null)
			throw new IOException("Unable to insert message");

		return threadId;
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
		try {
			Uri uri = ContentUris.withAppendedId(
					ContactsContract.Contacts.CONTENT_URI, id);

			InputStream input = ContactsContract.Contacts
					.openContactPhotoInputStream(context.getContentResolver(),
							uri);
			if (input == null) {
				return null;
			}
			return BitmapFactory.decodeStream(input);
		} catch (Exception e) {
			// catch any security exceptions in APIv14
			Log.e("MessageUtils", e.getMessage(), e);
			return null;
		}
	}

	public static class MessageIntentException extends Exception {
		private static final long serialVersionUID = 1L;
		public MessageIntentException(String message) {
			super(message);
		}
		public MessageIntentException(String message, Throwable e) {
			super(message, e);
		}
	}

	/** Helper function to extract a SimpleMeshMS object from an Intent in a consistent
	 * fashion.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static SimpleMeshMS getSimpleMessageFromIntent(Intent intent) throws MessageIntentException {
		SimpleMeshMS message = intent.getParcelableExtra("simple");
		if (message != null)
			return message;
		SubscriberId sender;
		SubscriberId recipient;
		try {
			sender = new SubscriberId(intent.getStringExtra("sender"));
		}
		catch (NullPointerException e) {
			Log.w("BatPhone", "intent is missing 'sender' extra data -- using current identity");
			Identity main = Identity.getMainIdentity();
			if (main == null)
				throw new MessageIntentException(
						"Can't send message, I don't seem to know who I am", e);
			sender = main.subscriberId;
		}
		catch (SubscriberId.InvalidHexException e) {
			throw new MessageIntentException("invalid 'sender' extra data", e);
		}
		try {
			recipient = new SubscriberId(intent.getStringExtra("recipient"));
		}
		catch (NullPointerException e) {
			throw new MessageIntentException("missing 'recipient' extra data");
		}
		catch (SubscriberId.InvalidHexException e) {
			throw new MessageIntentException("invalid 'recipient' extra data", e);
		}
		String text = intent.getStringExtra("text");
		if (text == null)
			throw new MessageIntentException("missing 'text' extra data");
		String timestamp = intent.getStringExtra("timestamp");
		long millis;
		if (timestamp != null) {
			try {
				millis = Long.parseLong(timestamp);
			}
			catch (NumberFormatException e) {
				throw new MessageIntentException("invalid 'timestamp' extra data", e);
			}
		} else {
			Log.w("BatPhone", "intent is missing 'millis' extra data -- using current time");
			millis = System.currentTimeMillis();
		}
		return new SimpleMeshMS(
				sender,
				recipient,
				intent.getStringExtra("senderDid"),
				intent.getStringExtra("recipientDid"),
				millis,
				text
			);
	}

}
