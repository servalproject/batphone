package org.servalproject.dna;

import java.nio.ByteBuffer;

import org.servalproject.Instrumentation;
import org.servalproject.dna.OpDT.DTtype;

public abstract class OpVisitor {
	
	public void onPacketArrived(Packet packet, PeerConversation peer){
		
	}
	public boolean onSimpleCode(Packet packet, OpSimple.Code code){
		throw new UnsupportedOperationException(code.name());
	}
	public boolean onError(Packet packet, String error){
		throw new IllegalStateException(error);
	}
	public boolean onGet(Packet packet, VariableRef reference){
		throw new UnsupportedOperationException(reference.toString());
	}
	public boolean onStat(Packet packet, Instrumentation.Variable field,int value){
		throw new UnsupportedOperationException(""+field+"="+value+"");
	}
	public boolean onData(Packet packet, VariableRef reference, short varLen, ByteBuffer buffer){
		throw new UnsupportedOperationException(reference.toString());
	}
	public boolean onSet(Packet packet, VariableRef reference, OpSet.Flag flag, ByteBuffer buffer){
		throw new UnsupportedOperationException(reference.toString());
	}
	public boolean onWrote(Packet packet, VariableRef reference){
		throw new UnsupportedOperationException(reference.toString());
	}
	public boolean onDone(Packet packet, byte count){
		throw new UnsupportedOperationException("Count: "+Integer.toString(count));
	}
	public boolean onDT(Packet packet, DTtype messageType, String emitterPhoneNumber, String message){
		throw new UnsupportedOperationException("DT type "+messageType+" from "+emitterPhoneNumber+" : '"+message+"'");
	}
	
}
