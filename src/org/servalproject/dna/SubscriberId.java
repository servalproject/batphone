package org.servalproject.dna;

import java.nio.ByteBuffer;

public class SubscriberId {
	private byte []sid;
	
	/*TODO
	public SubscriberId(String sid){
		
	}
	*/
	public SubscriberId(ByteBuffer b){
		sid=new byte[32];
		b.get(sid);
	}
	public SubscriberId(byte[] sid){
		if (sid.length!=32) throw new IllegalArgumentException("Subscriber id's must be 32 bytes long");
		this.sid=sid;
	}
	
	byte[] getSid(){
		return sid;
	}
	
	public String toString(){
		return Packet.binToHex(sid);
	}
}
