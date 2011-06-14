package org.servalproject.batman;

import java.io.IOException;

import org.servalproject.system.CoreTask;

import android.util.Log;

public class Batman extends Routing {

	public Batman(CoreTask coretask) {
		super(coretask);
	}

	@Override
	public void start() throws IOException {
		if (coretask.runRootCommand(coretask.DATA_FILE_PATH + "/bin/batmand "
				+ coretask.getProp("wifi.interface")) != 0)
			throw new IOException("Failed to start batman routing");
	}

	@Override
	public void stop() throws IOException {
		if (isRunning())
			coretask.killProcess("bin/batmand", true);
	}

	@Override
	public boolean isRunning() {
		try {
			return coretask.isProcessRunning("bin/batmand");
		} catch (Exception e) {
			Log.e("BatPhone", e.toString(), e);
			return false;
		}
	}

}
