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
	
	public String toString(){
		return varType.name+", "+(this.varType.hasMultipleValues()?instance+", ":"")+offset+", "+len;
	}
}
