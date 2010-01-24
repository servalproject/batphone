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

import android.content.Intent;
import android.os.Bundle;

/**
 * {@link ConnectorSpec} presents all necessary informations to use a
 * {@link Connector}.
 * 
 * @author flx
 */
public final class ConnectorSpec {

	/** Key to find a connector in a Bundle. */
	private static final String EXTRAS_CONNECTOR = "connector";

	/** Connector: Package. */
	private static final String PACKAGE = "connector_package";
	/** Connector: ID. */
	private static final String ID = "connector_id";
	/** Connector: ID. */
	private static final String NAME = "connector_name";
	/** Connector: Status. */
	private static final String STATUS = "connector_status";
	/** Connector: Status: inactive. */
	public static final short STATUS_INACTIVE = 0;
	/** Connector: Status: enabled. */
	public static final short STATUS_ENABLED = 1;
	/** Connector: Status: ready. */
	public static final short STATUS_READY = 2;
	/** Connector: Status: bootstrapping. */
	public static final short STATUS_BOOTSTRAPPING = 4;
	/** Connector: Status: updating. */
	public static final short STATUS_UPDATING = 8;
	/** Connector: Status: sending. */
	public static final short STATUS_SENDING = 16;
	/** Connector: Status: error. */
	public static final short STATUS_ERROR = 32;
	/** Connector: Author. */
	private static final String AUTHOR = "connector_author";
	/** Connector: Preferences' intent URI. */
	private static final String PREFSINTENT = "connector_prefsintent";
	/** Connector: Preferences' title. */
	private static final String PREFSTITLE = "connector_prefstitle";
	/** Connector: Capabilities. */
	private static final String CAPABILITIES = "connector_capabilities";
	/** Feature: none. */
	public static final short CAPABILITIES_NONE = 0;
	/** Feature: bootstrap. */
	public static final short CAPABILITIES_BOOSTRAP = 1;
	/** Feature: update. */
	public static final short CAPABILITIES_UPDATE = 2;
	/** Feature: send. */
	public static final short CAPABILITIES_SEND = 4;
	/** Feature: limit lenth of messages using 7bit chars. */
	public static final short CAPABILITIES_LIMITLENGTH_7BIT = 8;
	/** Feature: limit lenth of messages using 8bit chars. */
	public static final short CAPABILITIES_LIMITLENGTH_8BIT = 16;
	/** Feature: limit lenth of messages using 16bit chars. */
	public static final short CAPABILITIES_LIMITLENGTH_16BIT = 32;

	/** Connector: Limit for message length. */
	private static final String LENGTH = "connector_limitlength";
	/** Connector: Balance. */
	private static final String BALANCE = "connector_balance";
	/** Connector: Error message. */
	private static final String ERRORMESSAGE = "connector_errormessage";

	// Subconnectors
	/** Connector: SubConnector prefix. */
	private static final String SUB_PREFIX = "sub_";
	/** Connector: number of subconnectors. */
	private static final String SUB_COUNT = SUB_PREFIX + "n";

	/**
	 * {@link SubConnectorSpec} presents all necessary informations to use a
	 * SubConnector.
	 * 
	 * @author flx
	 */
	public final class SubConnectorSpec {
		/** Connector: ID. */
		private static final String ID = "subconnector_id";
		/** Connector: name. */
		private static final String NAME = "subconnector_name";
		/** Connector: features. */
		private static final String FEATURES = "subconnector_features";
		/** Feature: none. */
		public static final short FEATURE_NONE = 0;
		/** Feature: multiple recipients. */
		public static final short FEATURE_MULTIRECIPIENTS = 1;
		/** Feature: flash sms. */
		public static final short FEATURE_FLASHSMS = 2;
		/** Feature: send later. */
		public static final short FEATURE_SENDLATER = 4;
		/** Feature: send later - only 1/4 hours allowd. */
		public static final short FEATURE_SENDLATER_QUARTERS = 8;
		/** Feature: custom sender. */
		public static final short FEATURE_CUSTOMSENDER = 16;

		/** {@link Bundle} represents the SubConnectorSpec. */
		private final Bundle bundle;

		/**
		 * Create {@link SubConnectorSpec} from {@link Bundle}.
		 * 
		 * @param b
		 *            {@link Bundle}
		 */
		SubConnectorSpec(final Bundle b) {
			this.bundle = b;
		}

		/**
		 * Create {@link SubConnectorSpec}.
		 * 
		 * @param id
		 *            id
		 * @param name
		 *            name
		 * @param features
		 *            features
		 */
		SubConnectorSpec(final String id, final String name,
				final short features) {
			this.bundle = new Bundle();
			this.bundle.putString(ID, id);
			this.bundle.putString(NAME, name);
			this.bundle.putShort(FEATURES, features);
		}

		/**
		 * @return internal {@link Bundle}
		 */
		Bundle getBundle() {
			return this.bundle;
		}

		/**
		 * @return ID
		 */
		public String getID() {
			return this.bundle.getString(ID);
		}

		/**
		 * @return name
		 */
		public String getName() {
			return this.bundle.getString(NAME);
		}

		/**
		 * @return features
		 */
		public short getFeatures() {
			return this.bundle.getShort(FEATURES, FEATURE_NONE);
		}

		/**
		 * @param features
		 *            features
		 * @return true if {@link SubConnectorSpec} has given features
		 */
		public boolean hasFeatures(final short features) {
			final short f = this.bundle.getShort(FEATURES, FEATURE_NONE);
			return (f & features) == features;
		}
	}

	/** {@link Bundle} represents the {@link ConnectorSpec}. */
	private final Bundle bundle;

	/**
	 * Create {@link ConnectorSpec} from {@link Intent}.
	 * 
	 * @param i
	 *            {@link Intent}
	 */
	public ConnectorSpec(final Intent i) {
		Bundle e = i.getExtras();
		if (e != null) {
			this.bundle = e.getBundle(EXTRAS_CONNECTOR);
		} else {
			this.bundle = new Bundle();
		}
	}

	/**
	 * Create {@link ConnectorSpec}.
	 * 
	 * @param id
	 *            ID
	 * @param name
	 *            name
	 */
	public ConnectorSpec(final String id, final String name) {
		this.bundle = new Bundle();
		this.bundle.putString(ID, id);
		this.bundle.putString(NAME, name);
	}

	/**
	 * @return array of {@link SubConnectorSpec}.
	 */
	public static SubConnectorSpec[] getSubConnectorReturnArray() {
		return new SubConnectorSpec[1];
	}

	/**
	 * Update {@link ConnectorSpec}.
	 * 
	 * @param connector
	 *            {@link ConnectorSpec}
	 */
	public void update(final ConnectorSpec connector) {
		final boolean error = this.hasStatus(STATUS_ERROR);
		this.bundle.putAll(connector.getBundle());
		if (error) {
			this.addStatus(STATUS_ERROR);
		}
	}

	/**
	 * @return name.
	 */
	@Override
	public String toString() {
		return this.getName();
	}

	/**
	 * Does nothing. {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return 0;
	}

	/**
	 * @param connector
	 *            {@link ConnectorSpec} or {@link String}
	 * @return true if this {@link ConnectorSpec} has the same id as
	 *         {@link ConnectorSpec}
	 */
	@Override
	public boolean equals(final Object connector) {
		if (this == connector) {
			return true;
		} else if (connector == null) {
			return false;
		} else if (connector instanceof ConnectorSpec) {
			return this.getID().equals(((ConnectorSpec) connector).getID());
		} else if (connector instanceof String) {
			return this.getID().equals(connector);
		} else {
			return false;
		}
	}

	/**
	 * Compare two {@link Intent}s.
	 * 
	 * @param i1
	 *            first {@link Intent}
	 * @param i2
	 *            second {@link Intent}
	 * @return true if both {@link Intent}s describe the same
	 *         {@link ConnectorSpec}
	 */
	public static boolean equals(final Intent i1, final Intent i2) {
		Bundle b1 = i1.getExtras();
		Bundle b2 = i2.getExtras();
		if (b1 == null || b2 == null) {
			return false;
		}
		b1 = b1.getBundle(EXTRAS_CONNECTOR);
		b2 = b2.getBundle(EXTRAS_CONNECTOR);
		if (b1 == null || b2 == null) {
			return false;
		}
		final String s1 = b1.getString(ID);
		final String s2 = b2.getString(ID);
		if (s1 == null || s2 == null) {
			return false;
		}
		return s1.equals(s2);
	}

	/**
	 * Set this {@link ConnectorSpec} to an {@link Intent}.
	 * 
	 * @param intent
	 *            {@link Intent}.
	 * @return the same {@link Intent}
	 */
	public Intent setToIntent(final Intent intent) {
		Intent i = intent;
		if (i == null) {
			i = new Intent(Connector.ACTION_INFO);
		}
		i.putExtra(EXTRAS_CONNECTOR, this.getBundle());
		return i;
	}

	/**
	 * @return internal bundle
	 */
	public Bundle getBundle() {
		return this.bundle;
	}

	/**
	 * @return package
	 */
	public String getPackage() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(PACKAGE);
	}

	/**
	 * Set package name.
	 * 
	 * @param p
	 *            package
	 */
	void setPackage(final String p) {
		this.bundle.putString(PACKAGE, p);
	}

	/**
	 * @return ID
	 */
	public String getID() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(ID);
	}

	/**
	 * @return Name
	 */
	public String getName() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(NAME);
	}

	/**
	 * Set name.
	 * 
	 * @param name
	 *            name
	 */
	public void setName(final String name) {
		this.bundle.putString(NAME, name);
	}

	/**
	 * @return status
	 */
	public short getStatus() {
		if (this.bundle == null) {
			return STATUS_INACTIVE;
		}
		return this.bundle.getShort(STATUS, STATUS_INACTIVE);
	}

	/**
	 * Set status.
	 * 
	 * @param status
	 *            status
	 */
	public void setStatus(final short status) {
		this.bundle.putShort(STATUS, status);
	}

	/**
	 * Add status.
	 * 
	 * @param status
	 *            status
	 */
	public void addStatus(final short status) {
		this.setStatus(status | this.getStatus());
	}

	/**
	 * Set status.
	 * 
	 * @param status
	 *            status
	 */
	public void setStatus(final int status) {
		this.bundle.putShort(STATUS, (short) status);
	}

	/**
	 * Set status: ready.
	 */
	public void setReady() {
		this.setStatus(STATUS_ENABLED | STATUS_READY);
	}

	/**
	 * @param status
	 *            status
	 * @return true if connector has given status
	 */
	public boolean hasStatus(final short status) {
		if (this.bundle == null) {
			return false;
		}
		final short s = this.bundle.getShort(STATUS, STATUS_INACTIVE);
		return (s & status) == status;
	}

	/**
	 * @return author
	 */
	public String getAuthor() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(AUTHOR);
	}

	/**
	 * Set author.
	 * 
	 * @param author
	 *            author
	 */
	public void setAuthor(final String author) {
		this.bundle.putString(AUTHOR, author);
	}

	/**
	 * @return prefs intent uri
	 */
	public String getPrefsIntent() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(PREFSINTENT);
	}

	/**
	 * Set prefs intent.
	 * 
	 * @param prefsIntent
	 *            prefs intent
	 */
	public void setPrefsIntent(final String prefsIntent) {
		this.bundle.putString(PREFSINTENT, prefsIntent);
	}

	/**
	 * @return prefs title
	 */
	public String getPrefsTitle() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(PREFSTITLE);
	}

	/**
	 * Set prefs title.
	 * 
	 * @param prefsTitle
	 *            prefs title
	 */
	public void setPrefsTitle(final String prefsTitle) {
		this.bundle.putString(PREFSTITLE, prefsTitle);
	}

	/**
	 * @return limit of message length
	 */
	public int getLimitLength() {
		if (this.bundle == null) {
			return -1;
		}
		return this.bundle.getInt(LENGTH);
	}

	/**
	 * Set limit of message length.
	 * 
	 * @param length
	 *            length
	 */
	public void setLimitLength(final int length) {
		this.bundle.putInt(LENGTH, length);
	}

	/**
	 * @return balance
	 */
	public String getBalance() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(BALANCE);
	}

	/**
	 * Set balance.
	 * 
	 * @param balance
	 *            balance
	 */
	public void setBalance(final String balance) {
		this.bundle.putString(BALANCE, balance);
	}

	/**
	 * @return capabilities
	 */
	public short getCapabilities() {
		if (this.bundle == null) {
			return CAPABILITIES_NONE;
		}
		return this.bundle.getShort(CAPABILITIES, CAPABILITIES_NONE);
	}

	/**
	 * Set capabilities.
	 * 
	 * @param capabilities
	 *            capabilities
	 */
	public void setCapabilities(final short capabilities) {
		this.bundle.putShort(CAPABILITIES, capabilities);
	}

	/**
	 * Set capabilities.
	 * 
	 * @param capabilities
	 *            capabilities
	 */
	public void setCapabilities(final int capabilities) {
		this.setCapabilities((short) capabilities);
	}

	/**
	 * @param capabilities
	 *            capabilities
	 * @return true if connector has given capabilities
	 */
	public boolean hasCapabilities(final short capabilities) {
		if (this.bundle == null) {
			return false;
		}
		final short c = this.bundle.getShort(CAPABILITIES, CAPABILITIES_NONE);
		return (c & capabilities) == capabilities;
	}

	/**
	 * Get error message.
	 * 
	 * @return error message
	 */
	public String getErrorMessage() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(ERRORMESSAGE);
	}

	/**
	 * Set error message.
	 * 
	 * @param error
	 *            error message
	 */
	public void setErrorMessage(final String error) {
		if (error != null) {
			this.addStatus(STATUS_ERROR);
		}
		this.bundle.putString(ERRORMESSAGE, this.getName() + ": " + error);
	}

	/**
	 * Set error message.
	 * 
	 * @param error
	 *            error message
	 */
	public void setErrorMessage(final Exception error) {
		if (error != null) {
			this.addStatus(STATUS_ERROR);
		}
		if (error instanceof WebSMSException) {
			this.bundle.putString(ERRORMESSAGE, this.getName() + ": "
					+ error.getMessage());
		} else {
			this.bundle.putString(ERRORMESSAGE, this.getName() + ": "
					+ error.toString());
		}
	}

	/**
	 * @return all {@link SubConnectorSpec}
	 */
	public SubConnectorSpec[] getSubConnectors() {
		if (this.bundle == null) {
			return null;
		}
		final int c = this.bundle.getInt(SUB_COUNT, 0);
		final SubConnectorSpec[] ret = new SubConnectorSpec[c];
		for (int i = 0; i < c; i++) {
			ret[i] = new SubConnectorSpec(// .
					this.bundle.getBundle(SUB_PREFIX + i));
		}
		return ret;
	}

	/**
	 * @return number of {@link SubConnectorSpec}
	 */
	public int getSubConnectorCount() {
		if (this.bundle == null) {
			return 0;
		}
		return this.bundle.getInt(SUB_COUNT, 0);
	}

	/**
	 * Get {@link SubConnectorSpec} by ID.
	 * 
	 * @param id
	 *            ID
	 * @return {@link SubConnectorSpec}
	 */
	public SubConnectorSpec getSubConnector(final String id) {
		if (id == null || this.bundle == null) {
			return null;
		}
		final int c = this.bundle.getInt(SUB_COUNT, 0);
		for (int i = 0; i < c; i++) {
			final SubConnectorSpec sc = new SubConnectorSpec(this.bundle
					.getBundle(SUB_PREFIX + i));
			if (id.equals(sc.getID())) {
				return sc;
			}
		}
		return null;
	}

	/**
	 * Add a {@link SubConnectorSpec}.
	 * 
	 * @param id
	 *            id
	 * @param name
	 *            name
	 * @param features
	 *            features
	 */
	public void addSubConnector(final String id, final String name,
			final short features) {
		if (this.bundle == null) {
			return;
		}
		final int c = this.bundle.getInt(SUB_COUNT, 0);
		this.bundle.putBundle(SUB_PREFIX + c, new SubConnectorSpec(id, name,
				features).getBundle());
		this.bundle.putInt(SUB_COUNT, c + 1);
	}

	/**
	 * Add a {@link SubConnectorSpec}.
	 * 
	 * @param id
	 *            id
	 * @param name
	 *            name
	 * @param features
	 *            features
	 */
	public void addSubConnector(final String id, final String name,
			final int features) {
		this.addSubConnector(id, name, (short) features);
	}
}
