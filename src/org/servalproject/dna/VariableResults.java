package org.servalproject.dna;

import java.io.InputStream;

public interface VariableResults {
	public void result(PeerConversation peer, SubscriberId sid, VariableType varType, byte instance, InputStream value);
}
