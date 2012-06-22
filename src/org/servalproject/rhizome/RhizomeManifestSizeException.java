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

import java.io.File;

/**
 * Thrown when a Rhizome manifest is too long to fit in a limited-size byte stream.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifestSizeException extends Exception {
	private static final long serialVersionUID = 1L;
	private long mSize;
	private long mMaxSize;

	public RhizomeManifestSizeException(String message, long size, long maxSize) {
		super(message + " (" + size + "bytes exceeds " + maxSize + ")");
		mSize = size;
		mMaxSize = maxSize;
	}

	public RhizomeManifestSizeException(File manifestFile, long maxSize) {
		this(manifestFile.toString(), manifestFile.length(), maxSize);
	}

}
