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
