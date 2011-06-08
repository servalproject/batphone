package org.servalproject.batman;

import java.io.IOException;

import org.servalproject.system.CoreTask;

public abstract class Routing {
	CoreTask coretask;

	Routing(CoreTask coretask) {
		this.coretask = coretask;
	}

	public abstract void start() throws IOException;

	public abstract void stop() throws IOException;

	public abstract boolean isRunning();
}
