package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpWrote implements Operation {
	byte[]stuff=new byte[6];
	
	OpWrote(){}
	
	static byte getCode(){return (byte)0x83;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		b.get(stuff);
	}

	@Override
	public void write(ByteBuffer b) {
		// TODO
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onWrote(packet, this);
	}

	@Override
	public String toString() {
		return "Wrote: "+Packet.binToHex(this.stuff);
	}
}
