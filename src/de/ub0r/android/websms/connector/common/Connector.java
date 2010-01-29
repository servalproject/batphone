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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Receives commands coming as broadcast from WebSMS.
 * 
 * @author flx
 */
public abstract class Connector extends BroadcastReceiver {
	/** Tag for debug output. */
	private static final String TAG = "WebSMS.cbcr";

	/** Common Action prefix. */
	private static final String ACTION_PREFIX = "de.ub0r."
			+ "android.websms.connector.";

	/**
	 * Action to start a connector's {@link Service}. This should include a
	 * {@link ConnectorCommand}: bootstrap.
	 */
	public static final String ACTION_RUN_BOOTSTRAP = ".RUN_BOOTSTRAP";

	/**
	 * Action to start a connector's {@link Service}. This should include a
	 * {@link ConnectorCommand}: update.
	 */
	public static final String ACTION_RUN_UPDATE = ".RUN_UPDATE";

	/**
	 * Action to start a connector's {@link Service}. This should include a
	 * {@link ConnectorCommand}: send.
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
	 * @throws WebSMSException
	 *             WebSMSException
	 */
	protected static final Connector getInstance()// .
			throws WebSMSException {
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
	protected static final void registerInstance(// .
			final Connector receiver) {
		instance = receiver;
	}

	/**
	 * Init {@link ConnectorSpec}. This is only run once. Changing properties
	 * should be set in updateSpec(). Default implementation does nothing at
	 * all.
	 * 
	 * @param context
	 *            {@link Context}
	 * @return updated {@link ConnectorSpec}
	 */
	protected ConnectorSpec initSpec(final Context context) {
		return new ConnectorSpec(TAG, "noname");
	}

	/**
	 * Update {@link ConnectorSpec}. Default implementation does nothing at all.
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
	 * Init {@link ConnectorSpec}.
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
			}
			return this.updateSpec(context, connector);
		}
	}

	/**
	 * Send INFO Broadcast back to WebSMS.
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
		Log.d("WebSMS." + this.getSpec(context), "-> bc: " + i.getAction());
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
		final String action = intent.getAction();
		Log.d("WebSMS." + this.getSpec(context), "action: " + action);
		final String pkg = context.getPackageName();
		if (action == null) {
			return;
		}
		if (Connector.ACTION_CONNECTOR_UPDATE.equals(action)) {
			this.sendInfo(context, null, null);
		} else if (action.equals(pkg + Connector.ACTION_CAPTCHA_SOLVED)) {
			final Bundle extras = intent.getExtras();
			if (extras == null) {
				gotSolvedCaptcha(context, null);
			} else {
				gotSolvedCaptcha(context, extras
						.getString(EXTRA_CAPTCHA_SOLVED));
			}
		} else if (action.equals(pkg + Connector.ACTION_RUN_BOOTSTRAP)
				|| action.equals(pkg + Connector.ACTION_RUN_UPDATE)
				|| action.equals(pkg + Connector.ACTION_RUN_SEND)) {
			final ConnectorCommand command = new ConnectorCommand(intent);
			final ConnectorSpec origSpecs = new ConnectorSpec(intent);
			final ConnectorSpec specs = this.getSpec(context);
			if (specs == null || // .
					!specs.hasStatus(ConnectorSpec.STATUS_ENABLED)) {
				// skip disabled connector
				return;
			}
			if (command == null) {
				// skip faulty commands
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
				context.startService(i); // start service
			}
		}
	}

	/**
	 * Do bootstrap. This is executed in a different thread! Do not do any GUI
	 * stuff.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param intent
	 *            {@link Intent} comming from outside
	 * @throws WebSMSException
	 *             WebSMSException
	 */
	protected void doBootstrap(final Context context, final Intent intent)
			throws WebSMSException {
		// do nothing by default
	}

	/**
	 * Do update. This is executed in a different thread! Do not do any GUI
	 * stuff.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param intent
	 *            {@link Intent} coming from outside
	 * @throws WebSMSException
	 *             WebSMSException
	 */
	protected void doUpdate(final Context context, final Intent intent)
			throws WebSMSException {
		// do nothing by default
	}

	/**
	 * Do send. This is executed in a different thread! Do not do any GUI stuff.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param intent
	 *            {@link Intent} comming from outside
	 * @throws WebSMSException
	 *             WebSMSException
	 */
	protected void doSend(final Context context, final Intent intent)
			throws WebSMSException {
		// do nothing by default
	}

	/**
	 * This method will be run, if any bradcast with a solved captcha arrived.
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
