package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpWrote implements Operation {
	ByteBuffer wrote;
	
	OpWrote(){}
	public OpWrote(ByteBuffer b){
		this.wrote=b;
		if (wrote.remaining()>255) wrote.limit(wrote.position()+255);
	}
	
	static byte getCode(){return (byte)0x83;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		int len=((int)b.get())&0xff;
		this.wrote=Packet.slice(b,len);
		b.position(b.position()+len);
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.put((byte)b.remaining());
		b.put(b);
	}

	@Override
	public void visit(Packet packet, OpVisitor v) {
		wrote.clear();
		v.onWrote(packet, wrote);
	}

	@Override
	public String toString() {
		return "Wrote: "+Packet.binToHex(this.wrote);
	}
}
