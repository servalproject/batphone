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

package org.servalproject.rhizome;

import java.util.Properties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.util.Date;
import java.text.DateFormat;
import org.servalproject.rhizome.RhizomeManifestParseException;
import org.servalproject.rhizome.RhizomeManifestSizeException;

import android.util.Log;
import android.os.Bundle;

/**
 * Represents a Rhizome manifest, with methods to serialise to/from a byte stream for storage
 * on disk.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifest {

	public class MissingField extends Exception {
		final public String fieldName;
		private MissingField(String fieldName) {
			super(fieldName);
			this.fieldName = fieldName;
		}
	}

	public final static int RHIZOME_BAR_BYTES = 32;
	public final static int MAX_MANIFEST_VARS = 256;
	public final static int MAX_MANIFEST_BYTES = 8192;
	public final static int MANIFEST_ID_BYTES = 32;
	public final static int MANIFEST_ID_HEXCHARS = MANIFEST_ID_BYTES * 2;
	public final static int FILE_HASH_BYTES = 64;
	public final static int FILE_HASH_HEXCHARS = FILE_HASH_BYTES * 2;

	private Bundle mBundle;
	private byte[] mSignatureBlock;
	private String mIdHex;
	private String mName;
	private long mDateMillis;
	private long mVersion;
	private long mFilesize;

	/** Construct a Rhizome manifest from its byte-stream representation.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeManifest fromByteArray(byte[] bytes) throws RhizomeManifestParseException {
		// The signature block follows the first nul character at the start of a line.
		byte[] sigblock = null;
		int proplen = bytes.length;
		for (int i = 0; i != bytes.length; ++i) {
			if (bytes[i] == 0 && (i == 0 || bytes[i-1] == '\n')) {
				sigblock = new byte[bytes.length - i - 1];
				System.arraycopy(bytes, i + 1, sigblock, 0, sigblock.length);
				proplen = i;
				break;
			}
		}
		Properties prop = new Properties();
		try {
			prop.load(new ByteArrayInputStream(bytes, 0, proplen));
		}
		catch (IllegalArgumentException e) {
			throw new RhizomeManifestParseException("malformed manifest" + (e.getMessage() == null ? "" : ": " + e.getMessage()), e);
		}
		catch (IOException e) {
			// should not happen with ByteArrayInputStream
		}
		Bundle b = new Bundle();
		for (Enumeration e = prop.propertyNames(); e.hasMoreElements(); ) {
			String propName = (String) e.nextElement();
			b.putString(propName, prop.getProperty(propName));
		}
		/* If the NACL library were available in Java, then we could check here that the manifest ID
		 * matches the first signature block. */
		return new RhizomeManifest(b, sigblock);
	}

	/** Construct a Rhizome manifest from an Android Bundle containing various manifest fields.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public RhizomeManifest(Bundle b, byte[] signatureBlock) throws RhizomeManifestParseException {
		this(b.getString("id"), b.getString("name"), b.getString("date"), b.getString("version"), b.getString("filesize"));
		mBundle = b;
		mSignatureBlock = signatureBlock;
	}

	/** Construct a Rhizome manifest from minimal required field values.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public RhizomeManifest(String id, String name, String date, String version, String filesize) throws RhizomeManifestParseException {
		mIdHex = parseNonEmpty("id", id);
		if (!(mIdHex.length() == MANIFEST_ID_HEXCHARS && mIdHex.matches("\\A\\p{XDigit}+\\z")))
			throw new RhizomeManifestParseException("illegal id '" + mIdHex + "'");
		mName = parseNonEmpty("name", name);
		mDateMillis = parseULong("date", date);
		mVersion = parseULong("version", version);
		mFilesize = parseULong("filesize", filesize);
		mBundle = null;
		mSignatureBlock = null;
	}

	/** Helper method for constructors.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	private static String parseNonEmpty(String fieldName, String text) throws RhizomeManifestParseException {
		if (text == null || text.length() == 0)
			throw new RhizomeManifestParseException("missing '" + fieldName + "' field");
		return text;
	}

	/** Helper method for constructors.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	private static long parseLong(String fieldName, String text) throws RhizomeManifestParseException {
		try {
			return Long.parseLong(parseNonEmpty(fieldName, text));
		}
		catch (NumberFormatException e) {
			throw new RhizomeManifestParseException("illegal " + fieldName + " value '" + text + "'", e);
		}
	}

	/** Helper method for constructors.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	private static long parseULong(String fieldName, String text) throws RhizomeManifestParseException {
		long value = parseLong(fieldName, text);
		if (value < 0)
			throw new RhizomeManifestParseException("illegal " + fieldName + ": " + value);
		return value;
	}

	/** Convert a Rhizome manifest to a byte-stream representation, without a signature block.  This
	 * isn't actually the canonical form, because it adds a date comment and does not sort the
	 * properties by name.  It will do for now.
	 *
	 * If the NACL library were available in Java, could also provide a getBytesSigned() method that
	 * produced a signature block.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public byte[] asBytesUnsigned() throws RhizomeManifestSizeException {
		makeBundle();
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(os);
			ArrayList<String> propNames = new ArrayList<String>(mBundle.size());
			for (String propName: mBundle.keySet())
				propNames.add(propName);
			Collections.sort(propNames);
			for (String propName: propNames) {
				osw.write(propName, 0, propName.length());
				osw.write("=", 0, 1);
				String value = mBundle.getString(propName);
				osw.write(value, 0, value.length());
				osw.write("\n", 0, 1);
			}
			if (os.size() > MAX_MANIFEST_BYTES)
				throw new RhizomeManifestSizeException("manifest too long", os.size(), MAX_MANIFEST_BYTES);
			return os.toByteArray();
		}
		catch (IOException e) {
			// should not happen with ByteArrayOutputStream
			return new byte[0];
		}
	}

	/** Return a Bundle representing all the fields in the manifest.  If passed to the Bundle
	 * constructor, will reproduce an identical RhizomeManifest object but without any signature
	 * block.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public Bundle asBundle() {
		makeBundle();
		// Return a copy so the caller can't reach through it to modify this manifest.
		return new Bundle(mBundle);
	}

	protected void makeBundle() {
		if (mBundle == null) {
			mBundle = new Bundle();
			mBundle.putString("id", mIdHex);
			mBundle.putString("name", mName);
			mBundle.putString("date", "" + mDateMillis);
			mBundle.putString("version", "" + mVersion);
			mBundle.putString("filesize", "" + mFilesize);
		}
	}

	/** Return the manifest ID as a hex-encoded string.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public String getIdHex() {
		return mIdHex;
	}

	/** Return the 'name' field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public String getName() {
		return mName;
	}

	/** Return the 'date' field as an integer milliseconds since epoch.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public long getDateMillis() {
		return mDateMillis;
	}

	/** Return the 'version' field as an integer.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public long getVersion() {
		return mVersion;
	}

	/** Return the 'filesize' field as an integer.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public long getFilesize() {
		return mFilesize;
	}

	/** Return the signature block as an array of bytes.
	 * @throw MissingField if no signature block was present in the source
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public byte[] getSignatureBlock() throws MissingField {
		if (mSignatureBlock == null)
			throw new MissingField("signature block");
		return mSignatureBlock;
	}

}
