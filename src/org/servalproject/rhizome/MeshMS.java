package org.servalproject.rhizome;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.messages.MessagesListActivity;
import org.servalproject.servaldna.AbstractId;
import org.servalproject.servald.Identity;
import org.servalproject.servald.ServalD;
import org.servalproject.servaldna.ServalDFailureException;
import org.servalproject.servaldna.SubscriberId;

public class MeshMS {
	private final ServalBatPhoneApplication app;
	private final Identity identity;
	private static final String TAG="MeshMS";
	public static final String NEW_MESSAGES="org.servalproject.meshms.NEW";

	public MeshMS(ServalBatPhoneApplication app, Identity identity){
		this.app=app;
		this.identity=identity;
	}

	public void bundleArrived(RhizomeManifest_MeshMS meshms){
		initialiseNotification();
		app.sendBroadcast(new Intent(NEW_MESSAGES));
	}

	public void markRead(SubscriberId recipient){
		try {
			ServalD.readMessage(identity.subscriberId, recipient);
		} catch (ServalDFailureException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		cancelNotification();
		initialiseNotification();
	}

	private int lastMessageHash =0;

	// build an initial notification on startup
	public void initialiseNotification() {
		SubscriberId recipient=null;
		boolean unread=false;
		int messageHash=0;
		try {
			Cursor c = ServalD.listConversations(identity.subscriberId);
			try{
				int readCol = c.getColumnIndexOrThrow("read");
				int recipientCol = c.getColumnIndexOrThrow("recipient");
				int lastMessageCol = c.getColumnIndexOrThrow("last_message");
				while(c.moveToNext()){
					try {
						SubscriberId sid = new SubscriberId(c.getBlob(recipientCol));
						String unreadValue = c.getString(readCol);
						long lastMessage = c.getLong(lastMessageCol);
						// detect when the number of incoming messages has changed
						if (lastMessage>0)
							messageHash = (messageHash<<25) ^ (messageHash>>>7) ^ sid.hashCode() ^
									(int)((lastMessage&0xFFFFFFFF) ^ ((lastMessage>>32)&0xFFFFFFFF));
						if ("unread".equals(unreadValue)){
							// remember the recipient, if it is the only recipient with unread messages
							if (unread){
								recipient=null;
							}else{
								recipient=sid;
							}
							unread=true;
						}
					} catch (AbstractId.InvalidBinaryException e) {
						Log.e(TAG, e.getMessage(), e);
					}
				}
			}finally{
				c.close();
			}
		} catch (ServalDFailureException e) {
			Log.e(TAG, e.getMessage(), e);
		}

		if (!unread){
			cancelNotification();
			return;
		}
		if (messageHash == lastMessageHash)
			return;
		lastMessageHash = messageHash;
		// For now, just indicate that there are some unread messages
		String content = "Unread message(s)";
		Intent intent = new Intent(Intent.ACTION_MAIN);

		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_SINGLE_TOP
				| Intent.FLAG_ACTIVITY_CLEAR_TOP);

		intent.setClass(app, MessagesListActivity.class);

		PendingIntent pendingIntent = PendingIntent.getActivity(app, 0,
				intent, PendingIntent.FLAG_UPDATE_CURRENT);

		Notification n = new Notification(R.drawable.ic_serval_logo,
				content,
				System.currentTimeMillis());

		n.setLatestEventInfo(app,
				"", content, pendingIntent);
		n.defaults |= Notification.DEFAULT_VIBRATE
				| Notification.DEFAULT_LIGHTS;

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(app);
		String sound = settings.getString("meshms_notification_sound",
				null);
		if (sound==null)
		if (sound == null) {
			n.defaults |= Notification.DEFAULT_SOUND;
		} else {
			n.sound = Uri.parse(sound);
		}

		n.flags |= Notification.FLAG_SHOW_LIGHTS
				| Notification.FLAG_AUTO_CANCEL;

		NotificationManager nm = (NotificationManager) app
				.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify("meshms",0, n);
	}

	public void cancelNotification() {
		NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel("meshms",0);
	}
}
