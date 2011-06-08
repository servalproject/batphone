package org.servalproject.batman;

import java.io.IOException;

import org.servalproject.system.CoreTask;

import android.util.Log;

public class Olsr extends Routing {

	public Olsr(CoreTask coretask) {
		super(coretask);
	}

	@Override
	public void start() throws IOException {
		coretask.runRootCommand(coretask.DATA_FILE_PATH + "/bin/olsrd -f "
				+ coretask.DATA_FILE_PATH + "/conf/olsrd.conf -d 0");
	}

	@Override
	public void stop() throws IOException {
		if (isRunning())
			coretask.killProcess("bin/olsrd", true);
	}

	@Override
	public boolean isRunning() {
		try {
			return coretask.isProcessRunning("bin/olsrd");
		} catch (Exception e) {
			Log.e("BatPhone", e.toString(), e);
			return false;
		}
	}

}
