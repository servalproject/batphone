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
import java.lang.Integer;
import java.lang.Long;

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
	public final String[] outv;
	private HashMap<String,String> keyValue;

	public ServalDResult(String[] args, int status, String[] outv) {
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

	@Override
	public String toString() {
		return this.getClass().getName() + "(args=" + Arrays.deepToString(this.args) + ", status=" + this.status + ", outv=" + Arrays.deepToString(this.outv) + ")";
	}

	public void failIfStatusError() throws ServalDFailureException {
		if (this.status == STATUS_ERROR)
			throw new ServalDFailureException("error exit status", this);
	}

	public void failIfStatusNonzero() throws ServalDFailureException {
		if (this.status != 0)
			throw new ServalDFailureException("non-zero exit status", this);
	}

	protected String getField(String fieldName) throws ServalDInterfaceError {
		if (this.keyValue == null) {
			if (this.outv.length % 2 != 0)
				throw new ServalDInterfaceError("odd number of fields", this);
			this.keyValue = new HashMap<String,String>();
			for (int i = 0; i != this.outv.length; i += 2)
				this.keyValue.put(this.outv[i], this.outv[i + 1]);
		}
		if (!this.keyValue.containsKey(fieldName))
			throw new ServalDInterfaceError("missing '" + fieldName + "' field", this);
		return this.keyValue.get(fieldName);
	}

	public String getFieldString(String fieldName) throws ServalDInterfaceError {
		return getField(fieldName);
	}

	public long getFieldLong(String fieldName) throws ServalDInterfaceError {
		String value = getField(fieldName);
		try {
			return new Long(value);
		}
		catch (NumberFormatException e) {
			throw new ServalDInterfaceError("field " + fieldName + "='" + value + "' is not of type long", this);
		}
	}

	public int getFieldInt(String fieldName) throws ServalDInterfaceError {
		String value = getField(fieldName);
		try {
			return new Integer(value);
		}
		catch (NumberFormatException e) {
			throw new ServalDInterfaceError("field " + fieldName + "='" + value + "' is not of type int", this);
		}
	}

	public BundleId getFieldBundleId(String fieldName) throws ServalDInterfaceError {
		String value = getField(fieldName);
		try {
			return new BundleId(value);
		}
		catch (BundleId.InvalidHexException e) {
			throw new ServalDInterfaceError("field " + fieldName + "='" + value + "' is not a Bundle ID: " + e.getMessage(), this);
		}
	}

}
