package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpPad implements Operation {
	
	static byte getCode(){return (byte)0xfe;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		int len = ((int)b.get())&0xff;
		b.position(b.position()+len);
	}

	@Override
	public void write(ByteBuffer b) {
		int len=Packet.rand.nextInt(16);
		if (len==0) return;
		
		b.put(getCode());
		b.put((byte)len);
		byte padding[]=new byte[len];
		Packet.rand.nextBytes(padding);
		b.put(padding);
	}
	
	@Override
	public void visit(Packet packet, OpVisitor v) {}

	@Override
	public String toString() {
		return "Padding";
	}
}
