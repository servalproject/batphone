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

import android.content.Intent;
import android.os.Bundle;

/**
 * A Command send to a Connector.
 * 
 * @author flx
 */
public final class ConnectorCommand implements Cloneable {

	/** Key to find command in a Bundle. */
	private static final String EXTRAS_COMMAND = "command";

	/** Command: type. */
	private static final String TYPE = "command_type";
	/** Command: type - bootstrap. */
	public static final short TYPE_BOOTSTRAP = 1;
	/** Command: type - update. */
	public static final short TYPE_UPDATE = 2;
	/** Command: type - send. */
	public static final short TYPE_SEND = 4;
	/** Command: default sender. */
	private static final String DEFSENDER = "command_defsender";
	/** Command: default prefix. */
	private static final String DEFPREFIX = "command_defprefix";
	/** Command: recipients. */
	private static final String RECIPIENTS = "command_reciepients";
	/** Command: text. */
	private static final String TEXT = "command_text";
	/** Command: flashsms. */
	private static final String FLASHSMS = "command_flashsms";
	/** Command: timestamp. */
	private static final String TIMESTAMP = "command_timestamp";
	/** Command: custom sender. */
	private static final String CUSTOMSENDER = "command_customsender";
	/** Command: list of message Uris. */
	private static final String MSG_URIS = "command_msgUris";
	/** Command: selected SubConnectorSpec for sending. */
	private static final String SELECTEDSUBCONNECTOR = // .
	"command_selectedsubconnector";

	/** {@link Bundle} represents the ConnectorSpec. */
	private final Bundle bundle;

	/**
	 * Create command with type update.
	 * 
	 * @param defPrefix
	 *            default prefix
	 * @param defSender
	 *            default sender
	 * @return created command
	 */
	public static ConnectorCommand update(final String defPrefix,
			final String defSender) {
		final Bundle b = new Bundle();
		b.putShort(TYPE, TYPE_UPDATE);
		b.putString(DEFPREFIX, defPrefix);
		b.putString(DEFSENDER, defSender);
		return new ConnectorCommand(b);
	}

	/**
	 * Create command with type bootstrap.
	 * 
	 * @param defPrefix
	 *            default prefix
	 * @param defSender
	 *            default sender
	 * @return created command
	 */
	public static ConnectorCommand bootstrap(final String defPrefix,
			final String defSender) {
		final Bundle b = new Bundle();
		b.putShort(TYPE, TYPE_BOOTSTRAP);
		b.putString(DEFPREFIX, defPrefix);
		b.putString(DEFSENDER, defSender);
		return new ConnectorCommand(b);
	}

	/**
	 * Create Command with type "send".
	 * 
	 * @param selectedSubConnector
	 *            selected SubConnectorSpec
	 * @param defPrefix
	 *            default prefix
	 * @param defSender
	 *            default sender
	 * @param recipients
	 *            recipients
	 * @param text
	 *            text
	 * @param flashSMS
	 *            flashsms
	 * @param timestamp
	 *            timestamp for sending
	 * @param customSender
	 *            custom sender
	 * @return created command
	 */
	public static ConnectorCommand send(final String selectedSubConnector,
			final String defPrefix, final String defSender,
			final String[] recipients, final String text,
			final boolean flashSMS, final long timestamp,
			final String customSender) {
		ConnectorCommand ret = send(selectedSubConnector, defPrefix, defSender,
				recipients, text, flashSMS);
		ret.setSendLater(timestamp);
		ret.setCustomSender(customSender);
		return ret;
	}

	/**
	 * Create Command with type "send".
	 * 
	 * @param selectedSubConnector
	 *            selected SubConnectorSpec
	 * @param defPrefix
	 *            default prefix
	 * @param defSender
	 *            default sender
	 * @param recipients
	 *            recipients
	 * @param text
	 *            text
	 * @param flashSMS
	 *            flashsms
	 * @return created command
	 */
	public static ConnectorCommand send(final String selectedSubConnector,
			final String defPrefix, final String defSender,
			final String[] recipients, final String text, // .
			final boolean flashSMS) {
		final Bundle b = new Bundle();
		b.putShort(TYPE, TYPE_SEND);
		b.putString(SELECTEDSUBCONNECTOR, selectedSubConnector);
		b.putString(DEFPREFIX, defPrefix);
		b.putString(DEFSENDER, defSender);
		final int l = recipients.length;
		ArrayList<String> r = new ArrayList<String>(l);
		String s;
		for (int i = 0; i < l; i++) {
			s = recipients[i];
			if (s != null && s.trim().length() > 0) {
				r.add(s);
			}
		}
		s = null;
		b.putStringArray(RECIPIENTS, r.toArray(new String[0]));
		b.putString(TEXT, text);
		b.putBoolean(FLASHSMS, flashSMS);
		b.putLong(TIMESTAMP, -1);
		b.putString(CUSTOMSENDER, null);
		return new ConnectorCommand(b);
	}

	/**
	 * Create Command from {@link Bundle}.
	 * 
	 * @param b
	 *            Bundle
	 */
	private ConnectorCommand(final Bundle b) {
		this.bundle = b;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object clone() {
		return new ConnectorCommand((Bundle) this.bundle.clone());
	}

	/**
	 * Create Command from {@link Intent}.
	 * 
	 * @param i
	 *            Intent
	 */
	public ConnectorCommand(final Intent i) {
		Bundle e = i.getExtras();
		if (e != null) {
			this.bundle = e.getBundle(EXTRAS_COMMAND);
		} else {
			this.bundle = new Bundle();
		}
	}

	/**
	 * Set this {@link ConnectorCommand} to an {@link Intent}. Creates new
	 * Intent if needed.
	 * 
	 * @param intent
	 *            {@link Intent}.
	 * @return the same {@link Intent}
	 */
	public Intent setToIntent(final Intent intent) {
		Intent i = intent;
		if (i == null) {
			switch (this.getType()) {
			case TYPE_BOOTSTRAP:
				i = new Intent(Connector.ACTION_RUN_BOOTSTRAP);
				break;
			case TYPE_UPDATE:
				i = new Intent(Connector.ACTION_RUN_UPDATE);
				break;
			case TYPE_SEND:
				i = new Intent(Connector.ACTION_RUN_SEND);
				break;
			default:
				return null;
			}
		}
		i.putExtra(EXTRAS_COMMAND, this.getBundle());
		return i;
	}

	/**
	 * Get internal {@link Bundle}.
	 * 
	 * @return internal {@link Bundle}
	 */
	public Bundle getBundle() {
		return this.bundle;
	}

	/**
	 * Get type.
	 * 
	 * @return type
	 */
	public short getType() {
		if (this.bundle != null) {
			return this.bundle.getShort(TYPE);
		} else {
			return 0;
		}
	}

	/**
	 * Get selected SubConnectorSpec.
	 * 
	 * @return selected SubConnectorSpec
	 */
	public String getSelectedSubConnector() {
		if (this.bundle != null) {
			return this.bundle.getString(SELECTEDSUBCONNECTOR);
		} else {
			return "";
		}
	}

	/**
	 * Get default sender.
	 * 
	 * @return default sender
	 */
	public String getDefSender() {
		return this.bundle.getString(DEFSENDER);
	}

	/**
	 * Get default prefix.
	 * 
	 * @return default prefix
	 */
	public String getDefPrefix() {
		return this.bundle.getString(DEFPREFIX);
	}

	/**
	 * Set a single recipient to this command.
	 * 
	 * @param recipient
	 *            recipient
	 */
	public void setRecipients(final String recipient) {
		this.bundle.putStringArray(RECIPIENTS, new String[] { recipient });
	}

	/**
	 * Get recipients.
	 * 
	 * @return recipients
	 */
	public String[] getRecipients() {
		return this.bundle.getStringArray(RECIPIENTS);
	}

	/**
	 * Get text.
	 * 
	 * @return text
	 */
	public String getText() {
		return this.bundle.getString(TEXT);
	}

	/**
	 * Check if message should be sent as flash sms.
	 * 
	 * @return flashsms
	 */
	public boolean getFlashSMS() {
		return this.bundle.getBoolean(FLASHSMS, false);
	}

	/**
	 * Get time of when the message should be sent.
	 * 
	 * @return timestamp for sending
	 */
	public long getSendLater() {
		return this.bundle.getLong(TIMESTAMP, -1);
	}

	/**
	 * Set timestamp for sending later.
	 * 
	 * @param timestamp
	 *            timestamp
	 */
	public void setSendLater(final long timestamp) {
		this.bundle.putLong(TIMESTAMP, timestamp);
	}

	/**
	 * Get custom sender with which the message should be sent.
	 * 
	 * @return custom sender
	 */
	public String getCustomSender() {
		return this.bundle.getString(CUSTOMSENDER);
	}

	/**
	 * Set custom sender.
	 * 
	 * @param customSender
	 *            custom sender
	 */
	public void setCustomSender(final String customSender) {
		this.bundle.putString(CUSTOMSENDER, customSender);
	}

	/**
	 * Get message Uris.
	 * 
	 * @return message uris
	 */
	public String[] getMsgUris() {
		return this.bundle.getStringArray(MSG_URIS);
	}

	/**
	 * Set message Uris.
	 * 
	 * @param uris
	 *            uris
	 */
	public void setMsgUris(final String[] uris) {
		this.bundle.putStringArray(MSG_URIS, uris);
	}

	/**
	 * Compare two {@link Intent}s.
	 * 
	 * @param i1
	 *            first intent
	 * @param i2
	 *            second intent
	 * @return true if both intents describe the same command
	 */
	public static boolean equals(final Intent i1, final Intent i2) {
		Bundle b1 = i1.getExtras();
		Bundle b2 = i2.getExtras();
		if (b1 == null || b2 == null) {
			return false;
		}
		b1 = b1.getBundle(EXTRAS_COMMAND);
		b2 = b2.getBundle(EXTRAS_COMMAND);
		if (b1 == null || b2 == null) {
			return false;
		}
		final short s1 = b1.getShort(TYPE);
		final short s2 = b2.getShort(TYPE);
		return s1 == s2;
	}
}
