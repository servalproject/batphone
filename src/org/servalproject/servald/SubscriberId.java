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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Random;

public class SubscriberId {

	public static final int BINARY_LENGTH = 32;
	public static final int HEX_LENGTH = 64;

	public static class InvalidHexException extends Exception {
		private InvalidHexException(String message) {
			super(message);
		}
	}

	public static class InvalidBinaryException extends Exception {
		private InvalidBinaryException(String message) {
			super(message);
		}
	}

	private byte[] sid;

	public SubscriberId(String sidHex) throws InvalidHexException {
		this.sid = new byte[BINARY_LENGTH];
		try {
			Packet.hexToBin(sidHex, this.sid);
		}
		catch (Packet.HexDecodeException e) {
			throw new InvalidHexException(e.getMessage());
		}
	}

	public SubscriberId(ByteBuffer b) throws InvalidBinaryException {
		sid = new byte[BINARY_LENGTH];
		try {
			b.get(sid);
		}
		catch (BufferUnderflowException e) {
			throw new InvalidBinaryException("not enough bytes (expecting " + BINARY_LENGTH + ")");
		}
	}

	public SubscriberId(byte[] sidBin) throws InvalidBinaryException {
		if (sidBin.length != BINARY_LENGTH)
			throw new InvalidBinaryException("invalid number of bytes (" + sidBin.length + "), should be " + BINARY_LENGTH);
		this.sid = sidBin;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof SubscriberId) {
			SubscriberId osid = (SubscriberId) other;
			for (int i = 0; i < this.sid.length; i++)
				if (this.sid[i] != osid.sid[i])
					return false;
			return true;
		}
		return false;
	}

	@Override
	public int hashCode() {
		int hashCode = 0;
		for (int i = 0; i < this.sid.length; i++) {
			hashCode = (hashCode << 8 | hashCode >>> 24) ^ sid[i];
		}
		return hashCode;
	}

	public byte[] toByteArray() {
		return this.sid;
	}

	public String toHex() {
		return Packet.binToHex(this.sid);
	}

	public String abbreviation() {
		return "sid:" + Packet.binToHex(this.sid, 4) + "*";
	}

	@Override
	public String toString() {
		return toHex();
	}

	// get a random sid, purely for testing purposes
	public static SubscriberId randomSid() throws InvalidBinaryException {
		Random r = new Random();
		byte buff[] = new byte[32];
		r.nextBytes(buff);
		return new SubscriberId(buff);
	}

	public boolean isBroadcast() {
		for (int i = 0; i < 24; i++) {
			if ((0xFF & this.sid[i]) != 0xFF)
				return false;
		}
		return true;
	}
}
