/*
 * Copyright (C) 2010-2011 Felix Bechstein
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

import org.servalproject.R;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.widget.Toast;

/**
 * {@link Service} run by the connectors BroadcastReceiver.
 * 
 * @author flx
 */
public final class ConnectorService extends IntentService {
	/** Tag for output. */
	private static final String TAG = "IO";

	/** {@link NotificationManager}. */
	private NotificationManager mNM = null;

	/** Notification ID of this Service. */
	public static final int NOTIFICATION_PENDING = 0;

	/** Pending tasks. */
	private final ArrayList<Intent> pendingIOOps = new ArrayList<Intent>();

	/** {@link WakeLock} for forcing IO done before sleep. */
	private WakeLock wakelock = null;

	/** {@link Handler}. */
	private Handler handler = null;

	/**
	 * Default constructor.
	 */
	public ConnectorService() {
		super("WebSMS.Connector");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		this.handler = new Handler() {
			@Override
			public void handleMessage(final Message msg) {
				Log.i(TAG, "In handleMessage...");
			}
		};
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onStart(final Intent intent, final int startId) {
		super.onStart(intent, startId);
		Log.d(TAG, "onStart()");
		if (intent != null) {
			this.register(intent);
		}
	}

	/**
	 * Show {@link Toast} on main thread.
	 * 
	 * @param text
	 *            text
	 */
	void showToast(final String text) {
		Log.d(TAG, "showToast(" + text + ")");
		this.handler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(ConnectorService.this, text, Toast.LENGTH_LONG)
						.show();
			}
		});
	}

	/**
	 * Build IO {@link Notification}.
	 * 
	 * @param command
	 *            {@link ConnectorCommand}
	 * @return {@link Notification}
	 */
	public static Notification getNotification(final Context context,
			final ConnectorCommand command) {
		final String t = context.getString(R.string.stat_notify_sms_pending);
		final String te = context.getString(R.string.stat_notify_sending) + " "
				+ Utils.joinRecipients(command.getRecipients(), ", ");
		final String tt = command.getText();

		final Notification notification = new Notification(
				R.drawable.stat_notify_sms_pending, t,
				System.currentTimeMillis());
		final PendingIntent contentIntent = PendingIntent.getActivity(context,
				0, null, 0);
		notification.setLatestEventInfo(context, te, tt, contentIntent);
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
	private void register(final Intent intent) {
		Log.i(TAG, "register(" + intent.getAction() + ")");
		synchronized (this.pendingIOOps) {
			if (this.wakelock == null) {
				final PowerManager pm = (PowerManager) this
						.getSystemService(Context.POWER_SERVICE);
				this.wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
						TAG);
				this.wakelock.acquire();
			}
			final ConnectorCommand c = new ConnectorCommand(intent);
			if (c.getType() == ConnectorCommand.TYPE_SEND) {
				if (this.mNM == null) {
					this.mNM = (NotificationManager) this
							.getSystemService(NOTIFICATION_SERVICE);
				}
				try {
					final Notification notification = getNotification(this, c);
					this.mNM.notify(NOTIFICATION_PENDING, notification);
				} catch (IllegalArgumentException e) {
					Log.e(TAG, "illegal argument", e);
				}
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
	private void unregister(final Intent intent) {
		Log.i(TAG, "unregister(" + intent.getAction() + ")");
		synchronized (this.pendingIOOps) {
			Log.d(TAG, "currentIOOps=" + this.pendingIOOps.size());
			final int l = this.pendingIOOps.size();
			if (l == 1) {
				this.pendingIOOps.clear();
			} else {
				Intent oi;
				for (int i = 0; i < l; i++) {
					oi = this.pendingIOOps.get(i);
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
				if (this.mNM != null) {
					this.mNM.cancel(NOTIFICATION_PENDING);
				}
				if (this.wakelock != null && this.wakelock.isHeld()) {
					this.wakelock.release();
				}
				// stop unneeded service
				// this.stopSelf();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy()");
		Log.i(TAG, "currentIOOps=" + this.pendingIOOps.size());
		final int s = this.pendingIOOps.size();
		ConnectorCommand cc;
		ConnectorSpec cs;
		Intent in;
		for (int i = 0; i < s; i++) {
			cc = new ConnectorCommand(this.pendingIOOps.get(i));
			cs = new ConnectorSpec(// .
					this.pendingIOOps.get(i));
			if (cc.getType() == ConnectorCommand.TYPE_SEND) {
				cs.setErrorMessage("error while IO");
				in = cs.setToIntent(null);
				cc.setToIntent(in);
				this.sendBroadcast(in);
				in = null;
			} else {
				// Toast.makeText(this, cs.getName() + ": error while IO",
				// Toast.LENGTH_LONG);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onHandleIntent(final Intent intent) {
		Log.d(TAG, "onHandleIntent()");
		if (intent == null) {
			return;
		}
		final String a = intent.getAction();
		Log.d(TAG, "action: " + a);
		final String pkg = this.getPackageName();
		if (a != null && (// .
				a.equals(pkg + Connector.ACTION_RUN_BOOTSTRAP) || // .
						a.equals(pkg + Connector.ACTION_RUN_UPDATE) || // .
				a.equals(pkg + Connector.ACTION_RUN_SEND))) {
			// register intent, if service gets killed, all pending intents
			// get send to websms
			// this.register(intent);

			try {
				final ConnectorSpec connector = new ConnectorSpec(intent);
				final ConnectorCommand command = new ConnectorCommand(intent);
				final Connector receiver = Connector.getInstance();

				this.doInBackground(intent, connector, command, receiver);
				this.onPostExecute(connector, command, receiver);
			} catch (WebSMSException e) {
				Log.e(TAG, "error starting service", e);
				// Toast.makeText(this, e.getMessage(),
				// Toast.LENGTH_LONG).show();
			}
		}
		this.unregister(intent);
	}

	/**
	 * Do the work in background.
	 * 
	 * @param intent
	 *            {@link Intent}
	 * @param connector
	 *            {@link ConnectorSpec}
	 * @param command
	 *            {@link ConnectorCommand}
	 * @param receiver
	 *            {@link Connector}
	 */
	private void doInBackground(final Intent intent,
			final ConnectorSpec connector, final ConnectorCommand command,
			final Connector receiver) {
		try {
			switch (command.getType()) {
			case ConnectorCommand.TYPE_BOOTSTRAP:
				receiver.doBootstrap(this, intent);
				break;
			case ConnectorCommand.TYPE_UPDATE:
				receiver.doUpdate(this, intent);
				break;
			case ConnectorCommand.TYPE_SEND:
				String t = command.getText();
				String[] r = command.getRecipients();
				if (t == null || t.length() == 0 || // .
						r == null || r.length == 0) {
					break;
				}
				t = null;
				r = null;
				receiver.doSend(this, intent);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			if (e instanceof WebSMSException) {
				Log.d(TAG, connector.getPackage() + ": error in AsyncTask", e);
			} else {
				Log.e(TAG, connector.getPackage() + ": error in AsyncTask", e);
			}
			// put error message to ConnectorSpec
			connector.setErrorMessage(this, e);
		}
	}

	/**
	 * Do post processing.
	 * 
	 * @param connector
	 *            {@link ConnectorSpec}
	 * @param command
	 *            {@link ConnectorCommand}
	 * @param receiver
	 *            {@link Connector}
	 */
	private void onPostExecute(final ConnectorSpec connector,
			final ConnectorCommand command, final Connector receiver) {
		// final String e = connector.getErrorMessage();
		// if (e != null) {
		// Toast.makeText(this, e, Toast.LENGTH_LONG).show();
		// }
		connector.update(receiver.getSpec(this));
		final Intent i = connector.setToIntent(null);
		command.setToIntent(i);
		Log.d(TAG, connector.getPackage() + ": send broadcast info");
		this.sendBroadcast(i);
		// this.unregister(i);
	}
}
