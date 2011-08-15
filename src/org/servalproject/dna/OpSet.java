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
import java.util.HashMap;
import java.util.Map;

public class OpSet implements Operation {
	VariableRef varRef;
	Flag flag;
	ByteBuffer value=null;

	enum Flag{
		None((byte)0),
		NoReplace((byte)1),
		Replace((byte)2),
		NoCreate((byte)3),
		Fragment((byte)0x80);

		byte code;
		Flag(byte code){
			this.code=code;
		}
	}

	private static Map<Byte, Flag> flags=new HashMap<Byte, Flag>();
	static{
		for (Flag f:Flag.values()){
			flags.put(f.code, f);
		}
	}
	OpSet(){}
	public OpSet(VariableType varType, byte instance, short offset, Flag flag, ByteBuffer value){
		if (varType.hasMultipleValues()&&instance==(byte)-1)
			throw new IllegalArgumentException("You must specify an instance for variable "+varType.name());
		this.varRef=new VariableRef(varType,instance,offset,(short)value.remaining());
		this.flag=flag;
		this.value=value;
	}

	static byte getCode(){return (byte)0x01;}

	@Override
	public void parse(ByteBuffer b, byte code) {
		this.varRef=new VariableRef(b);
		this.flag=flags.get(b.get());
		this.value=Packet.slice(b,varRef.len & 0xffff);
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		varRef.write(b);
		b.put(this.flag.code);
		this.value.rewind();
		b.put(this.value);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onSet(packet, varRef, flag, value);
	}

	@Override
	public String toString() {
		return "Set: "+varRef+", "+flag.name()+", "+(value==null?"null":"\n"+Test.hexDump(value));
	}
}
