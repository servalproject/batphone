package org.servalproject.servald;

/**
 * Indicates an internal (coding) error in the JNI interface to servald.  Typically encountered when
 * unpacking the outv strings returned by a servald operation, and indicates that the C code in
 * servald that constructs the outv array is not consistent with the Java code that unpacks the outv
 * strings.
 */
public class ServalDInterfaceError extends Error
{

	public ServalDInterfaceError(String message, ServalDResult result) {
		super(message + ": " + result);
	}

	public ServalDInterfaceError(String message, ServalDResult result, Throwable cause) {
		super(message + ": " + result, cause);
	}

	public ServalDInterfaceError(ServalDResult result, Throwable cause) {
		super("" + result, cause);
	}

}
