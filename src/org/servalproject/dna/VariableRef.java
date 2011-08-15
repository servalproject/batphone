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

public class VariableRef {
	public VariableType varType;
	public byte instance;
	public short offset;
	public short len;

	public VariableRef(VariableType varType, byte instance, short offset, short len){
		this.varType=varType;
		if (varType.hasMultipleValues())
			this.instance=instance;
		else
			this.instance=0;
		this.offset=offset;
		this.len=len;
	}
	public VariableRef(VariableType varType, short offset, short len){
		this(varType,(byte)-1,offset,len);
	}

	VariableRef(ByteBuffer b) {
		this.varType=VariableType.getVariableType(b.get());
		if (this.varType.hasMultipleValues())
			this.instance=b.get();
		else
			this.instance=0;
		this.offset=b.getShort();
		this.len=b.getShort();
	}

	void write(ByteBuffer b) {
		b.put(this.varType.varId);
		if (this.varType.hasMultipleValues())
			b.put(this.instance);
		b.putShort(this.offset);
		b.putShort(this.len);
	}

	@Override
	public String toString(){
		return varType.name+", "+(this.varType.hasMultipleValues()?instance+", ":"")+offset+", "+len;
	}
}
