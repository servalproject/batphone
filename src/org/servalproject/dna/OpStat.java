package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpStat implements Operation {
	short field;
	int value;
	
	OpStat(){}
	public OpStat(short field, int value){
		this.field=field;
		this.value=value;
	}
	
	static byte getCode(){return (byte)0x40;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		this.field=b.getShort();
		this.value=b.getInt();
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.putShort(field);
		b.putInt(value);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onStat(packet, this.field, this.value);
	}

	@Override
	public String toString() {
		return "Stat: "+field+"="+value;
	}
}
