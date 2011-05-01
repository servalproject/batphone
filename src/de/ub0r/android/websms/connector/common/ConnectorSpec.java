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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

/**
 * {@link ConnectorSpec} presents all necessary informations to use a
 * {@link Connector}.
 * 
 * @author flx
 */
public final class ConnectorSpec implements Serializable {
	/** Serial version UID. */
	private static final long serialVersionUID = -1040204065670497635L;

	/** Write null string to serialized form. */
	private static final String NULL = "-NULL";

	/** Key to find a connector in a Bundle. */
	private static final String EXTRAS_CONNECTOR = "connector";

	/** Connector: Package. */
	private static final String PACKAGE = "connector_package";
	/** Connector: Name. */
	private static final String NAME = "connector_name";
	/** Connector's API version. */
	private static final String APIVERSION = "api_version";
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
	/** Connector: Capabilities. */
	private static final String CAPABILITIES = "connector_capabilities";
	/** Feature: none. */
	public static final short CAPABILITIES_NONE = 0;
	/** Feature: bootstrap. */
	public static final short CAPABILITIES_BOOTSTRAP = 1;
	/** Feature: update. */
	public static final short CAPABILITIES_UPDATE = 2;
	/** Feature: send. */
	public static final short CAPABILITIES_SEND = 4;
	/** Feature: preferences. */
	public static final short CAPABILITIES_PREFS = 8;
	/** Feature: limit lenth of messages using 7bit chars. */
	public static final short CAPABILITIES_LIMITLENGTH_7BIT = 8;
	/** Feature: limit lenth of messages using 8bit chars. */
	public static final short CAPABILITIES_LIMITLENGTH_8BIT = 16;
	/** Feature: limit lenth of messages using 16bit chars. */
	public static final short CAPABILITIES_LIMITLENGTH_16BIT = 32;
	/** Feature: limit to specified characters. */
	public static final short CAPABILITIES_CHARACTER_CHECK = 64;

	/** Connector: Limit for message length. */
	private static final String LENGTH = "connector_limitlength";
	/** Connector: Balance. */
	private static final String BALANCE = "connector_balance";
	/** Connector: Error message. */
	private static final String ERRORMESSAGE = "connector_errormessage";
	/** Connector: Valid characters. */
	private static final String VALID_CHARACTERS = "connector_valid_characters";
	/** Connector: Ad unit id for google's admob. */
	private static final String AD_UNITID = "ad_unitid";

	// Subconnectors
	/** Connector: SubConnector prefix. */
	private static final String SUB_PREFIX = "sub_";
	/** Connector: number of subconnectors. */
	private static final String SUB_COUNT = SUB_PREFIX + "n";

	/** Previous set balance. */
	private String oldBalance = null;

	/**
	 * {@link SubConnectorSpec} presents all necessary informations to use a
	 * SubConnector.
	 * 
	 * @author flx
	 */
	public final class SubConnectorSpec implements Serializable {
		/** Serial version UID. */
		private static final long serialVersionUID = -4074828738607134376L;

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
		private Bundle bundle = null;

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
		 * Write object to {@link ObjectOutputStream}.
		 * 
		 * @param stream
		 *            {@link ObjectOutputStream}
		 * @throws IOException
		 *             IOException
		 */
		private void writeObject(final ObjectOutputStream stream)
				throws IOException {
			writeString(stream, this.getID());
			writeString(stream, this.getName());
			stream.writeInt(this.getFeatures());
		}

		/**
		 * Read object from {@link ObjectInputStream}.
		 * 
		 * @param stream
		 *            {@link ObjectInputStream}
		 * @throws IOException
		 *             IOException
		 * @throws ClassNotFoundException
		 *             ClassNotFoundException
		 */
		private void readObject(final ObjectInputStream stream)
				throws IOException, ClassNotFoundException {
			this.bundle = new Bundle();
			this.bundle.putString(ID, readString(stream));
			this.bundle.putString(NAME, readString(stream));
			this.bundle.putShort(FEATURES, (short) stream.readInt());
		}

		/**
		 * Get internal {@link Bundle}.
		 * 
		 * @return internal {@link Bundle}
		 */
		Bundle getBundle() {
			return this.bundle;
		}

		/**
		 * Get ID.
		 * 
		 * @return ID
		 */
		public String getID() {
			return this.bundle.getString(ID);
		}

		/**
		 * Get name.
		 * 
		 * @return name
		 */
		public String getName() {
			return this.bundle.getString(NAME);
		}

		/**
		 * Get features.
		 * 
		 * @return features
		 */
		public short getFeatures() {
			return this.bundle.getShort(FEATURES, FEATURE_NONE);
		}

		/**
		 * Check if given features are supported.
		 * 
		 * @param features
		 *            features
		 * @return true if {@link SubConnectorSpec} has all given features
		 */
		public boolean hasFeatures(final short features) {
			final short f = this.getFeatures();
			return (f & features) == features;
		}
	}

	/** {@link Bundle} represents the {@link ConnectorSpec}. */
	private Bundle bundle = null;

	/**
	 * Read a {@link String} from {@link ObjectInputStream}.
	 * 
	 * @param stream
	 *            {@link ObjectInputStream}
	 * @return {@link String}
	 * @throws IOException
	 *             IOException
	 */
	static String readString(final ObjectInputStream stream) // .
			throws IOException {
		String ret = stream.readUTF();
		if (NULL.equals(ret)) {
			return null;
		}
		return ret;
	}

	/**
	 * Write {@link String} to {@link ObjectOutputStream}.
	 * 
	 * @param stream
	 *            {@link ObjectOutputStream}
	 * @param string
	 *            {@link String}
	 * @throws IOException
	 *             IOException
	 */
	static void writeString(final ObjectOutputStream stream, // .
			final String string) throws IOException {
		if (string == null) {
			stream.writeUTF(NULL);
		} else {
			stream.writeUTF(string);
		}
	}

	/**
	 * Write object to {@link ObjectOutputStream}.
	 * 
	 * @param stream
	 *            {@link ObjectOutputStream}
	 * @throws IOException
	 *             IOException
	 */
	private void writeObject(final ObjectOutputStream stream)
			throws IOException {
		writeString(stream, this.getPackage());
		writeString(stream, this.getName());
		writeString(stream, this.getAuthor());
		stream.writeInt(this.getCapabilities());
		stream.writeInt(this.getStatus());
		stream.writeInt(this.getLimitLength());
		writeString(stream, this.getValidCharacters());
		writeString(stream, this.getAdUnitIds());
		final SubConnectorSpec[] scss = this.getSubConnectors();
		stream.writeInt(scss.length);
		for (SubConnectorSpec scs : scss) {
			stream.writeObject(scs);
		}
	}

	/**
	 * Read object from {@link ObjectInputStream}.
	 * 
	 * @param stream
	 *            {@link ObjectInputStream}
	 * @throws IOException
	 *             IOException
	 * @throws ClassNotFoundException
	 *             ClassNotFoundException
	 */
	private void readObject(final ObjectInputStream stream) throws IOException,
			ClassNotFoundException {
		this.bundle = new Bundle();
		this.bundle.putString(PACKAGE, readString(stream));
		this.bundle.putString(NAME, readString(stream));
		this.bundle.putString(AUTHOR, readString(stream));
		this.bundle.putShort(CAPABILITIES, (short) stream.readInt());
		this.bundle.putShort(STATUS, (short) stream.readInt());
		this.bundle.putInt(LENGTH, stream.readInt());
		this.bundle.putString(VALID_CHARACTERS, readString(stream));
		this.bundle.putString(AD_UNITID, readString(stream));
		final int c = stream.readInt();
		for (int i = 0; i < c; i++) {
			this.addSubConnector((SubConnectorSpec) stream.readObject());
		}
	}

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
	 * @param name
	 *            name
	 */
	public ConnectorSpec(final String name) {
		this.bundle = new Bundle();
		this.bundle.putString(NAME, name);
	}

	/**
	 * Return a array of size 1 for use in toArray().
	 * 
	 * @return array of {@link SubConnectorSpec}.
	 */
	public static SubConnectorSpec[] getSubConnectorReturnArray() {
		return new SubConnectorSpec[1];
	}

	/**
	 * Update {@link ConnectorSpec} with values from the given
	 * {@link ConnectorSpec}.
	 * 
	 * @param connector
	 *            {@link ConnectorSpec}
	 */
	public void update(final ConnectorSpec connector) {
		final boolean error = this.hasStatus(STATUS_ERROR);
		this.oldBalance = this.getBalance();
		this.bundle.putAll(connector.getBundle());
		if (error) {
			this.addStatus(STATUS_ERROR);
		}
	}

	/**
	 * Return name of the {@link Connector}.
	 * 
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
	 * Check if both connectors are equal by ID.
	 * 
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
			return this.getPackage().equals(
					((ConnectorSpec) connector).getPackage());
		} else if (connector instanceof String) {
			return this.getPackage().equals(connector);
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
		final String s1 = b1.getString(PACKAGE);
		final String s2 = b2.getString(PACKAGE);
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
	 * Get {@link Bundle} representing the {@link ConnectorSpec}.
	 * 
	 * @return internal bundle
	 */
	public Bundle getBundle() {
		return this.bundle;
	}

	/**
	 * @return API version
	 */
	public int getAPIVersion() {
		if (this.bundle == null) {
			return -1;
		}
		return this.bundle.getInt(APIVERSION);
	}

	/**
	 * Do not run this method, internal use only.
	 * 
	 * @param v
	 *            API version
	 */
	public void setAPIVersion(final int v) {
		if (this.bundle == null) {
			return;
		}
		this.bundle.putInt(APIVERSION, v);
	}

	/**
	 * Get package name.
	 * 
	 * @return package
	 */
	public String getPackage() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(PACKAGE);
	}

	/**
	 * Set package name. This is run by {@link Connector} automatically.
	 * 
	 * @param p
	 *            package
	 */
	void setPackage(final String p) {
		if (this.bundle == null) {
			return;
		}
		this.bundle.putString(PACKAGE, p);
	}

	/**
	 * Get name.
	 * 
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
		if (this.bundle == null) {
			return;
		}
		this.bundle.putString(NAME, name);
	}

	/**
	 * Get status.
	 * 
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
		if (this.bundle == null) {
			return;
		}
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
		this.setStatus((short) status);
	}

	/**
	 * Set status: ready.
	 */
	public void setReady() {
		this.setStatus(STATUS_ENABLED | STATUS_READY);
	}

	/**
	 * Return whether this {@link Connector} is ready.
	 * 
	 * @return if {@link Connector} is ready
	 */
	public boolean isReady() {
		return this.hasStatus((short) (STATUS_ENABLED | STATUS_READY));
	}

	/**
	 * Return whether this {@link Connector} is running, eg. bootstrapping,
	 * updating or sending.
	 * 
	 * @return if {@link Connector} is running
	 */
	public boolean isRunning() {
		short s = this.getStatus();
		return (s & // .
		(STATUS_BOOTSTRAPPING | STATUS_UPDATING | STATUS_SENDING)) != 0;
	}

	/**
	 * Return whether this {@link ConnectorSpec} has all given status.
	 * 
	 * @param status
	 *            status
	 * @return true if connector has given status
	 */
	public boolean hasStatus(final short status) {
		if (this.bundle == null) {
			return false;
		}
		final short s = this.getStatus();
		return (s & status) == status;
	}

	/**
	 * Get author.
	 * 
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
		if (this.bundle == null) {
			return;
		}
		this.bundle.putString(AUTHOR, author);
	}

	/**
	 * Get all ad unit ids.
	 * 
	 * @return ad unit ids
	 */
	private String getAdUnitIds() {
		if (this.bundle == null) {
			return null;
		}
		final String ret = this.bundle.getString(AD_UNITID);
		if (TextUtils.isEmpty(ret)) {
			return null;
		}
		return ret;
	}

	/**
	 * Get ad unit id. If more than one are listed, one is picked by random.
	 * 
	 * @return ad unit id
	 */
	public String getAdUnitId() {
		if (this.bundle == null) {
			return null;
		}
		String ret = this.bundle.getString(AD_UNITID);
		if (TextUtils.isEmpty(ret)) {
			return null;
		}
		ret = ret.trim();
		if (TextUtils.isEmpty(ret)) {
			return null;
		}
		if (ret.contains(",")) {
			String[] rets = ret.split(",");
			final int l = rets.length;
			for (int i = 0; i < l; i++) {
				ret = rets[(int) Math.floor(Math.random() * l)];
				if (!TextUtils.isEmpty(ret)) {
					return ret;
				}
			}
		}
		if (TextUtils.isEmpty(ret)) {
			return null;
		}
		return ret;
	}

	/**
	 * Set ad unit id.
	 * 
	 * @param unitid
	 *            unit id; separate by "," for more than one unit id
	 */
	public void setAdUnitId(final String unitid) {
		if (this.bundle == null) {
			return;
		}
		this.bundle.putString(AD_UNITID, unitid);
	}

	/**
	 * Get limit of message length.
	 * 
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
	 * Get valid characters.
	 * 
	 * @return valid characters
	 */
	public String getValidCharacters() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(VALID_CHARACTERS);
	}

	/**
	 * Set valid characters.
	 * 
	 * @param validCharacters
	 *            valid characters
	 */
	public void setValidCharacters(final String validCharacters) {
		this.bundle.putString(VALID_CHARACTERS, validCharacters);
	}

	/**
	 * Get balance.
	 * 
	 * @return balance
	 */
	public String getBalance() {
		if (this.bundle == null) {
			return null;
		}
		return this.bundle.getString(BALANCE);
	}

	/**
	 * Get last set balance. This may be used to check whether it changed during
	 * last update.
	 * 
	 * @return previously set balance
	 */
	public String getOldBalance() {
		return this.oldBalance;
	}

	/**
	 * Set balance.
	 * 
	 * @param balance
	 *            balance
	 */
	public void setBalance(final String balance) {
		this.oldBalance = this.getBalance();
		this.bundle.putString(BALANCE, balance);
	}

	/**
	 * Get capabilities.
	 * 
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
	 * Return whether this {@link ConnectorSpec} has all the given capabilities.
	 * 
	 * @param capabilities
	 *            capabilities
	 * @return true if connector has given capabilities
	 */
	public boolean hasCapabilities(final short capabilities) {
		if (this.bundle == null) {
			return false;
		}
		final short c = this.getCapabilities();
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
		if (this.hasStatus(STATUS_ERROR)) {
			return this.bundle.getString(ERRORMESSAGE);
		} else {
			return null;
		}
	}

	/**
	 * Set error message.
	 * 
	 * @param error
	 *            error message
	 */
	public void setErrorMessage(final String error) {
		if (error == null) {
			int s = this.getStatus();
			s |= STATUS_ERROR;
			s ^= STATUS_ERROR;
			this.setStatus(s);
			if (this.bundle != null) {
				this.bundle.putString(ERRORMESSAGE, null);
			}
		} else {
			this.addStatus(STATUS_ERROR);
			if (this.bundle != null) {
				this.bundle.putString(ERRORMESSAGE, this.getName() + ": "
						+ error);
			}
		}
	}

	/**
	 * Set error message.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param error
	 *            error message
	 */
	public void setErrorMessage(final Context context, final Exception error) {
		if (error == null) {
			this.setErrorMessage((String) null);
		} else if (error instanceof WebSMSException) {
			this.setErrorMessage(error.getMessage());
		} else if (error instanceof IOException) {
			this.setErrorMessage(context.getString(R.string.error_connection));
		} else {
			this.setErrorMessage(error.toString());
		}
	}

	/**
	 * Get all {@link SubConnectorSpec}s of this {@link ConnectorSpec}.
	 * 
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
	 * Get number of {@link SubConnectorSpec}s of this {@link ConnectorSpec}.
	 * 
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
		if (this.bundle == null) {
			return null;
		}
		if (this.getSubConnectorCount() == 1) {
			return new SubConnectorSpec(this.bundle.getBundle(SUB_PREFIX + 0));
		}
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

	/**
	 * Add a {@link SubConnectorSpec}.
	 * 
	 * @param subconnector
	 *            {@link SubConnectorSpec}
	 */
	private void addSubConnector(final SubConnectorSpec subconnector) {
		this.addSubConnector(subconnector.getID(), subconnector.getName(),
				subconnector.getFeatures());
	}

}
