package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpGet implements Operation {
	VariableType varType;
	byte instance;
	short offset;
	short len;
	
	OpGet(){}
	public OpGet(VariableType varType, byte instance, short offset, short len){
		this.varType=varType;
		this.instance=instance;
		this.offset=offset;
		this.len=len;
	}
	
	static byte getCode(){return (byte)0x00;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		this.varType=VariableType.getVariableType(b.get());
		if (this.varType.hasMultipleValues())
			this.instance=b.get();
		else
			this.instance=-1;
		this.offset=b.getShort();
		this.len=b.getShort();
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.put(this.varType.varId);
		if (this.varType.hasMultipleValues())
			b.put(this.instance);
		b.putShort(this.offset);
		b.putShort(this.len);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onGet(packet, this);
	}

	@Override
	public String toString() {
		return "Get: "+varType.name+", "+(this.varType.hasMultipleValues()?instance+", ":"")+offset+", "+len;
	}
}
