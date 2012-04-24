package org.servalproject.rhizome;

/**
 * Represents a Rhizome manifest, with methods to serialise to/from a byte stream for storage on
 * disk.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifestParseException extends Exception {

	private int mOffset;

	/**
	 * Construct an exception which does not correspond to a particular place in the parsed stream.
	 */
	public RhizomeManifestParseException(String message) {
		super(message);
		mOffset = -1;
	}

	/**
	 * Construct an exception which does not correspond to a particular place in the parsed stream,
	 * specifying the cause.
	 */
	public RhizomeManifestParseException(String message, Throwable cause) {
		super(message, cause);
		mOffset = -1;
	}

	/**
	 * Construct an exception that identifies the position in the parsed stream that provoked it.
	 */
	public RhizomeManifestParseException(String message, int offset) {
		super(message);
		mOffset = offset;
	}

	/**
	 * Construct an exception that identifies the position in the parsed stream that provoked it
	 * and the specified cause.
	 */
	public RhizomeManifestParseException(String message, int offset, Throwable cause) {
		super(message, cause);
		mOffset = offset;
	}

	/**
	 * Return the position in the parsed stream where the error occurred.  If the exception was not
	 * related to any part of the stream, then this will return -1.
	 */
	public int getOffset() {
		return mOffset;
	}

}
