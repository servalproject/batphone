package org.servalproject.dna;

import java.nio.ByteBuffer;

public class SubscriberId {
	private byte []sid;
	
	public SubscriberId(){
		sid=new byte[32];
		Packet.rand.nextBytes(sid);
	}
	
	public SubscriberId(String sid){
		this.sid=new byte[32];
		if (sid.length()!=64)
			throw new IllegalArgumentException("Subscriber id's must be 64 characters in length");
		int j=0;
		for (int i=0;i<this.sid.length;i++){
			this.sid[i]=(byte)(
				(Character.digit(sid.charAt(j++),16)<<4) |
				Character.digit(sid.charAt(j++),16)
			);
		}
	}
	
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
