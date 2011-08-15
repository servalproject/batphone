/**
 * Copyright (C) 2011 The Serval Project
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

package org.servalproject.dt;

import org.servalproject.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class Receiver extends BroadcastReceiver {
	@Override
	public void onReceive(final Context context, final Intent intent) {
		Log.i("Receiver", "Intent received " + intent.getAction());
		if (intent.getAction().equals("org.servalproject.DT")) {
			String sender = intent.getExtras().getString("number");
			String content = intent.getExtras().getString("content");
			this.writeSMS(sender, content, context);
		}
	}

	private void writeSMS(final String sender, final String content,
			final Context context) {
		// Write the SMS in the Inbox
		ContentValues values = new ContentValues();
		values.put("address", sender);
		values.put("body", content);
		Uri uriSms = context.getContentResolver().insert(Uri.parse("content://sms/inbox"),
				values);

		// Get the ThreadID of the sms
		Cursor c = context.getContentResolver().query(uriSms,new String[] { "_id", "thread_id" }, null, null, null);
		long threadId;
		if (c != null && c.moveToFirst())
			threadId = c.getLong(1);
		else
			threadId = 0;

		// Display a notification linked with SMSDroid
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) context
		.getSystemService(ns);

		int icon = R.drawable.icon;
		CharSequence tickerText = "New Digital Telegram Message";
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);
		CharSequence contentTitle = "Digital Telegram";
		CharSequence contentText = sender + ": " + content;
		Intent notificationIntent = new Intent("android.intent.action.VIEW");
		notificationIntent.setData(Uri.parse("content://mms-sms/conversations/"
				+ threadId));
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);
		notification.defaults = Notification.DEFAULT_SOUND;
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		mNotificationManager.notify(1, notification);
	}
}
