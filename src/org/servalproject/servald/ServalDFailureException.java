package org.servalproject.servald;

/**
 * Thrown when a request to a servald JNI method fails.  This typically means that the returned
 * status is non-zero, or some other result was returned that indicated the operation failed.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class ServalDFailureException extends Exception
{

	public ServalDFailureException(String message, ServalDResult result) {
		super(message + ": " + result);
	}

	public ServalDFailureException(ServalDResult result) {
		super("" + result);
	}

}
