package org.servalproject.dna;

import java.io.InputStream;
import java.net.SocketAddress;

public interface VariableResults {
	public void result(SocketAddress peer, SubscriberId sid, VariableType varType, byte instance, InputStream value);
}
