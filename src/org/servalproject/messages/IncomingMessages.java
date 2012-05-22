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
import org.servalproject.servald.SubscriberId;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
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
	private static final int NOTIFICATION_ID = 999;

	public static final String INTENT_SENDER_SID = "senderSid";
	public static final String INTENT_SENDER_DID = "senderDid";
	public static final String INTENT_RECIPIENT_SID = "recipientSid";
	public static final String INTENT_RECIPIENT_DID = "recipientDid";
	public static final String INTENT_CONTENT = "content";

	@Override
	public void onReceive(Context context, Intent intent) {

		// check to make sure we've received the appropriate intent
		if (intent.getAction().equals(
				"org.servalproject.meshms.RECEIVE_MESHMS") == true) {
			processReceivedMessage(context, intent);
		} else {
			Log.w(TAG, "unknown intent received: " + intent.getAction());
		}
	}

	private void processReceivedMessage(Context context, Intent intent) {
		try {
			addToMessageStore(MessageUtils.getSimpleMessageFromIntent(intent), context);
		}
		catch (MessageUtils.MessageIntentException e) {
			Log.e(TAG, "cannot process message intent", e);
			// TODO - we should handle this error gracefully somehow
		}
	}

	private int countNewMessages(Context context) {

		// TODO - find a way to see how many messages have been received
		return 1;

		// ContentResolver resolver = context.getContentResolver();
		// Cursor cursor = resolver.query(Uri.parse("content://sms"), null,
		// "(type=1 and read=0)", null, null);
		// if (cursor == null)
		// return 0;
		// try {
		// return cursor.getCount();
		// } finally {
		// cursor.close();
		// }
	}

	private void updateNotification(Context context, SimpleMeshMS message, long threadId) {
		int count = countNewMessages(context);
		NotificationManager nm = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);

		nm.cancel(NOTIFICATION_ID);

		// note, cloned some of this from the android messaging application
		Notification n = new Notification(R.drawable.ic_serval_logo,
				message.sender + ": " + message.content,
				System.currentTimeMillis());
		Intent intent = null;

		if (count > 1) {
			intent = new Intent(Intent.ACTION_MAIN);

			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP
					| Intent.FLAG_ACTIVITY_CLEAR_TOP);

			intent.setClass(context, MessagesListActivity.class);
		} else {
			intent = new Intent(context, ShowConversationActivity.class);
			intent.putExtra("threadId", threadId);
		}

		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
				intent, PendingIntent.FLAG_UPDATE_CURRENT);

		n.setLatestEventInfo(context, message.sender.toString(), message.content, pendingIntent);
		n.defaults |= Notification.DEFAULT_VIBRATE
				| Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND;
		n.flags |= Notification.FLAG_SHOW_LIGHTS
				| Notification.FLAG_AUTO_CANCEL;

		nm.notify(NOTIFICATION_ID, n);
	}

	private void addToMessageStore(SimpleMeshMS message, Context context) {
		ContentResolver contentResolver = context.getContentResolver();

		// save the message
		int[] result = MessageUtils.saveReceivedMessage(message, contentResolver);

		int threadId = result[0];
		int messageId = result[1];

		if (messageId != -1) {
			Log.i(TAG, "New message saved with messageId '" + messageId
					+ "', threadId '" + threadId + "'");
			updateNotification(context, message, threadId);
		} else {
			Log.e(TAG, "unable to save new message");
		}

	}
}
