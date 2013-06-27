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

package org.servalproject.servald;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Represents the result of invoking servald via the JNI command-line interface.  The 'args'
 * attribute contains a copy of the arguments that were passed to the call that produced this
 * result, to facilitate diagnosis of failures and errors.  The results of a call are an integer
 * 'status' value (normally the process exit status) and a list of strings in 'outv', called "output
 * fields".  These strings must be interpreted depending on the operation that produced them.
 *
 * Many operations return information about their outcome as a sequence of key-value pairs of
 * fields.  The getField() method and variants offer an order-independent means to query these
 * fields by key and optionally enforce a type on the value.  If the operation produces output
 * that is not in the key-value structure, then the caller simply avoids using these methods,
 * and accesses the 'outv' array directly.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class ServalDResult
{
	public static final int STATUS_ERROR = 255;

	public final String[] args;
	public final int status;
	public final byte[][] outv;
	private HashMap<String,byte[]> keyValue;

	public ServalDResult(String[] args, int status, byte[][] outv) {
		this.args = args;
		this.status = status;
		this.outv = outv;
		this.keyValue = null;
	}

	public ServalDResult(ServalDResult orig) {
		this.args = orig.args;
		this.status = orig.status;
		this.outv = orig.outv;
		this.keyValue = orig.keyValue;
	}

	public String getCommandString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < this.args.length; i++) {
			if (i > 0)
				sb.append(' ');
			sb.append(args[i]);
		}
		return sb.toString();
	}
	@Override
	public String toString() {
		String[] outvstr = new String[this.outv.length];
		for (int i = 0; i != this.outv.length; ++i)
			outvstr[i] = new String(this.outv[i]);
		return this.getClass().getName() + "(args=" + Arrays.deepToString(this.args) + ", status=" + this.status + ", outv=" + Arrays.deepToString(outvstr) + ")";
	}

	public void failIfStatusError() throws ServalDFailureException {
		if (this.status == STATUS_ERROR)
			throw new ServalDFailureException("error exit status", this);
	}

	public void failIfStatusNonzero() throws ServalDFailureException {
		if (this.status != 0)
			throw new ServalDFailureException("non-zero exit status", this);
	}

	protected void makeKeyValueMap() {
		if (this.keyValue == null) {
			if (this.outv.length % 2 != 0)
				throw new ServalDInterfaceError("odd number of fields", this);
			this.keyValue = new HashMap<String,byte[]>();
			for (int i = 0; i != this.outv.length; i += 2)
				this.keyValue.put(new String(this.outv[i]), this.outv[i + 1]);
		}
	}

	public Map<String,byte[]> getKeyValueMap() {
		makeKeyValueMap();
		return new HashMap<String,byte[]>(this.keyValue);
	}

	protected byte[] getFieldOrNull(String fieldName) {
		makeKeyValueMap();
		if (!this.keyValue.containsKey(fieldName))
			return null;
		return this.keyValue.get(fieldName);
	}

	protected byte[] getField(String fieldName) throws ServalDInterfaceError {
		byte[] value = getFieldOrNull(fieldName);
		if (value == null)
			throw new ServalDInterfaceError("missing '" + fieldName + "' field", this);
		return value;
	}

	public byte[] getFieldByteArray(String fieldName, byte[] defaultValue) {
		byte[] value = getFieldOrNull(fieldName);
		if (value == null)
			return defaultValue;
		return value;
	}

	public String getFieldString(String fieldName, String defaultValue) {
		byte[] value = getFieldOrNull(fieldName);
		if (value == null)
			return defaultValue;
		return new String(value);
	}

	public String getFieldString(String fieldName) throws ServalDInterfaceError {
		return new String(getField(fieldName));
	}

	public String getFieldStringNonEmptyOrNull(String fieldName) throws ServalDInterfaceError {
		String value = getFieldString(fieldName, "");
		return value.length() == 0 ? null : value;
	}

	public long getFieldLong(String fieldName) throws ServalDInterfaceError {
		String value = getFieldString(fieldName);
		try {
			return new Long(value);
		}
		catch (NumberFormatException e) {
			throw new ServalDInterfaceError("field " + fieldName + "='" + value + "' is not of type long", this);
		}
	}

	public int getFieldInt(String fieldName) throws ServalDInterfaceError {
		String value = getFieldString(fieldName);
		try {
			return new Integer(value);
		}
		catch (NumberFormatException e) {
			throw new ServalDInterfaceError("field " + fieldName + "='" + value + "' is not of type int", this);
		}
	}

	public boolean getFieldBoolean(String fieldName) throws ServalDInterfaceError {
		String value = getFieldString(fieldName);
		try {
			if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("on"))
				return true;
			if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no") || value.equalsIgnoreCase("off"))
				return false;
			return Integer.parseInt(value) != 0;
		}
		catch (NumberFormatException e) {
		}
		throw new ServalDInterfaceError("field " + fieldName + "='" + value + "' is not of type boolean", this);
	}

	public SubscriberId getFieldSubscriberId(String fieldName, SubscriberId defaultValue) throws ServalDInterfaceError {
		byte[] value = getFieldOrNull(fieldName);
		if (value == null)
			return defaultValue;
		String str = new String(value);
		try {
			return new SubscriberId(str);
		}
		catch (BundleId.InvalidHexException e) {
			throw new ServalDInterfaceError("field " + fieldName + "='" + str + "' is not a Bundle ID: " + e.getMessage(), this);
		}
	}

	public SubscriberId getFieldSubscriberId(String fieldName) throws ServalDInterfaceError {
		SubscriberId value = getFieldSubscriberId(fieldName, null);
		if (value == null)
			throw new ServalDInterfaceError("missing '" + fieldName + "' field", this);
		return value;
	}

	public BundleId getFieldBundleId(String fieldName) throws ServalDInterfaceError {
		String value = getFieldString(fieldName);
		try {
			return new BundleId(value);
		}
		catch (BundleId.InvalidHexException e) {
			throw new ServalDInterfaceError("field " + fieldName + "='" + value + "' is not a Bundle ID: " + e.getMessage(), this);
		}
	}

	public FileHash getFieldFileHash(String fieldName) throws ServalDInterfaceError {
		String value = getFieldString(fieldName);
		try {
			return new FileHash(value);
		}
		catch (BundleId.InvalidHexException e) {
			throw new ServalDInterfaceError("field " + fieldName + "='" + value + "' is not a file hash: " + e.getMessage(), this);
		}
	}

}
