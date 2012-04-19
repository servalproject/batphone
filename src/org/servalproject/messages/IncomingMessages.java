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
	private final boolean V_LOG = true;

	private final int STATUS_NOTIFICATION = 0;

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

		int mThreadId = MessageUtils.getThreadId(mSimpleMessage,
				mContentResolver);

		if (V_LOG) {
			Log.v(TAG, "Thread ID: " + mThreadId);
		}

		if (mThreadId != -1) {
			int mMessageId = MessageUtils.saveReceivedMessage(
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

		addNotification(context);
	}

	private void addNotification(Context context) {

		// add a notification icon
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);

		// TODO update this with a better icon
		// TODO update this with a custom notification with stats
		int mNotificationIcon = android.R.drawable.stat_notify_chat;
		CharSequence mTickerText = context
				.getString(R.string.new_message_notification_ticker_text);
		long mWhen = System.currentTimeMillis();

		// create the notification and set the flag so that it stays up
		Notification mNotification = new Notification(mNotificationIcon,
				mTickerText, mWhen);
		mNotification.flags |= Notification.FLAG_AUTO_CANCEL;

		// get the content of the notification
		CharSequence mNotificationTitle = context
				.getString(R.string.new_message_notification_title);
		CharSequence mNotificationContent = context
				.getString(R.string.new_message_notification_message);

		// create the intent for the notification
		// set flags so that the user returns to this activity and not a new one
		Intent mNotificationIntent = new Intent(context,
				org.servalproject.messages.MessagesListActivity.class);
		mNotificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
				| Intent.FLAG_ACTIVITY_SINGLE_TOP);

		// create a pending intent so that the system can use the above intent
		// at a later time.
		PendingIntent mPendingIntent = PendingIntent.getActivity(context, 0,
				mNotificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

		// complete the setup of the notification
		mNotification.setLatestEventInfo(context.getApplicationContext(),
				mNotificationTitle, mNotificationContent, mPendingIntent);

		// add the notification
		mNotificationManager.notify(STATUS_NOTIFICATION, mNotification);
	}
}
