/*
 * Copyright (C) 2010 Felix Bechstein
 * 
 * This file is part of WebSMS.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.android.websms.connector.common;

import java.util.ArrayList;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.widget.Toast;

/**
 * {@link Service} run by the connectors BroadcastReceiver.
 * 
 * @author flx
 */
public final class ConnectorService extends Service {
	/** Tag for output. */
	private static final String TAG = "WebSMS.IO";

	/** Notification text. */
	private static final String NOTIFICATION_TEXT = "WebSMS Connector IO";
	/** Notification text. */
	private static int notificationText = 0;
	/** Notification icon. */
	private static int notificationIcon = 0;

	/** Notification ID of this Service. */
	private static final int NOTIFICATION_PENDING = 0;

	/** Wrapper for API5 commands. */
	private HelperAPI5Service helperAPI5s = null;

	/** Pending tasks. */
	private final ArrayList<Intent> pendingIOOps = new ArrayList<Intent>();

	/** {@link WakeLock} for forcing IO done before sleep. */
	private WakeLock wakelock = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IBinder onBind(final Intent intent) {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		try {
			this.helperAPI5s = new HelperAPI5Service();
			if (!this.helperAPI5s.isAvailable()) {
				this.helperAPI5s = null;
			}
		} catch (VerifyError e) {
			this.helperAPI5s = null;
			Log.d(TAG, "no api5 currentIOOps", e);
		}
	}

	/**
	 * Build IO {@link Notification}.
	 * 
	 * @return {@link Notification}
	 */
	private Notification getNotification() {
		if (notificationIcon == 0) {
			notificationIcon = this.getResources().getIdentifier(
					"stat_notify_sms_pending", "drawable",
					this.getPackageName());
			Log.d(TAG, "resID.icon=" + notificationIcon);
		}
		if (notificationText == 0) {
			notificationText = this.getResources().getIdentifier(
					"stat_notify_sms_pending", "string", this.getPackageName());
			Log.d(TAG, "resID.text=" + notificationText);
		}
		String t = NOTIFICATION_TEXT;
		if (notificationText > 0) {
			t = this.getString(notificationText);
		} else {
			notificationText = -1;
		}
		final Notification notification = new Notification(notificationIcon,
				NOTIFICATION_TEXT, System.currentTimeMillis());
		final PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				null, 0);
		notification.setLatestEventInfo(this, t, "", contentIntent);
		notification.defaults |= Notification.FLAG_NO_CLEAR
				| Notification.FLAG_ONGOING_EVENT;
		notification.defaults &= Notification.DEFAULT_ALL
				^ Notification.DEFAULT_LIGHTS ^ Notification.DEFAULT_SOUND
				^ Notification.DEFAULT_VIBRATE;
		Log.d(TAG, "defaults: " + notification.defaults);
		return notification;
	}

	/**
	 * Register a IO task.
	 * 
	 * @param intent
	 *            intent holding IO operation
	 */
	public void register(final Intent intent) {
		Log.d(TAG, "register(" + intent.getAction() + ")");
		synchronized (this.pendingIOOps) {
			// setForeground / startForeground
			Notification notification = null;
			if (this.helperAPI5s == null) {
				this.setForeground(true);
			} else {
				notification = this.getNotification();
				this.helperAPI5s.startForeground(this, NOTIFICATION_PENDING,
						notification);
			}
			if (new ConnectorCommand(intent).getType() == // .
			ConnectorCommand.TYPE_SEND) {
				if (notification == null) {
					notification = this.getNotification();
				}
				final NotificationManager mNotificationMgr = // .
				(NotificationManager) this
						.getSystemService(Context.NOTIFICATION_SERVICE);
				mNotificationMgr.notify(NOTIFICATION_PENDING, notification);
			}
			if (this.wakelock == null) {
				final PowerManager pm = (PowerManager) this
						.getSystemService(Context.POWER_SERVICE);
				this.wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
						TAG);
				this.wakelock.acquire();
			}
			Log.d(TAG, "currentIOOps=" + this.pendingIOOps.size());
			this.pendingIOOps.add(intent);
			Log.d(TAG, "currentIOOps=" + this.pendingIOOps.size());
		}
	}

	/**
	 * Unregister a IO task.
	 * 
	 * @param intent
	 *            intent holding IO operation
	 */
	public void unregister(final Intent intent) {
		Log.d(TAG, "unregister(" + intent.getAction() + ")");
		synchronized (this.pendingIOOps) {
			Log.d(TAG, "currentIOOps=" + this.pendingIOOps.size());
			final int l = this.pendingIOOps.size();
			if (l == 1) {
				this.pendingIOOps.clear();
			} else {
				for (int i = 0; i < l; i++) {
					final Intent oi = this.pendingIOOps.get(i);
					if (ConnectorSpec.equals(intent, oi)
							&& ConnectorCommand.equals(intent, oi)) {
						this.pendingIOOps.remove(i);
						break;
					}
				}
			}
			Log.d(TAG, "currentIOOps=" + this.pendingIOOps.size());
			if (this.pendingIOOps.size() == 0) {
				// set service to background
				if (this.helperAPI5s == null) {
					this.setForeground(false);
				} else {
					this.helperAPI5s.stopForeground(this, true);
				}
				final NotificationManager mNotificationMgr = // .
				(NotificationManager) this
						.getSystemService(Context.NOTIFICATION_SERVICE);
				mNotificationMgr.cancelAll();
				if (this.wakelock != null) {
					this.wakelock.release();
				}
				// stop unneeded service
				this.stopSelf();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onStart(final Intent intent, final int startId) {
		if (intent != null) {
			final String a = intent.getAction();
			Log.d("WebSMS.service", "action: " + a);
			final String pkg = this.getPackageName();
			if (a != null && (// .
					a.equals(pkg + Connector.ACTION_RUN_BOOTSTRAP) || // .
					a.equals(pkg + Connector.ACTION_RUN_UPDATE) || // .
					a.equals(pkg + Connector.ACTION_RUN_SEND))) {
				// register intent, if service gets killed, all pending intents
				// get send to websms
				this.register(intent);
				try {
					new ConnectorTask(intent, Connector.getInstance(), this)
							.execute((Void[]) null);
				} catch (WebSMSException e) {
					Log.e(TAG, "error starting service", e);
					Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG)
							.show();
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy()");
		Log.d(TAG, "currentIOOps=" + this.pendingIOOps.size());
		final int s = this.pendingIOOps.size();
		for (int i = 0; i < s; i++) {
			final ConnectorCommand cc = new ConnectorCommand(this.pendingIOOps
					.get(i));
			final ConnectorSpec cs = new ConnectorSpec(// .
					this.pendingIOOps.get(i));
			if (cc.getType() == ConnectorCommand.TYPE_SEND) {
				cs.setErrorMessage("error while IO");
				final Intent in = cs.setToIntent(null);
				cc.setToIntent(in);
				this.sendBroadcast(in);
			} else {
				Toast.makeText(this, cs.getName() + ": error while IO",
						Toast.LENGTH_LONG);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int onStartCommand(final Intent intent, final int flags,
			final int startId) {
		this.onStart(intent, startId);
		return START_NOT_STICKY;
	}
}
