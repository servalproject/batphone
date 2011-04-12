package org.servalproject.dna;

import java.net.SocketAddress;

public class PeerConversation {
	Packet packet;
	SocketAddress addr;
	boolean responseReceived=false;
	boolean conversationComplete=false;
	int retryCount=0;
	OpVisitor vis;
	
	PeerConversation(Packet packet, SocketAddress addr, OpVisitor vis){
		this.packet=packet;
		this.addr=addr;
		this.vis=vis;
	}
	
	void processResponse(Packet p){
		responseReceived=true;
		for (Operation o:p.operations){
			if (o.visit(p, vis))
				conversationComplete=true;
		}
	}
}
