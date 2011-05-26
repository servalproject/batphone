package org.servalproject.dna;

import java.net.InetAddress;

public class PeerConversation {
	static class Id{
		long transId;
		Address addr;
		
		Id(long transId, Address addr){
			this.transId=transId;
			this.addr=addr;
		}
		
		@Override
		public int hashCode() {
			return (int) transId ^ addr.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Id){
				Id x = (Id)o;
				return x.transId==this.transId && x.addr.equals(this.addr);
			}
			return false;
		}
	}
	
	Id id;
	Packet packet;
	boolean responseReceived=false;
	boolean conversationComplete=false;
	int retryCount=0;
	long transmissionTime;
	long replyTime;
	OpVisitor vis;
	
	PeerConversation(Packet packet, InetAddress addr, OpVisitor vis){
		this(packet,new Address(addr, Packet.dnaPort),vis);
	}
	
	PeerConversation(Packet packet, InetAddress addr, int port, OpVisitor vis){
		this(packet, new Address(addr, port), vis);
	}
	PeerConversation(Packet packet, Address addr, OpVisitor vis){
		this.id=new Id(packet.transactionId, addr);
		this.packet=packet;
		this.vis=vis;
	}
	
	public Address getAddress(){
		return id.addr;
	}
	
	public int getPingTime(){
		return (int) (this.replyTime - this.transmissionTime);
	}
	
	public int getRetries(){
		return this.retryCount;
	}
	
	void processResponse(Packet p){
		responseReceived=true;
		if (vis == null){
			conversationComplete=true;
			return;
		}
		vis.onPacketArrived(p, this);
		for (Operation o:p.operations){
			if (o.visit(p, vis))
				conversationComplete=true;
		}
	}
}
