package org.servalproject.dna;

import java.nio.ByteBuffer;

public interface Operation {
	// note it is expected that all implementors of Operation have a default constructor
	// and are added to Packet.opTypes
	
	void parse(ByteBuffer b, byte code);
	void write(ByteBuffer b);
	boolean visit(Packet packet, OpVisitor v);
}
