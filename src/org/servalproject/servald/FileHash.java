/**
 * Copyright (C) 2012 Serval Project, Inc.
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

import java.nio.ByteBuffer;
import java.util.Random;

public class FileHash extends AbstractId {

	public static final int BINARY_SIZE = 64;

	@Override
	public int getBinarySize() {
		return BINARY_SIZE;
	}

	public FileHash(String hex) throws InvalidHexException {
		super(hex);
	}

	public FileHash(ByteBuffer b) throws InvalidBinaryException {
		super(b);
	}

	public FileHash(byte[] binary) throws InvalidBinaryException {
		super(binary);
	}

	// get a random sid, purely for testing purposes
	public static FileHash randomFileHash() {
		Random r = new Random();
		byte buff[] = new byte[BINARY_SIZE];
		r.nextBytes(buff);
		try {
			return new FileHash(buff);
		}
		catch (InvalidBinaryException e) {
			throw new AssertionError("something is very wrong: " + e);
		}
	}

}
