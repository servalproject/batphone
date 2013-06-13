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

public class SubscriberId extends AbstractId {

	public static final int BINARY_SIZE = 32;

	@Override
	public int getBinarySize() {
		return BINARY_SIZE;
	}

	public SubscriberId(String hex) throws InvalidHexException {
		super(hex);
	}

	public SubscriberId(ByteBuffer b) throws InvalidBinaryException {
		super(b);
	}

	public SubscriberId(byte[] binary) throws InvalidBinaryException {
		super(binary);
	}

	@Override
	public String abbreviation() {
		return "sid:" + Packet.binToHex(this.binary, 6) + "*";
	}

	// get a random sid, purely for testing purposes
	public static SubscriberId randomSid() {
		Random r = new Random();
		byte buff[] = new byte[BINARY_SIZE];
		r.nextBytes(buff);
		try {
			return new SubscriberId(buff);
		}
		catch (InvalidBinaryException e) {
			throw new AssertionError("something is very wrong: " + e);
		}
	}

	/** Return true iff this SID is a broadcast address.
	 *
	 * At the moment, a broadcast address is defined as one whose bits are all 1 except
	 * for the final 64 bits, which could be anything.  This definition may change in
	 * future, so treat this code with a grain of salt.
	 */
	public boolean isBroadcast() {
		for (int i = 0; i < 24; i++)
			if ((0xFF & this.binary[i]) != 0xFF)
				return false;
		return true;
	}

	public static SubscriberId broadcastSid() {
		byte buff[] = new byte[BINARY_SIZE];
		for (int i = 0; i < BINARY_SIZE; i++)
			buff[i] = (byte) 0xff;
		try {
			return new SubscriberId(buff);
		} catch (InvalidBinaryException e) {
			throw new AssertionError("something is very wrong: " + e);
		}

	}
}
