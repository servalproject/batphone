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
//import org.servalproject.rhizome.RhizomeManifestParseException;
//import org.servalproject.rhizome.RhizomeManifestSizeException;

import android.util.Log;
import android.os.Bundle;

/**
 * Represents a Rhizome manifest, with methods to serialise to/from a byte stream for storage
 * on disk.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public abstract class RhizomeManifest {

	public static class MissingField extends Exception {
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

	protected Bundle mBundle;
	protected byte[] mSignatureBlock;
	private String mService;
	private String mIdHex;
	private Long mDateMillis;
	private Long mVersion;
	private Long mFilesize;
	private String mFilehash;

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
		/* We could check here that the manifest ID matches the first signature block. */
		return fromBundle(b, sigblock);
	}

	/** Construct a Rhizome manifest from a bundle of named fields.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeManifest fromBundle(Bundle b, byte[] signatureBlock) throws RhizomeManifestParseException {
		String service = parseNonEmpty("service", b.getString("service"));
		if (service.equalsIgnoreCase(RhizomeManifest_File.SERVICE))
			return RhizomeManifest_File.fromBundle(b, signatureBlock);
		else if (service.equalsIgnoreCase(RhizomeManifest_MeshMS.SERVICE))
			return RhizomeManifest_MeshMS.fromBundle(b, signatureBlock);
		else
			throw new RhizomeManifestParseException("unsupported service '" + service + "'");
	}

	/** Construct an empty Rhizome manifest.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected RhizomeManifest() {
		mIdHex = null;
		mDateMillis = null;
		mVersion = null;
		mFilesize = null;
		mFilehash = null;
		mBundle = null;
		mSignatureBlock = null;
	}

	/** Construct a Rhizome manifest from an Android Bundle containing various manifest fields.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected RhizomeManifest(Bundle b, byte[] signatureBlock) throws RhizomeManifestParseException {
		this();
		mIdHex = parseSID("id", b.getString("id"));
		mDateMillis = parseULong("date", b.getString("date"));
		mVersion = parseULong("version", b.getString("version"));
		mFilesize = parseULong("filesize", b.getString("filesize"));
		mFilehash = parseFilehash("filehash", b.getString("filehash"));
		mBundle = b;
		mSignatureBlock = signatureBlock;
	}

	/** Helper method for constructors.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static String parseNonEmpty(String fieldName, String text) throws RhizomeManifestParseException {
		if (text == null)
			return null;
		if (text.length() == 0)
			throw new RhizomeManifestParseException("missing '" + fieldName + "' field");
		return text;
	}

	/** Helper method for constructors.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static Long parseLong(String fieldName, String text) throws RhizomeManifestParseException {
		try {
			text = parseNonEmpty(fieldName, text);
			return text == null ? null : new Long(text);
		}
		catch (NumberFormatException e) {
			throw new RhizomeManifestParseException("malformed " + fieldName + " (long): '" + text + "'", e);
		}
	}

	/** Helper method for constructors and setters.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static Long parseULong(String fieldName, String text) throws RhizomeManifestParseException {
		return validateULong(fieldName, parseLong(fieldName, text));
	}

	protected static Long validateULong(String fieldName, Long value) throws RhizomeManifestParseException {
		if (value != null && value < 0)
			throw new RhizomeManifestParseException("invalid " + fieldName + " value: " + value);
		return value;
	}

	/** Helper method for constructors.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static String parseSID(String fieldName, String text) throws RhizomeManifestParseException {
		return validateSID(fieldName, parseNonEmpty(fieldName, text));
	}

	/** Helper method for constructors and setters.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static String validateSID(String fieldName, String value) throws RhizomeManifestParseException {
		if (value != null && !(value.length() == MANIFEST_ID_HEXCHARS && value.matches("\\A\\p{XDigit}+\\z")))
			throw new RhizomeManifestParseException("invalid " + fieldName +" (SID): '" + value + "'");
		return value;
	}

	/** Helper method for constructors.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static String parseFilehash(String fieldName, String text) throws RhizomeManifestParseException {
		return validateFilehash(fieldName, parseNonEmpty(fieldName, text));
	}

	/** Helper method for constructors and setters.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static String validateFilehash(String fieldName, String value) throws RhizomeManifestParseException {
		if (value != null && !(value.length() == FILE_HASH_HEXCHARS && value.matches("\\A\\p{XDigit}+\\z")))
			throw new RhizomeManifestParseException("invalid " + fieldName +" (hash): '" + value + "'");
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
	public byte[] toByteArrayUnsigned() throws RhizomeManifestSizeException {
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
			mBundle.putString("service", getService());
			if (mIdHex != null) mBundle.putString("id", mIdHex);
			if (mDateMillis != null) mBundle.putString("date", "" + mDateMillis);
			if (mVersion != null) mBundle.putString("version", "" + mVersion);
			if (mFilesize != null) mBundle.putString("filesize", "" + mFilesize);
			if (mFilehash != null) mBundle.putString("filehash", "" + mFilehash);
		}
	}

	/** Return the 'service' field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	abstract public String getService();

	/** Helper for getter methods that throw MissingField.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static void missingIfNull(String fieldName, Object value) throws MissingField {
		if (value == null)
			throw new MissingField(fieldName);
	}

	/** Return the manifest ID as a hex-encoded string.
	 * @throws MissingField if the field is not present
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public String getIdHex() throws MissingField {
		missingIfNull("id", mIdHex);
		return mIdHex;
	}

	/** Set the manifest ID to a hex-encoded string or null.
	 * @throws RhizomeManifestParseException if the supplied string is not a hex-encoded SID
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void setIdHex(String id) throws RhizomeManifestParseException {
		mIdHex = validateSID("id", id);
	}

	/** Unset the manifest ID.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void unsetIdHex() {
		mIdHex = null;
	}

	/** Return the 'date' field as an integer milliseconds since epoch.
	 * @throws MissingField if the field is not present
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public long getDateMillis() throws MissingField {
		missingIfNull("date", mDateMillis);
		return mDateMillis;
	}

	/** Set the 'date' field to null (missing) or an integer milliseconds since epoch.
	 * @throws RhizomeManifestParseException if the millisecond value is out of range
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void setDateMillis(Long millis) throws RhizomeManifestParseException {
		mDateMillis = validateULong("date", millis);
	}

	/** Unset the 'date' field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void unsetDateMillis() {
		mDateMillis = null;
	}

	/** Return the 'version' field as an integer.
	 * @throws MissingField if the field is not present
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public long getVersion() throws MissingField {
		missingIfNull("version", mVersion);
		return mVersion;
	}

	/** Set the 'version' field to null (missing) or a non-negative integer.
	 * @throws RhizomeManifestParseException if the version value is negative
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void setVersion(Long value) throws RhizomeManifestParseException {
		mVersion = validateULong("version", value);
	}

	/** Unset the 'version' field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void unsetVersion() {
		mVersion = null;
	}

	/** Return the 'filesize' field as an integer.
	 * @throws MissingField if the field is not present
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public long getFilesize() throws MissingField {
		missingIfNull("filesize", mFilesize);
		return mFilesize;
	}

	/** Set the 'filesize' field to null (missing) or a non-negative integer.
	 * @throws RhizomeManifestParseException if the size value is negative
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void setFilesize(Long size) throws RhizomeManifestParseException {
		mFilesize = validateULong("filesize", size);
	}

	/** Unset the 'filesize' field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void unsetFilesize() {
		mFilesize = null;
	}

	/** Return the 'filehash' field as a String.
	 * @throws MissingField if the field is not present
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public String getFilehash() throws MissingField {
		missingIfNull("filehash", mFilehash);
		return mFilehash;
	}

	/** Set the 'filehash' field to null (missing) or a hex-encoded file hash.
	 * @throws RhizomeManifestParseException if the supplied string is not a hex-encoded file hash
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void setFilehash(String hash) throws RhizomeManifestParseException {
		mFilehash = validateFilehash("filehash", hash);
	}

	/** Unset the 'filehash' field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void unsetFilehash() {
		mFilehash = null;
	}

	/** Return the signature block as an array of bytes.
	 * @throws MissingField if no signature block was present in the source
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public byte[] getSignatureBlock() throws MissingField {
		missingIfNull("signature block", mSignatureBlock);
		return mSignatureBlock;
	}

}
