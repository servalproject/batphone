package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpTTL implements Operation {

	public int received_ttl = -1;

	static byte getCode() {
		return (byte) 0xfd;
	}

	@Override
	public void parse(ByteBuffer b, byte code) {
		received_ttl = (b.get()) & 0xff;
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.put((byte) received_ttl);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onTTL(packet, received_ttl);
	}

	@Override
	public String toString() {
		return "Received TTL:" + received_ttl;
	}
}
