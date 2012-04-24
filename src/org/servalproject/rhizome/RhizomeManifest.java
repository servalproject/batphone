package org.servalproject.rhizome;

import java.util.Properties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.text.DateFormat;
import org.servalproject.rhizome.RhizomeManifestParseException;
import org.servalproject.rhizome.RhizomeManifestSizeException;

/**
 * Represents a Rhizome manifest, with methods to serialise to/from a byte stream for storage
 * on disk.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifest {

	public final int RHIZOME_BAR_BYTES = 32;
	public final int MAX_MANIFEST_VARS = 256;
	public final int MAX_MANIFEST_BYTES = 8192;

	private Properties mProperties;

	/**
	 * Construct a Rhizome manifest from its byte-stream representation.
	 */
	public RhizomeManifest(byte[] bytes) throws RhizomeManifestParseException {
		if (bytes.length > MAX_MANIFEST_BYTES) {
			throw new RhizomeManifestParseException("manifest too long", MAX_MANIFEST_BYTES);
		}
		mProperties = new Properties();
		try {
			mProperties.load(new ByteArrayInputStream(bytes));
		}
		catch (IllegalArgumentException e) {
			throw new RhizomeManifestParseException("malformed manifest" + (e.getMessage() == null ? "" : ": " + e.getMessage()), e);
		}
		catch (IOException e) {
			// should not happen with ByteArrayInputStream
		}
		String value;
		if ((value = mProperties.getProperty("name")) == null || value.length() == 0) {
			throw new RhizomeManifestParseException("malformed manifest: missing name variable");
		}
		if ((value = mProperties.getProperty("date")) == null || value.length() == 0) {
			throw new RhizomeManifestParseException("malformed manifest: missing date variable");
		}
		try {
			long date = Long.parseLong(value);
			if (date < 0) {
				throw new RhizomeManifestParseException("malformed manifest: illegal date value " + date);
			}
		}
		catch (NumberFormatException e) {
			throw new RhizomeManifestParseException("malformed manifest: illegal date value '" + value + '"', e);
		}
		if ((value = mProperties.getProperty("id")) == null || value.length() == 0) {
			throw new RhizomeManifestParseException("malformed manifest: missing id variable");
		}
		/* If the NACL library were available in Java, then we could extract the signature blocks
		 * and check here that the id variable matches the first signature block. */
	}

	/**
	 * Convert a Rhizome manifest to its byte-stream representation.
	 */
	public byte[] getBytes() throws RhizomeManifestSizeException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
		String comments = "#Rhizome manifest data for " + getName() + "\n#" + df.format(new Date(getDate())) + "\n";
		try {
			mProperties.store(os, comments);
		}
		catch (IOException e) {
			// should not happen with ByteArrayOutputStream
		}
		if (os.size() > MAX_MANIFEST_BYTES) {
			throw new RhizomeManifestSizeException("manifest too long", os.size(), MAX_MANIFEST_BYTES);
		}
		return os.toByteArray();
	}

	public String getId() {
		return mProperties.getProperty("id");
	}

	public String getName() {
		return mProperties.getProperty("name");
	}

	public long getDate() {
		return Long.parseLong(mProperties.getProperty("date"));
	}

}
