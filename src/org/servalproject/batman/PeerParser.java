package org.servalproject.batman;

import java.io.IOException;
import java.util.ArrayList;

public interface PeerParser {
	public int getPeerCount() throws IOException;
	public ArrayList<PeerRecord> getPeerList() throws IOException;
}
