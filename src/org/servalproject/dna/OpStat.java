package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpStat implements Operation {
	byte[] bytes;
	short field;
	int value;
	
	OpStat(){}
	public OpStat(short field, int value){
		this.bytes=new byte[6];
		this.bytes[0]=(byte) (field&0xff);
		this.bytes[1]=(byte) (field>>8);
		this.bytes[2]=(byte) (value&0xff);
		this.bytes[3]=(byte) ((value>>8)&0xff);
		this.bytes[4]=(byte) ((value>>16)&0xff);
		this.bytes[5]=(byte) ((value>>24)&0xff);
	}
	
	static byte getCode(){return (byte)0x40;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		this.field=(short) (b.get()+(b.get()<<8));
		this.value=b.get()+(b.get()<<8)+(b.get()<<16)+(b.get()<<24);
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.put(this.bytes);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onStat(packet, this.field,this.value);
	}

	@Override
	public String toString() {
		return "Stat: "+field+"="+value;
	}
}
