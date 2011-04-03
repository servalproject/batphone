package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpDeclined implements Operation {
	
	static byte getCode(){return (byte)0x80;}
	
	@Override
	public void parse(ByteBuffer b) {
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
	}
	
	@Override
	public String toString() {
		return "Declined";
	}
}
