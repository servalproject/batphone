package org.servalproject.servald;

/**
 * Thrown when a request to a servald JNI method fails.  This typically means that the returned
 * status is non-zero, or some other result was returned that indicated the operation failed.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
class ServalDFailureException extends Exception
{

	public ServalDFailureException(String message) {
		super(message);
	}

	public ServalDFailureException(String message, Throwable cause) {
		super(message, cause);
	}

	public ServalDFailureException(Throwable cause) {
		super(cause);
	}

}
