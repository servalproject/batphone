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

import java.util.List;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.messages.MessageUtils;
import org.servalproject.messages.MessagesListActivity;
import org.servalproject.messages.ShowConversationActivity;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.SubscriberId;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Invoked by Rhizome whenever it has received a batch of incoming MeshMS messages.
 * Responsible for announcing the messages by sending out the proper intents.
 */
public class IncomingMeshMS extends BroadcastReceiver {

	private static final String TAG = "IncomingMeshMS";
	private static final int NOTIFICATION_ID = 999;

	/* @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
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

	public static final String NEW_MESSAGES = "org.servalproject.meshms.NEW";
	// add new incoming messages
	public static void addMessages(List<SimpleMeshMS> messages) {
		if (messages.size() == 0)
			return;

		ContentResolver resolver = ServalBatPhoneApplication.context
				.getContentResolver();
		int threadId = -1;
		SimpleMeshMS lastMsg = null;

		for (int i = 0; i < messages.size(); i++) {
			lastMsg = messages.get(i);
			int ret[] = MessageUtils.saveReceivedMessage(lastMsg, resolver);
			threadId = ret[0];
		}

		// make sure we always beep on incoming messages
		updateNotification(ServalBatPhoneApplication.context, lastMsg, threadId);

		ServalBatPhoneApplication.context
				.sendBroadcast(new Intent(NEW_MESSAGES));

	}

	// build an initial notification on startup
	public static void initialiseNotification(final Context context) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (ServalD.isRhizomeEnabled()) {
					try {
						Rhizome.readMessageLogs();
					} catch (Exception e) {
						Log.e(TAG, e.getMessage(), e);
					}
				}
				updateNotification(context, null, -1);
			}

		}, "IncomingMessages").start();
	}

	// update notification after messages have been received
	private static void updateNotification(Context context,
			SimpleMeshMS message, int threadId) {

		int count = MessageUtils.countUnseenMessages(context
				.getContentResolver());
		NotificationManager nm = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);

		nm.cancel(NOTIFICATION_ID);

		if (count == 0)
			return;

		String senderTxt = null;
		String content = null;
		SubscriberId otherSid = null;
		if (message == null) {
			content = "Unread message(s)";
		} else {
			otherSid = message.sender;
			Peer sender = PeerListService.getPeer(context.getContentResolver(),
					otherSid);
			if (message.senderDid != null && !sender.hasName())
				senderTxt = message.senderDid;
			else
				senderTxt = sender.toString();
			content = message.content;
		}

		// note, cloned some of this from the android messaging application
		Notification n = new Notification(R.drawable.ic_serval_logo,
				(senderTxt == null ? "" : senderTxt + ": ") + content,
				System.currentTimeMillis());
		Intent intent = null;

		if (count > 1 || message == null) {
			intent = new Intent(Intent.ACTION_MAIN);

			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP
					| Intent.FLAG_ACTIVITY_CLEAR_TOP);

			intent.setClass(context, MessagesListActivity.class);
		} else {
			intent = new Intent(context, ShowConversationActivity.class);
			intent.putExtra("threadId", threadId);
			if (otherSid != null)
				intent.putExtra("recipient", otherSid.toString());
		}

		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
				intent, PendingIntent.FLAG_UPDATE_CURRENT);

		n.setLatestEventInfo(context,
				senderTxt, content, pendingIntent);
		n.defaults |= Notification.DEFAULT_VIBRATE
				| Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND;
		n.flags |= Notification.FLAG_SHOW_LIGHTS
				| Notification.FLAG_AUTO_CANCEL;
		n.number = count;
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
