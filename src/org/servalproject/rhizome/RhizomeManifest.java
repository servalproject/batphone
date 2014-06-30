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

import android.os.Bundle;

import org.servalproject.servaldna.BundleId;
import org.servalproject.servaldna.BundleKey;
import org.servalproject.servaldna.FileHash;
import org.servalproject.servaldna.SubscriberId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Represents a Rhizome manifest, with methods to serialise to/from a byte stream for storage
 * on disk.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifest implements Cloneable {

	public static class MissingField extends Exception {
		private static final long serialVersionUID = 1L;
		final public String fieldName;
		private MissingField(String fieldName) {
			super(fieldName);
			this.fieldName = fieldName;
		}
	}

	public final static int RHIZOME_BAR_BYTES = 32;
	public final static int MAX_MANIFEST_VARS = 256;
	public final static int MAX_MANIFEST_BYTES = 8192;
	public final static int FILE_HASH_BYTES = 64;
	public final static int FILE_HASH_HEXCHARS = FILE_HASH_BYTES * 2;
	private final static String TAG="RhizomeManifest";
	protected Bundle mBundle;
	protected byte[] mSignatureBlock;
	protected final String mService;
	protected BundleId mManifestId;
	protected Long mDateMillis;
	protected Long mVersion;
	protected Long mFilesize;
	protected FileHash mFilehash;
	protected BundleKey mBundleKey;
	protected Long mCrypt;

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
		for (Enumeration<?> e = prop.propertyNames(); e.hasMoreElements();) {
			String propName = (String) e.nextElement();
			if (propName.startsWith("."))
				throw new RhizomeManifestParseException("malformed manifest: illegal property name \"" + propName + "\"");
			b.putString(propName, prop.getProperty(propName));
		}
		/* We could check here that the manifest ID matches the first signature block. */
		return fromBundle(b, sigblock);
	}

	/** Helper function to read a manifest from a file.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeManifest readFromFile(File manifestFile)
		throws IOException, RhizomeManifestSizeException, RhizomeManifestParseException, RhizomeManifestServiceException
	{
		long length = manifestFile.length();
		if (length > RhizomeManifest.MAX_MANIFEST_BYTES)
			throw new RhizomeManifestSizeException(manifestFile, RhizomeManifest.MAX_MANIFEST_BYTES);
		FileInputStream fis = new FileInputStream(manifestFile);
		try {
			byte[] content = new byte[(int) length];
			int offset = 0;
			while (offset < length) {
				int n = fis.read(content, offset, (int) (length - offset));
				if (n < 0)
					throw new EOFException();
				offset += n;
			}
			return fromByteArray(content);
		}
		finally {
			fis.close();
		}
	}

	/** Construct a Rhizome manifest from a bundle of named fields.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeManifest fromBundle(Bundle b, byte[] signatureBlock) throws RhizomeManifestParseException {
		String service = parseNonEmpty("service", b.getString("service"));
		if (service == null)
			throw new RhizomeManifestParseException("missing 'service' field");
		if (service.equalsIgnoreCase(RhizomeManifest_File.SERVICE))
			return new RhizomeManifest_File(b, signatureBlock);
		else if (service.equalsIgnoreCase(RhizomeManifest_MeshMS.SERVICE)
				||service.equalsIgnoreCase(RhizomeManifest_MeshMS.OLD_SERVICE))
			return new RhizomeManifest_MeshMS(b, signatureBlock);
		else
			return new RhizomeManifest(b, signatureBlock);
	}

	@Override
	public RhizomeManifest clone() throws CloneNotSupportedException {
		makeBundle();
		try {
			return fromBundle(mBundle, mSignatureBlock);
		}
		catch (RhizomeManifestParseException e) {
			throw new CloneNotSupportedException("cannot clone manifest: " + e.getMessage());
		}
	}

	/** Construct an empty Rhizome manifest.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected RhizomeManifest(String service) {
		this.mService=service;
		mManifestId = null;
		mDateMillis = null;
		mVersion = null;
		mFilesize = null;
		mFilehash = null;
		mBundleKey = null;
		mBundle = null;
		mSignatureBlock = null;
		mCrypt = null;
	}

	/** Construct a Rhizome manifest from an Android Bundle containing various manifest fields.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected RhizomeManifest(Bundle b, byte[] signatureBlock) throws RhizomeManifestParseException {
		this(b.getString("service"));
		mManifestId = parseBID("id", b.getString("id"));
		mDateMillis = parseULong("date", b.getString("date"));
		mVersion = parseULong("version", b.getString("version"));
		mFilesize = parseULong("filesize", b.getString("filesize"));
		mCrypt = parseULong("crypt", b.getString("crypt"));
		mFilehash = (mFilesize != null && mFilesize != 0) ? parseFilehash("filehash", b.getString("filehash")) : null;
		String bk = b.getString("BK");
		if (bk != null)
			mBundleKey = parseBK("BK", bk);
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
	protected static BundleId parseBID(String fieldName, String text) throws RhizomeManifestParseException {
		return validateBID(fieldName, parseNonEmpty(fieldName, text));
	}

	/** Helper method for constructors and setters.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static BundleId validateBID(String fieldName, String value) throws RhizomeManifestParseException {
		try {
			return new BundleId(value);
		}
		catch (BundleId.InvalidHexException e) {
			throw new RhizomeManifestParseException("invalid " + fieldName +" (BID): '" + value + "'", e);
		}
	}

	/** Helper method for constructors.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static SubscriberId parseSID(String fieldName, String text) throws RhizomeManifestParseException {
		return validateSID(fieldName, parseNonEmpty(fieldName, text));
	}

	/** Helper method for constructors and setters.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static SubscriberId validateSID(String fieldName, String value) throws RhizomeManifestParseException {
		try {
			return value == null ? null : new SubscriberId(value);
		}
		catch (SubscriberId.InvalidHexException e) {
			throw new RhizomeManifestParseException("invalid " + fieldName +" (SID): '" + value + "'", e);
		}
	}

	/** Helper method for constructors.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static FileHash parseFilehash(String fieldName, String text) throws RhizomeManifestParseException {
		return validateFilehash(fieldName, parseNonEmpty(fieldName, text));
	}

	/** Helper method for constructors and setters.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static FileHash validateFilehash(String fieldName, String value) throws RhizomeManifestParseException {
		try {
			return value == null ? null : new FileHash(value);
		}
		catch (FileHash.InvalidHexException e) {
			throw new RhizomeManifestParseException("invalid " + fieldName +" (hash): '" + value + "'", e);
		}
	}

	/** Helper method for constructors.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static BundleKey parseBK(String fieldName, String text) throws RhizomeManifestParseException {
		return validateBK(fieldName, parseNonEmpty(fieldName, text));
	}

	/** Helper method for constructors and setters.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static BundleKey validateBK(String fieldName, String value) throws RhizomeManifestParseException {
		try {
			return new BundleKey(value);
		}
		catch (BundleId.InvalidHexException e) {
			throw new RhizomeManifestParseException("invalid " + fieldName +" (BK): '" + value + "'", e);
		}
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
				if (!propName.startsWith("."))
					propNames.add(propName);
			Collections.sort(propNames);
			for (String propName: propNames) {
				String value = mBundle.getString(propName);
				if (value != null) {
					osw.write(propName, 0, propName.length());
					osw.write("=", 0, 1);
					osw.write(value, 0, value.length());
					osw.write("\n", 0, 1);
				}
			}
			osw.close();
			if (os.size() > MAX_MANIFEST_BYTES)
				throw new RhizomeManifestSizeException("manifest too long", os.size(), MAX_MANIFEST_BYTES);
			return os.toByteArray();
		}
		catch (IOException e) {
			// should not happen with ByteArrayOutputStream
			return new byte[0];
		}
	}

	public void writeTo(File manifestFile) throws RhizomeManifestSizeException, IOException {
		byte content[] = toByteArrayUnsigned();
		FileOutputStream fos = new FileOutputStream(manifestFile);
		try {
			fos.write(content);
		} finally {
			fos.close();
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
		if (mBundle == null)
			mBundle = new Bundle();
		mBundle.putString("service", this.mService);
		mBundle.putString("id", mManifestId == null ? null : mManifestId.toHex().toUpperCase());
		mBundle.putString("date", mDateMillis == null ? null : "" + mDateMillis);
		mBundle.putString("version", mVersion == null ? null : "" + mVersion);
		mBundle.putString("filesize", mFilesize == null ? null : "" + mFilesize);
		mBundle.putString("filehash", mFilehash == null ? null : "" + mFilehash);
		mBundle.putString("BK", mBundleKey == null ? null : "" + mBundleKey);
		mBundle.putString("crypt", mCrypt == null ? null : "" + mCrypt);
	}

	@Override
	public String toString() {
		makeBundle();
		StringBuffer b = new StringBuffer();
		b.append(getClass().getName());
		b.append("(");
		boolean first = true;
		for (String propName: mBundle.keySet()) {
			String value = mBundle.getString(propName);
			if (value != null) {
				if (!first)
					b.append(", ");
				b.append(propName);
				b.append("=");
				b.append(value);
				first = false;
			}
		}
		b.append(")");
		return b.toString();
	}

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
	public BundleId getManifestId() throws MissingField {
		missingIfNull("id", mManifestId);
		return mManifestId;
	}

	/** Set the manifest ID to a valid bundle Id or null.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void setManifestId(BundleId id) {
		mManifestId = id;
	}

	/** Set the manifest ID (aka Bundle ID) to a hex-encoded string or null.
	 * @throws RhizomeManifestParseException if the supplied string is not a hex-encoded SID
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void setManifestIdHex(String id) throws RhizomeManifestParseException {
		mManifestId = validateBID("id", id);
	}

	/** Unset the manifest ID.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void unsetManifestId() {
		mManifestId = null;
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

	public String getDisplayName() {
		return (mManifestId == null ? "null" : mManifestId.abbreviation())
				+ " - " + (mVersion == null ? "null" : mVersion);
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
	public long getFilesize() {
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

	public long getCrypt() throws MissingField {
		missingIfNull("crypt", mCrypt);
		return mCrypt;
	}

	public void setCrypt(long crypt) {
		this.mCrypt = crypt;
	}

	public void unsetCrypt() {
		mCrypt = null;
	}

	/** Return the 'filehash' field as a String.
	 * @throws MissingField if the field is not present
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public FileHash getFilehash() throws MissingField {
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

	/** Return the 'BK' field as a String.
	 * @throws MissingField if the field is not present
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public BundleKey getBundleKey() throws MissingField {
		missingIfNull("BK", mBundleKey);
		return mBundleKey;
	}

	/** Set the 'BK' field to a valid bundle key or null.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void setBundleKey(BundleKey key) {
		mBundleKey = key;
	}

	/** Set the 'filehash' field to null (missing) or a hex-encoded file hash.
	 * @throws RhizomeManifestParseException if the supplied string is not a hex-encoded file hash
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void setBundleKey(String key) throws RhizomeManifestParseException {
		mBundleKey = validateBK("BK", key);
	}

	/** Unset the 'BK' field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void unsetBundleKey() {
		mBundleKey = null;
	}

	/** Return the signature block as an array of bytes.
	 * @throws MissingField if no signature block was present in the source
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public byte[] getSignatureBlock() throws MissingField {
		missingIfNull("signature block", mSignatureBlock);
		return mSignatureBlock;
	}

	public void setField(String name, String value) throws RhizomeManifestParseException {
		if (name.equalsIgnoreCase("id"))
			setManifestId(parseBID("id", value));
		else if (name.equalsIgnoreCase("date"))
			setDateMillis(parseULong("date", value));
		else if (name.equalsIgnoreCase("version"))
			setVersion(parseULong("version", value));
		else if (name.equalsIgnoreCase("filesize"))
			setFilesize(parseULong("filesize", value));
		else if (name.equalsIgnoreCase("crypt"))
			setCrypt(parseULong("crypt", value));
		else if (name.equalsIgnoreCase("filehash"))
			setFilehash(value);
		else if (name.equalsIgnoreCase("BK"))
			setBundleKey(value);
		else
			mBundle.putString(name, value);
	}
	public String getMimeType(){
		return "application/binary";
	}
}
