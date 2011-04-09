package org.servalproject.dna;

import java.nio.ByteBuffer;

public abstract class OpVisitor {
	
	public boolean onSimpleCode(Packet packet, OpSimple.Code code){
		throw new UnsupportedOperationException(code.name());
	}
	public boolean onError(Packet packet, String error){
		throw new IllegalStateException(error);
	}
	public boolean onGet(Packet packet, VariableRef reference){
		throw new UnsupportedOperationException(reference.toString());
	}
	public boolean onSet(Packet packet, VariableRef reference, OpSet.Flag flag, ByteBuffer buffer){
		throw new UnsupportedOperationException(reference.toString());
	}
	public boolean onWrote(Packet packet, VariableRef reference){
		throw new UnsupportedOperationException(reference.toString());
	}
}
