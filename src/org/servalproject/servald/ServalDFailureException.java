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
 * Thrown when a request to a servald JNI method fails.  This typically means that the returned
 * status is non-zero, or some other result was returned that indicated the operation failed.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class ServalDFailureException extends Exception
{
	private static final long serialVersionUID = 1L;

	public ServalDFailureException(String message, ServalDResult result) {
		super(message + " for command: " + result.getCommandString());
	}

	public ServalDFailureException(String message) {
		super(message);
	}

}
