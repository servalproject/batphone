package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpError implements Operation {
	String error;
	
	OpError(){}
	public OpError(String error){
		if (error.length()>255)
			this.error=error.substring(0, 255);
		else
			this.error=error;
	}
	
	static byte getCode(){return (byte)0x7f;}
	
	public void parse(ByteBuffer b, byte code) {
		int len=(b.get())&0xff;
		this.error=new String(b.array(), b.arrayOffset()+b.position(), len);
		b.position(b.position()+len);
	}

	public void write(ByteBuffer b) {
		b.put(getCode());
		b.put((byte)error.length());
		b.put(error.getBytes());
	}

	public boolean visit(Packet packet, OpVisitor v) {
		return v.onError(packet, this.error);
	}

	@Override
	public String toString() {
		return "Error: "+this.error;
	}
}
