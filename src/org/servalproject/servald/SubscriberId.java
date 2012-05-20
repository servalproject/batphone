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
		if (sidHex.length() != HEX_LENGTH)
			throw new InvalidHexException("invalid length (" + sidHex.length() + "), should be " + HEX_LENGTH);
		this.sid = new byte[BINARY_LENGTH];
		int j = 0;
		for (int i = 0; i != this.sid.length; i++) {
			int d1 = Character.digit(sidHex.charAt(j++), 16);
			int d2 = Character.digit(sidHex.charAt(j++), 16);
			if (d1 == -1 || d2 == -1)
				throw new InvalidHexException("contains non-hex character");
			this.sid[i] = (byte) ((d1 << 4) | d2);
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

}
