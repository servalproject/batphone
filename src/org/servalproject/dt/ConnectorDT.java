/*
 * Copyright (C) 2010-2011 Felix Bechstein
 * 
 * This file is part of WebSMS and Serval Project.
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
package org.servalproject.dt;

import java.io.IOException;

import org.servalproject.R;
import org.servalproject.dna.Dna;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import de.ub0r.android.websms.connector.common.Connector;
import de.ub0r.android.websms.connector.common.ConnectorCommand;
import de.ub0r.android.websms.connector.common.ConnectorSpec;
import de.ub0r.android.websms.connector.common.ConnectorSpec.SubConnectorSpec;
import de.ub0r.android.websms.connector.common.Log;
import de.ub0r.android.websms.connector.common.WebSMSException;

/**
 * AsyncTask to manage IO to DT API.
 * 
 * @author Thomas Giraud
 */
public class ConnectorDT extends Connector {
	/** Tag for output. */
	private static final String TAG = "ConnectorDT";

	@Override
	public final ConnectorSpec initSpec(final Context context) {
		Log.i(TAG, "initSpec()");
		final String name = context.getString(R.string.connector_dt_name);
		ConnectorSpec c = new ConnectorSpec(name);
		c.setAuthor(// .
		context.getString(R.string.connector_dt_author));
		c.setBalance("Free!");
		c.setCapabilities(ConnectorSpec.CAPABILITIES_UPDATE
				| ConnectorSpec.CAPABILITIES_SEND
				| ConnectorSpec.CAPABILITIES_PREFS);
		c.addSubConnector("Digital Telegram", c.getName(),
				SubConnectorSpec.FEATURE_NONE);
		return c;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec updateSpec(final Context context,
			final ConnectorSpec connectorSpec) {
		Log.i(TAG, "updateSpec()");
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (p.getBoolean(Preferences.PREFS_ENABLED, false)) {
			connectorSpec.setReady();
		} else {
			connectorSpec.setStatus(ConnectorSpec.STATUS_INACTIVE);
		}
		return connectorSpec;
	}

	/**
	 * Send text.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param command
	 *            {@link ConnectorCommand}
	 * @throws IOException
	 *             IOException
	 */
	private void sendText(final Context context, final ConnectorCommand command)
			throws IOException {
		String number = command.getRecipients()[0];
		String message = command.getText();
		Log.i(TAG, "sendText()");
		Log.i(TAG, "number : " + number);
		Log.i(TAG, "content : " + message);

		// Utils.national2international(
		// command.getDefPrefix();

		// String phoneNumber =
		// Utils.getRecipientsNumber(command.getRecipients()[0]).substring(1);

		// SEND A LOCAL SMS
		Intent i = new Intent();
		i.setAction("android.intent.action.PICK");
		i.setType("vnd.servalproject.DTSMS/vnd.servalproject.DTSMS-text");
		i.putExtra("number", number);
		i.putExtra("content", message);
		context.sendBroadcast(i);

		// SEND A MESH SMS THROUGH DNA
		Dna clientDNA = new Dna();
		boolean result = clientDNA.sendSms(number, message);
		Log.i(TAG, "sendSms has returned : " + result);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doUpdate(final Context context, final Intent intent) {
		Log.i(TAG, "doUpdate()");
		this.getSpec(context).setBalance("Free!");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doSend(final Context context, final Intent intent) {
		Log.i(TAG, "doSend()");
		try {
			this.sendText(context, new ConnectorCommand(intent));
		} catch (IOException e) {
			Log.e(TAG, "send failed", e);
			throw new WebSMSException(e.toString());
		}
	}
}
