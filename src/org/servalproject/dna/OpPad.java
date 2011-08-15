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

package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpPad implements Operation {

	static byte getCode(){return (byte)0xfe;}

	@Override
	public void parse(ByteBuffer b, byte code) {
		int len = (b.get())&0xff;
		b.position(b.position()+len);
	}

	@Override
	public void write(ByteBuffer b) {
		int len=Packet.rand.nextInt(16);
		if (len==0) return;

		b.put(getCode());
		b.put((byte)len);
		byte padding[]=new byte[len];
		Packet.rand.nextBytes(padding);
		b.put(padding);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {return false;}

	@Override
	public String toString() {
		return "Padding";
	}
}
