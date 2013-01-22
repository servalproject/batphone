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

package org.servalproject.servald;

/**
 * Indicates an internal (coding) error in the JNI interface to servald.  Typically encountered when
 * unpacking the outv strings returned by a servald operation, and indicates that the C code in
 * servald that constructs the outv array is not consistent with the Java code that unpacks the outv
 * strings.
 */
public class ServalDInterfaceError extends Error
{
	private static final long serialVersionUID = 1L;

	public ServalDInterfaceError(String message, ServalDResult result) {
		super(message + ": " + result);
	}

	public ServalDInterfaceError(String message, ServalDResult result, Throwable cause) {
		super(message + ": " + result, cause);
	}

	public ServalDInterfaceError(ServalDResult result, Throwable cause) {
		super("" + result, cause);
	}

	public ServalDInterfaceError(String message, Throwable cause) {
		super(message, cause);
	}
}
