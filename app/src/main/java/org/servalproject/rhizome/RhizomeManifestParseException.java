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

/**
 * Represents a Rhizome manifest, with methods to serialise to/from a byte stream for storage on
 * disk.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifestParseException extends Exception {
	private static final long serialVersionUID = 1L;
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
