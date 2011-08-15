/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

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
