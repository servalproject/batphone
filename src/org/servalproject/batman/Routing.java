package org.servalproject.batman;

import java.io.IOException;
import java.util.ArrayList;

import org.servalproject.system.CoreTask;

public abstract class Routing implements PeerParser {
	CoreTask coretask;

	Routing(CoreTask coretask) {
		this.coretask = coretask;
	}

	public abstract void start() throws IOException;

	public abstract void stop() throws IOException;

	public abstract boolean isRunning();

	@Override
	public abstract ArrayList<PeerRecord> getPeerList() throws IOException;
}
