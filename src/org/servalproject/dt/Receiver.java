package org.servalproject.dt;

import org.servalproject.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class Receiver extends BroadcastReceiver {	
	@Override
	public void onReceive(Context context, Intent intent) {
		if ((intent.getAction().equals("android.intent.action.PICK"))&(intent.getType().equals("vnd.servalproject.DTSMS/vnd.servalproject.DTSMS-text"))) {
			String sender = intent.getExtras().getString("number");
			String content = intent.getExtras().getString("content");
			writeSMS(sender, content, context);
		}
	}
	
	private void writeSMS(String sender, String content, Context context)
	{
		// Write the SMS in the Inbox
        ContentValues values = new ContentValues();
        values.put("address", sender);	
        values.put("body", content);
        context.getContentResolver().insert(Uri.parse("content://sms/inbox"), values);
        
   // Display a notification linked with SMSDroid
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(ns);
        
        int icon = R.drawable.icon;
        CharSequence tickerText = "Digital Telegram - New message";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
       // Context context = getApplicationContext();
        CharSequence contentTitle = "Digital Telegram";
        CharSequence contentText = "You have a new message";
        Intent notificationIntent = new Intent("android.intent.action.MAIN");
        // Lauch Messaging when the user clicks on the notification
        notificationIntent.setComponent(new ComponentName("com.android.mms","com.android.mms.ui.ConversationList"));
        // Lauch SmsDroid when the user clicks on the notification
        //notificationIntent.setComponent(new ComponentName("de.ub0r.android.smsdroid","de.ub0r.android.smsdroid.ConversationList"));
        //PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        PendingIntent contentIntent = PendingIntent.getBroadcast(context, 0, notificationIntent, 0);
        
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        mNotificationManager.notify(1, notification);
	}
}
