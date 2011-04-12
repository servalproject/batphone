package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpDone implements Operation {
	byte count;
	
	OpDone(){}
	public OpDone(byte count){
		this.count=count;
	}
	
	static byte getCode(){return (byte)0x7e;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		this.count=b.get();
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.put(this.count);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onDone(packet, this.count);
	}

	@Override
	public String toString() {
		return "Done: "+this.count;
	}
}
