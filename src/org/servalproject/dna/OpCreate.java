package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpCreate implements Operation {
	
	static byte getCode(){return (byte)0x0f;}
	
	@Override
	public void parse(ByteBuffer b) {
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
	}
	
	@Override
	public String toString() {
		return "Create";
	}
}
