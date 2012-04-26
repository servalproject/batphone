package org.servalproject.batman;

import java.io.IOException;

import org.servalproject.system.CoreTask;

public class None extends Routing {

	public None(CoreTask coretask) {
		super(coretask);
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

}
