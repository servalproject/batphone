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

public abstract class AbstractId {

	abstract int getBinarySize();

	public static class InvalidHexException extends Exception {
		private static final long serialVersionUID = 1L;

		private InvalidHexException(AbstractId id, String message) {
			super(id.getClass().getName() + ": " + message);
		}
	}

	public static class InvalidBinaryException extends Exception {
		private static final long serialVersionUID = 1L;

		private InvalidBinaryException(AbstractId id, String message) {
			super(id.getClass().getName() + ": " + message);
		}
	}

	public final byte[] binary;

	public AbstractId(String hex) throws InvalidHexException {
		this.binary = new byte[getBinarySize()];
		try {
			Packet.hexToBin(hex, this.binary);
		}
		catch (Packet.HexDecodeException e) {
			throw new InvalidHexException(this, e.getMessage());
		}
	}

	public AbstractId(ByteBuffer b) throws InvalidBinaryException {
		this.binary = new byte[getBinarySize()];
		try {
			b.get(this.binary);
		}
		catch (BufferUnderflowException e) {
			throw new InvalidBinaryException(this, "not enough bytes (expecting " + getBinarySize() + ")");
		}
	}

	public AbstractId(byte[] binary) throws InvalidBinaryException {
		if (binary.length != getBinarySize())
			throw new InvalidBinaryException(this, "invalid number of bytes (" + binary.length + "), should be " + getBinarySize());
		this.binary = binary;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof AbstractId) {
			AbstractId obinary = (AbstractId) other;
			for (int i = 0; i < this.binary.length; i++)
				if (this.binary[i] != obinary.binary[i])
					return false;
			return true;
		}
		return false;
	}

	@Override
	public int hashCode() {
		int hashCode = 0;
		for (int i = 0; i < this.binary.length; i++)
			hashCode = (hashCode << 8 | hashCode >>> 24) ^ this.binary[i];
		return hashCode;
	}

	public byte[] toByteArray() {
		return this.binary;
	}

	public String abbreviation() {
		return Packet.binToHex(binary, 4).toUpperCase();
	}

	public String toHex() {
		return Packet.binToHex(this.binary).toUpperCase();
	}

	@Override
	public String toString() {
		return toHex();
	}

}
