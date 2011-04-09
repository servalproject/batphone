package org.servalproject.dna;

public abstract class OpVisitor {
	
	public boolean onSimpleCode(Packet packet, OpSimple.Code code){
		throw new UnsupportedOperationException();
	}
	public boolean onError(Packet packet, String error){
		throw new IllegalStateException(error);
	}
	public boolean onGet(Packet packet, OpGet get){
		throw new UnsupportedOperationException();
	}
	public boolean onSet(Packet packet, OpSet set){
		throw new UnsupportedOperationException();
	}
	public boolean onWrote(Packet packet, OpWrote wrote){
		throw new UnsupportedOperationException(wrote.toString());
	}
}
