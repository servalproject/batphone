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

import java.io.IOException;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Receives commands coming as broadcast from WebSMS.
 * 
 * @author flx
 */
public abstract class Connector extends BroadcastReceiver {
	/** Common Action prefix. */
	private static final String ACTION_PREFIX = "de.ub0r."
			+ "android.websms.connector.";

	/** API version. */
	public static final int API_VERSION = 2;

	/**
	 * Action to start a connector's Preference Activity.
	 */
	public static final String ACTION_PREFS = ".PREFS";

	/**
	 * Action to start a connector's {@link android.app.Service}. This should
	 * include a {@link ConnectorCommand}: bootstrap.
	 */
	public static final String ACTION_RUN_BOOTSTRAP = ".RUN_BOOTSTRAP";

	/**
	 * Action to start a connector's {@link android.app.Service}. This should
	 * include a {@link ConnectorCommand}: update.
	 */
	public static final String ACTION_RUN_UPDATE = ".RUN_UPDATE";

	/**
	 * Action to start a connector's {@link android.app.Service}. This should
	 * include a {@link ConnectorCommand}: send.
	 */
	public static final String ACTION_RUN_SEND = ".RUN_SEND";

	/** Broadcast Action requesting update of {@link ConnectorSpec}'s status. */
	public static final String ACTION_CONNECTOR_UPDATE = ACTION_PREFIX
			+ "UPDATE";

	/**
	 * Broadcast Action sending updated {@link ConnectorSpec} informations back
	 * to WebSMS. This should include a {@link ConnectorSpec}.
	 */
	public static final String ACTION_INFO = ACTION_PREFIX + "INFO";

	/**
	 * Broadcast Action requesting to solve a captcha. Send
	 * EXTRA_CAPTCHA_DRAWABLE and your {@link ConnectorSpec} with it.
	 */
	public static final String ACTION_CAPTCHA_REQUEST = ACTION_PREFIX
			+ "CAPTCHA_REQUEST";
	/**
	 * Broadcast Action sending solved captcha back. The solved captcha will
	 * come as EXTRA_CAPTCHA_SOLVED. If no Extra is given back, the user aborted
	 * the activity.
	 */
	public static final String ACTION_CAPTCHA_SOLVED = ".CAPTCHA_SOLVED";

	/** Extra holding captcha as Drawable saved as Parcelable. */
	public static final String EXTRA_CAPTCHA_DRAWABLE = "captcha";
	/** Extra holding a message displayed to the user. */
	public static final String EXTRA_CAPTCHA_MESSAGE = "text";
	/** Extra holding the solved catpcha. */
	public static final String EXTRA_CAPTCHA_SOLVED = "solved";

	/** Internal {@link ConnectorSpec}. */
	private static ConnectorSpec connector = null;

	/** Sync access to connector. */
	private static final Object SYNC_UPDATE = new Object();

	/**
	 * This instance is ran by {@link ConnectorService} via
	 * {@link ConnectorTask}.Each implementer of this class should register
	 * here.
	 */
	private static Connector instance = null;

	/**
	 * @return single instance running all the IO in different thread.
	 */
	protected static final Connector getInstance() {
		if (instance == null) {
			throw new WebSMSException("no running Connector available");
		}
		return instance;
	}

	/**
	 * Register a {@link Connector} which should be ran to do all the IO in
	 * different thread.
	 * 
	 * @param receiver
	 *            {@link Connector}
	 */
	protected static final void registerInstance(final Connector receiver) {
		instance = receiver;
	}

	/**
	 * Initialize {@link ConnectorSpec}. This is only run once. Changing
	 * properties should be set in updateSpec(). Implement this method to
	 * register subconnectors and set up the connector. UpdateSpec() is called
	 * later. There is no need to duplicate code. Default implementation does
	 * nothing at all.
	 * 
	 * @param context
	 *            {@link Context}
	 * @return updated {@link ConnectorSpec}
	 */
	protected ConnectorSpec initSpec(final Context context) {
		return new ConnectorSpec("noname");
	}

	/**
	 * Update {@link ConnectorSpec}. Implement this method to return the
	 * connectors status etc. Default implementation does nothing at all.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param connectorSpec
	 *            {@link ConnectorSpec}
	 * @return updated {@link ConnectorSpec}
	 */
	protected ConnectorSpec updateSpec(final Context context,
			final ConnectorSpec connectorSpec) {
		return connectorSpec;
	}

	/**
	 * Get {@link ConnectorSpec}. Initialize and update it if needed.
	 * 
	 * @param context
	 *            {@link Context}
	 * @return {@link ConnectorSpec}
	 */
	protected final synchronized ConnectorSpec getSpec(final Context context) {
		synchronized (SYNC_UPDATE) {
			if (connector == null) {
				connector = this.initSpec(context);
				connector.setPackage(context.getPackageName());
				connector.setAPIVersion(API_VERSION);
			}
			return this.updateSpec(context, connector);
		}
	}

	/**
	 * Send INFO Broadcast back to WebSMS. Call this method after updating your
	 * status, changing balance and after processing a command.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param specs
	 *            {@link ConnectorSpec}; if null, getSpec() is called to get
	 *            them
	 * @param command
	 *            send back the {@link ConnectorCommand} which was done
	 */
	protected final void sendInfo(final Context context,
			final ConnectorSpec specs, final ConnectorCommand command) {
		ConnectorSpec c = specs;
		if (c == null) {
			c = this.getSpec(context);
		}
		final Intent i = c.setToIntent(null);
		if (command != null) {
			command.setToIntent(i);
		}
		Log.d(this.getSpec(context).toString(), "-> bc: " + i.getAction());
		context.sendBroadcast(i);
	}

	/**
	 * This default implementation will register the running {@link Connector}
	 * to an external service. This {@link ConnectorService} will run a
	 * {@link ConnectorTask} running the methods doBootstrap(), doUpdate() and
	 * doSend() implemented above.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param intent
	 *            {@link Intent}
	 */
	@Override
	public void onReceive(final Context context, final Intent intent) {
		final ConnectorSpec specs = this.getSpec(context);
		final String tag = specs.toString();
		final String action = intent.getAction();
		Log.d(tag, "action: " + action);
		final String pkg = context.getPackageName();
		if (action == null) {
			return;
		}
		if (Connector.ACTION_CONNECTOR_UPDATE.equals(action)) {
			Log.d(tag, "got info request");
			this.sendInfo(context, null, null);
			// try {
			// this.setResultCode(Activity.RESULT_OK);
			// } catch (Exception e) {
			// Log.d(tag, "not an ordered boradcast: " + e.toString());
			// }
		} else if (action.equals(pkg + Connector.ACTION_CAPTCHA_SOLVED)) {
			Log.d(tag, "got solved captcha");
			final Bundle extras = intent.getExtras();
			if (extras == null) {
				this.gotSolvedCaptcha(context, null);
			} else {
				this.gotSolvedCaptcha(context, extras
						.getString(EXTRA_CAPTCHA_SOLVED));
			}
			try {
				this.setResultCode(Activity.RESULT_OK);
			} catch (Exception e) {
				Log.w(tag, "not an ordered boradcast: " + e.toString());
			}
		} else if (action.equals(pkg + Connector.ACTION_RUN_BOOTSTRAP)
				|| action.equals(pkg + Connector.ACTION_RUN_UPDATE)
				|| action.equals(pkg + Connector.ACTION_RUN_SEND)) {
			Log.d(tag, "got command");
			final ConnectorCommand command = new ConnectorCommand(intent);
			final ConnectorSpec origSpecs = new ConnectorSpec(intent);
			boolean ordered = true;
			if (action.equals(pkg + Connector.ACTION_RUN_UPDATE)) {
				ordered = false;
			}
			if (specs == null) {
				// skip disabled connector
				Log.w(tag, "specs=null");
				return;
			}
			if (!specs.hasStatus(ConnectorSpec.STATUS_ENABLED)) {
				if (ordered) {
					this.setResultCode(Activity.RESULT_CANCELED);
				}
				Log.w(tag, "connector disabled");
				return;
			}
			if (command == null) {
				// skip faulty commands
				Log.w(tag, "command=null");
				return;
			}
			if (command.getType() != ConnectorCommand.TYPE_SEND
					|| (origSpecs != null && specs.equals(origSpecs))) {
				// command type is set.
				// if command == send: this receiver is the wanted one.
				registerInstance(this); // this instance will be run by service
				final Intent i = new Intent(context, ConnectorService.class);
				i.setAction(intent.getAction());
				// set command to intent
				command.setToIntent(i);
				if (origSpecs != null) {
					// set original sepcs to intent
					origSpecs.setToIntent(i);
				}
				// load updated specs to intent
				specs.setToIntent(i);
				Log.i(tag, "start service " + i.getAction());
				if (null != context.startService(i) && ordered) {
					// start service
					try {
						this.setResultCode(Activity.RESULT_OK);
					} catch (Exception e) {
						Log.w(tag, "not an ordered boradcast: " + e.toString());
					}
				}
			} else {
				Log.w(tag, "faulty command:");
				Log.w(tag, "command: " + command.getType());
				Log.w(tag, "origSpecs: " + origSpecs);
				Log.w(tag, "specs: " + specs);
			}
		}
	}

	/**
	 * Show {@link Toast} on main thread. Call this method for notifying the
	 * user.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param text
	 *            text
	 */
	protected final void showToast(final Context context, final String text) {
		if (context == null || text == null) {
			return;
		}
		if (context instanceof ConnectorService) {
			((ConnectorService) context).showToast(text);
		}
	}

	/**
	 * Do bootstrap: This method is called after each change of settings for
	 * doing some kind of remote setup. Most connectors do not need to implement
	 * this. Default implementation does nothing. This is executed in a
	 * different thread! Do not do any GUI stuff.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param intent
	 *            {@link Intent} coming from outside
	 * @throws IOException
	 *             IOException
	 */
	protected void doBootstrap(final Context context, final Intent intent)
			throws IOException {
		// do nothing by default
	}

	/**
	 * Do update: This method is called to update balance. Default
	 * implementation does nothing. This is executed in a different thread! Do
	 * not do any GUI stuff.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param intent
	 *            {@link Intent} coming from outside
	 * @throws IOException
	 *             IOException
	 */
	protected void doUpdate(final Context context, final Intent intent)
			throws IOException {
		// do nothing by default
	}

	/**
	 * Do send: This method is called to send the actual message. Default
	 * implementation does nothing. This is executed in a different thread! Do
	 * not do any GUI stuff.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param intent
	 *            {@link Intent} coming from outside
	 * @throws IOException
	 *             IOException
	 */
	protected void doSend(final Context context, final Intent intent)
			throws IOException {
		// do nothing by default
	}

	/**
	 * This method will be run, if any broadcast with a solved captcha arrived.
	 * You should release the locks waiting for this to happen. This is not done
	 * in the same thread as all the do* methods!
	 * 
	 * @param context
	 *            {@link Context}
	 * @param solvedCaptcha
	 *            the captcha solved as {@link String}, null if aborted by user.
	 */
	protected void gotSolvedCaptcha(final Context context,
			final String solvedCaptcha) {
		// do nothing by default
	}
}
