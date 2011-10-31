package org.servalproject.batman;

import java.io.IOException;
import java.util.ArrayList;

import org.servalproject.system.CoreTask;

public class None extends Routing {

	public None(CoreTask coretask) {
		super(coretask);
	}

	@Override
	public int getPeerCount() throws IOException {
		return 1;
	}

	@Override
	public void start() throws IOException {
	}

	@Override
	public void stop() throws IOException {
	}

	@Override
	public boolean isRunning() {
		return true;
	}

	@Override
	public ArrayList<PeerRecord> getPeerList() throws IOException {
		return null;
	}
}
