/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

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
		String cmd = coretask.DATA_FILE_PATH + "/bin/batmand "
				+ coretask.getProp("wifi.interface") + "\n";
		int result = coretask.runRootCommand(cmd);
		int tries = 0;
		while (result != 0 && tries < 4) {
			// Failed to start first time, so try again in a few seconds.
			try {
				Thread.sleep(1000);
				Log.e("Batman", "Retry starting batman...", null);
			} catch (InterruptedException e) {
				Log.e("Batman", e.toString(), e);
			}
			result = coretask.runRootCommand(cmd);
			if (result == 0)
				return;
			tries++;
		}
		if (result == 0)
			return;
		throw new IOException("Failed to start batman : " + cmd + " (result = "
				+ result + ")");
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

	/*
	 * private class constants
	 */

	// declare private variables
	public static final String PEER_FILE_LOCATION = "/data/data/org.servalproject/var/batmand.peers";
	private final int maxAge = 5;

	private int lastTimestamp = -1;
	private int lastOffset = -1;

}
