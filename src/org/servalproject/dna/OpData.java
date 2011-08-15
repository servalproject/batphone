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

public class OpData implements Operation {
	VariableRef varRef;
	short varLen;
	ByteBuffer data;

	OpData(){}
	public OpData(VariableRef varRef, short varLen, ByteBuffer data){
		this.varRef=varRef;
		this.varLen=varLen;
		this.data=data;
	}

	static byte getCode(){return (byte)0x82;}

	@Override
	public void parse(ByteBuffer b, byte code) {
		// for some reason we a dumping the variable length into the middle
		VariableType varType=VariableType.getVariableType(b.get());
		byte instance;
		if (varType.hasMultipleValues())
			instance=b.get();
		else
			instance=0;
		varLen=b.getShort();
		short offset=b.getShort();
		short len=b.getShort();

		varRef=new VariableRef(varType, instance, offset, len);
		data=Packet.slice(b, varRef.len);
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.put(varRef.varType.varId);
		if (varRef.varType.hasMultipleValues())
			b.put(varRef.instance);
		b.putShort(varLen);
		b.putShort(varRef.offset);
		b.putShort(varRef.len);
		b.rewind();
		b.put(data);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onData(packet, varRef, varLen, data);
	}

	@Override
	public String toString() {
		return "Data: "+varRef+", "+Packet.binToHex(data);
	}
}
