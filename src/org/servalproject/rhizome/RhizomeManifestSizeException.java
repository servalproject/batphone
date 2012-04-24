package org.servalproject.rhizome;

/**
 * Thrown when a Rhizome manifest is too long to fit in a limited-size byte stream.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifestSizeException extends Exception {

	private long mSize;
	private long mMaxSize;

	/**
	 * Construct an exception which does not correspond to a particular place in the parsed stream.
	 */
	public RhizomeManifestSizeException(String message, long size, long maxSize) {
		super(message + "(" + size + "bytes exceeds " + maxSize + ")");
		mSize = size;
		mMaxSize = maxSize;
	}

}
