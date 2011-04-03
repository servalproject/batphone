package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpOk implements Operation {
	
	static byte getCode(){return (byte)0x81;}
	
	@Override
	public void parse(ByteBuffer b) {
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
	}

	@Override
	public String toString() {
		return "Ok";
	}
}
