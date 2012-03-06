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
package org.servalproject.meshms;

import org.servalproject.dna.DataFile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * used to receive MeshMS messages from DNA and, repackage them and send them
 * out to any apps registered to receive the public API
 */
public class OutgoingMeshMS extends BroadcastReceiver {

	// class level constants
	private final String TAG = "OutgoingMeshMS";
	private static final int NOTIFICATION_ID = 999;
	/*
	 * (non-Javadoc)
	 *
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
	 * android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {

		// check to make sure we've received the appropriate intent
		// this is the intent used by the server.c component of Serval DNA
		if (intent.getAction().equals("org.servalproject.DT") == true) {
			// process the message from Serval DNA
			processFromServalDna(context, intent);
		} else if (intent.getAction().equals(
				"org.servalproject.meshms.RECEIVE_MESHMS") == true) {
			processFromRhizome(context, intent);
		} else {
			Log.w(TAG, "unknown intent received: " + intent.getAction());
		}
	}

	private void processFromServalDna(Context context, Intent intent) {

		// process the message
		String mSender = intent.getExtras().getString("number");
		String mContent = intent.getExtras().getString("content");

		// create a new SimpleMeshMS message and populate it
		SimpleMeshMS mMessage = new SimpleMeshMS(mSender, DataFile.getDid(0),
				mContent);

		addToSMSStore(mMessage.getSender(), mMessage.getContent(), context);
	}

	private void processFromRhizome(Context context, Intent intent) {

		SimpleMeshMS mMessage = (SimpleMeshMS) intent
				.getParcelableExtra("simple");

		addToSMSStore(mMessage.getSender(), mMessage.getContent(), context);

	}

	private int countNewMessages(Context context) {
		ContentResolver resolver = context.getContentResolver();
		Cursor cursor = resolver.query(Uri.parse("content://sms"), null,
				"(type=1 and read=0)", null, null);
		if (cursor == null)
			return 0;
		try {
			return cursor.getCount();
		} finally {
			cursor.close();
		}
	}

	private void updateNotification(Context context, String sender,
			String text, long threadId) {
		int count = countNewMessages(context);
		NotificationManager nm = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);

		nm.cancel(NOTIFICATION_ID);

		// note, cloned some of this from the android messaging application
		Notification n = new Notification(android.R.drawable.stat_notify_chat,
				sender + ": " + text, System.currentTimeMillis());
		Intent intent = null;

		if (count > 1) {
			intent = new Intent(Intent.ACTION_MAIN);

			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP
					| Intent.FLAG_ACTIVITY_CLEAR_TOP);

			intent.setType("vnd.android-dir/mms-sms");
			n.number = count;
		} else {
			intent = new Intent("android.intent.action.VIEW");
			intent.setData(Uri
					.parse("content://mms-sms/conversations/" + threadId));
		}

		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
				intent, PendingIntent.FLAG_UPDATE_CURRENT);

		n.setLatestEventInfo(context, sender, text, pendingIntent);
		n.defaults |= Notification.DEFAULT_VIBRATE
				| Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND;
		n.flags |= Notification.FLAG_SHOW_LIGHTS
				| Notification.FLAG_AUTO_CANCEL;

		nm.notify(NOTIFICATION_ID, n);
	}

	private void addToSMSStore(String sender, String content, Context context) {

		// TODO have some way to suppress what messages end up in the datastore
		ContentValues values = new ContentValues();
		values.put("address", sender);
		values.put("body", content);
		// values.put("date", value);
		Uri newRecord = context.getContentResolver().insert(
				Uri.parse("content://sms/inbox"),
				values);

		Cursor c = context.getContentResolver().query(newRecord,
				new String[] { "_id", "thread_id" }, null, null, null);
		long threadId = 0;
		if (c != null && c.moveToFirst())
			threadId = c.getLong(1);

		updateNotification(context, sender, content, threadId);
	}
}
