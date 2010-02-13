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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

	/** Method Signature: startForeground. */
	@SuppressWarnings("unchecked")
	private static final Class[] START_FOREGROUND_SIGNATURE = new Class[] {
			int.class, Notification.class };
	/** Method Signature: stopForeground. */
	@SuppressWarnings("unchecked")
	private static final Class[] STOP_FOREGROUND_SIGNATURE = // .
	new Class[] { boolean.class };

	/** {@link NotificationManager}. */
	private NotificationManager mNM;
	/** Method: startForeground. */
	private Method mStartForeground;
	/** Method: stopForeground. */
	private Method mStopForeground;
	/** Method's arguments: startForeground. */
	private Object[] mStartForegroundArgs = new Object[2];
	/** Method's arguments: stopForeground. */
	private Object[] mStopForegroundArgs = new Object[1];

	/** Notification text. */
	private static final String NOTIFICATION_TEXT = "WebSMS: Connector IO";
	/** Notification text, sending. */
	private static final String NOTIFICATION_TEXT_SENDING = "WebSMS: sending";
	/** Notification text, extra. */
	private static final String NOTIFICATION_TEXT_EXTRA = "Sending:";
	/** Notification text. */
	private static int notificationText = 0;
	/** Notification text, sending. */
	private static int notificationTextSending = 0;
	/** Notification text, extra. */
	private static int notificationTextExtra = 0;
	/** Notification icon. */
	private static int notificationIcon = 0;

	/** Notification ID of this Service. */
	private static final int NOTIFICATION_PENDING = 0;

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
		this.mNM = (NotificationManager) this
				.getSystemService(NOTIFICATION_SERVICE);
		try {
			this.mStartForeground = this.getClass().getMethod(
					"startForeground", START_FOREGROUND_SIGNATURE);
			this.mStopForeground = this.getClass().getMethod("stopForeground",
					STOP_FOREGROUND_SIGNATURE);
		} catch (NoSuchMethodException e) {
			// Running on an older platform.
			this.mStartForeground = null;
			this.mStopForeground = null;
		}
	}

	/**
	 * This is a wrapper around the new startForeground method, using the older
	 * APIs if it is not available.
	 * 
	 * @param id
	 *            {@link Notification} id
	 * @param notification
	 *            {@link Notification}
	 * @param foreNotification
	 *            for display of {@link Notification}
	 */
	private void startForegroundCompat(final int id,
			final Notification notification, final boolean foreNotification) {
		// If we have the new startForeground API, then use it.
		if (this.mStartForeground != null) {
			this.mStartForegroundArgs[0] = Integer.valueOf(id);
			this.mStartForegroundArgs[1] = notification;
			try {
				this.mStartForeground.invoke(this, this.mStartForegroundArgs);
			} catch (InvocationTargetException e) {
				// Should not happen.
				Log.w(TAG, "Unable to invoke startForeground", e);
			} catch (IllegalAccessException e) {
				// Should not happen.
				Log.w(TAG, "Unable to invoke startForeground", e);
			}
		} else {
			// Fall back on the old API.
			this.setForeground(true);
		}

		if (foreNotification) {
			this.mNM.notify(id, notification);
		}
	}

	/**
	 * This is a wrapper around the new stopForeground method, using the older
	 * APIs if it is not available.
	 * 
	 * @param id
	 *            {@link Notification} id
	 */
	private void stopForegroundCompat(final int id) {
		this.mNM.cancel(id);
		// If we have the new stopForeground API, then use it.
		if (this.mStopForeground != null) {
			this.mStopForegroundArgs[0] = Boolean.TRUE;
			try {
				this.mStopForeground.invoke(this, this.mStopForegroundArgs);
			} catch (InvocationTargetException e) {
				// Should not happen.
				Log.w(TAG, "Unable to invoke stopForeground", e);
			} catch (IllegalAccessException e) {
				// Should not happen.
				Log.w(TAG, "Unable to invoke stopForeground", e);
			}
		} else {
			// Fall back on the old API. Note to cancel BEFORE changing the
			// foreground state, since we could be killed at that point.
			// this.mNM.cancel(id);
			this.setForeground(false);
		}
	}

	/**
	 * Build IO {@link Notification}.
	 * 
	 * @param command
	 *            {@link ConnectorCommand}
	 * @return {@link Notification}
	 */
	private Notification getNotification(final ConnectorCommand command) {
		if (notificationIcon == 0) {
			notificationIcon = this.getResources().getIdentifier(
					"stat_notify_sms_pending", "drawable",
					this.getPackageName());
			Log.d(TAG, "resID.icon=" + notificationIcon);
		}
		if (notificationText == 0) {
			notificationText = this.getResources().getIdentifier(
					"stat_notify_IO", "string", this.getPackageName());
			Log.d(TAG, "resID.textIO=" + notificationText);
		}
		if (notificationTextSending == 0) {
			notificationTextSending = this.getResources().getIdentifier(
					"stat_notify_sms_pending", "string", this.getPackageName());
			Log.d(TAG, "resID.textSending=" + notificationTextSending);
		}
		if (notificationTextExtra == 0) {
			notificationTextExtra = this.getResources().getIdentifier(
					"stat_notify_sending", "string", this.getPackageName());
			Log.d(TAG, "resID.textExtra=" + notificationTextExtra);
		}
		String t = NOTIFICATION_TEXT;
		String te = NOTIFICATION_TEXT_EXTRA;
		String tt = "";
		if (command.getType() == ConnectorCommand.TYPE_SEND) {
			if (notificationTextSending > 0) {
				t = this.getString(notificationTextSending);
			} else {
				t = NOTIFICATION_TEXT_SENDING;
				notificationTextSending = -1;
			}
			if (notificationTextExtra > 0) {
				te = this.getString(notificationTextExtra);
			} else {
				notificationTextExtra = -1;
			}
			te += " " + Utils.joinRecipients(command.getRecipients(), ", ");
			tt = command.getText();
		} else {
			if (notificationText > 0) {
				t = this.getString(notificationText);
			} else {
				notificationText = -1;
			}
			te = t;
		}
		final Notification notification = new Notification(notificationIcon, t,
				System.currentTimeMillis());
		final PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				null, 0);
		notification.setLatestEventInfo(this, te, tt, contentIntent);
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
			final ConnectorCommand c = new ConnectorCommand(intent);
			// setForeground / startForeground
			final Notification notification = this.getNotification(c);
			if (c.getType() == ConnectorCommand.TYPE_SEND) {
				this.startForegroundCompat(NOTIFICATION_PENDING, notification,
						true);
			} else {
				this.startForegroundCompat(NOTIFICATION_PENDING, notification,
						false);
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
				this.stopForegroundCompat(NOTIFICATION_PENDING);
				if (this.wakelock != null && this.wakelock.isHeld()) {
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
