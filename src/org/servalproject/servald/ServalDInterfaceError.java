package org.servalproject.servald;

/**
 * Indicates an internal (coding) error in the JNI interface to servald.  Typically encountered when
 * unpacking the outv strings returned by a servald operation, and indicates that the C code in
 * servald that constructs the outv array is not consistent with the Java code that unpacks the outv
 * strings.
 */
class ServalDInterfaceError extends Error
{

	public ServalDInterfaceError(String message) {
		super(message);
	}

	public ServalDInterfaceError(String message, Throwable cause) {
		super(message, cause);
	}

	public ServalDInterfaceError(Throwable cause) {
		super(cause);
	}

}
