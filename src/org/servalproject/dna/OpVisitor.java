package org.servalproject.dna;

import java.nio.ByteBuffer;

public abstract class OpVisitor {
	
	public void onSimpleCode(Packet packet, OpSimple.Code code){
		throw new UnsupportedOperationException();
	}
	public void onError(Packet packet, String error){
		throw new UnsupportedOperationException();
	}
	public void onGet(Packet packet, OpGet get){
		throw new UnsupportedOperationException();
	}
	public void onSet(Packet packet, OpSet set){
		throw new UnsupportedOperationException();
	}
	public void onWrote(Packet packet, ByteBuffer buff){
		throw new UnsupportedOperationException();
	}
}
