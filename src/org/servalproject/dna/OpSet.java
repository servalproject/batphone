package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpSet implements Operation {
	VariableType varType;
	byte instance;
	short offset;
	short len;
	byte flags;
	ByteBuffer value=null;
	
	OpSet(){}
	public OpSet(VariableType varType, byte instance, short offset, byte flags, ByteBuffer value){
		this.varType=varType;
		this.instance=instance;
		this.offset=offset;
		this.len=(short)value.remaining();
		this.flags=flags;
		this.value=value;
	}
	
	static byte getCode(){return (byte)0x01;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		this.varType=VariableType.getVariableType(b.get());
		if (this.varType.hasMultipleValues()){
			this.instance=b.get();
		}else
			this.instance=-1;
		this.offset=b.getShort();
		this.len=b.getShort();
		this.flags=b.get();
		this.value=Packet.slice(b,(int)len & 0xffff);
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.put(this.varType.varId);
		if (this.varType.hasMultipleValues()){
			b.put(this.instance);
		}
		b.putShort(this.offset);
		b.putShort(this.len);
		b.put(this.flags);
		this.value.rewind();
		b.put(this.value);
	}

	@Override
	public void visit(Packet packet, OpVisitor v) {
		v.onSet(packet, this);
	}

	@Override
	public String toString() {
		return "Set: "+varType.name+", "+(this.varType.hasMultipleValues()?instance+", ":"")+offset+", "+len+", "+flags+", "+(value==null?"null":"\n"+Test.hexDump(value));
	}
}
