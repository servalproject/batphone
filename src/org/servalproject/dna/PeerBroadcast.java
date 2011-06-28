package org.servalproject.dna;

import java.util.ArrayList;
import java.util.List;

public class PeerBroadcast {
	Packet packet;
	OpVisitor visitor;
	int retryCount = 0;
	long transmissionTime;
	boolean hadResponse = false;
	List<PeerConversation> replies;

	PeerBroadcast(Packet packet, OpVisitor visitor) {
		this.packet = packet;
		this.visitor = visitor;
	}

	public PeerConversation getConversation(Address addr) {
		hadResponse = true;

		PeerConversation ret = new PeerConversation(packet, addr, visitor);
		ret.transmissionTime = this.transmissionTime;
		ret.retryCount = this.retryCount;

		if (replies == null)
			replies = new ArrayList<PeerConversation>();

		replies.add(ret);
		return ret;
	}
}
