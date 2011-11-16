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
/**
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
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.dna.DataFile;
import org.servalproject.dna.Dna;
import org.servalproject.dna.SubscriberId;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeMessage;

import android.content.Context;
import android.content.Intent;
import de.ub0r.android.websms.connector.common.Connector;
import de.ub0r.android.websms.connector.common.ConnectorCommand;
import de.ub0r.android.websms.connector.common.ConnectorSpec;
import de.ub0r.android.websms.connector.common.Log;
import de.ub0r.android.websms.connector.common.WebSMSException;
import de.ub0r.android.websms.connector.common.ConnectorSpec.SubConnectorSpec;

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
				| ConnectorSpec.CAPABILITIES_SEND);
		c.setLimitLength(255);
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

		if (ServalBatPhoneApplication.context.getState() == State.On) {
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
		Dna clientDNA = new Dna();
		clientDNA.timeout = 3000;
		ServalBatPhoneApplication app = (ServalBatPhoneApplication) context
				.getApplicationContext();

		clientDNA.setDynamicPeers(app.wifiRadio.getPeers());
		String senderNumber = DataFile.getDid(0);

		for (String recipient : command.getRecipients()) {
			String number = recipient.split(" ")[0];
			String message = command.getText();
			Log.i(TAG, "sendText()");
			Log.i(TAG, "number : " + number);
			Log.i(TAG, "content : " + message);

			boolean result;

			// Try to send the message on-line through DNA, since it
			// is fastest, and we basically know it has been delivered right
			// now if it succeeds.
			result = clientDNA.sendSms(senderNumber, number, message);
			Log.i(TAG, "DNA sendSms has returned : " + result);

			// But if the above does not succeed, then let's push it out via
			// our Rhizome public messag log instead.
			if (result == false) {
				// Send a mesh SMS through Rhizome
				SubscriberId sid = app.getPrimarySID();
				RhizomeMessage rm = new RhizomeMessage(senderNumber, number,
						message);
				result = Rhizome.appendMessage(sid, rm.toBytes());
				Log.i(TAG, "Rhizome append SMS message returned : " + result);
			}
		}
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
