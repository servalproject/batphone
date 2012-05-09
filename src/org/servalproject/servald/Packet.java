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

import java.nio.ByteBuffer;
import java.util.Random;

public class Packet {

	private final static int SID_SIZE = 32;

	static Random rand = new Random();

	static ByteBuffer slice(ByteBuffer b, int len) {
		int oldLimit = b.limit();
		int newPos = b.position() + len;
		b.limit(newPos);
		ByteBuffer ret = b.slice();
		b.limit(oldLimit);
		b.position(newPos);
		return ret;
	}

	static String binToHex(byte[] buff) {
		return binToHex(buff, 0, buff.length);
	}

	static String binToHex(byte[] buff, int len) {
		return binToHex(buff, 0, len);
	}

	static String binToHex(ByteBuffer b) {
		return binToHex(b.array(), b.arrayOffset() + b.position(),
				b.remaining());
	}

	static String binToHex(byte[] buff, int offset, int len) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < len; i++) {
			sb.append(Character.forDigit(((buff[i + offset]) & 0xf0) >> 4, 16));
			sb.append(Character.forDigit((buff[i + offset]) & 0x0f, 16));
		}
		return sb.toString();
	}

}
