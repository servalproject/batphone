package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpEot implements Operation {
	
	static byte getCode(){return (byte)0xff;}
	
	@Override
	public void parse(ByteBuffer b) {
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
	}
	
	@Override
	public String toString() {
		return "End";
	}
}
