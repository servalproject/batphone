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
import java.util.ArrayList;
import java.util.List;

import org.servalproject.system.CoreTask;

import android.util.Log;

public class Olsr extends Routing {

	public Olsr(CoreTask coretask) {
		super(coretask);
	}

	@Override
	public void start() throws IOException {
		if (coretask.runRootCommand(coretask.DATA_FILE_PATH + "/bin/olsrd -f "
				+ coretask.DATA_FILE_PATH + "/conf/olsrd.conf -d 0") != 0)
			throw new IOException("Failed to start olsrd");
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

	// get a list of known peers from the routing table (that way we don't care
	// what protocol is used)
	@Override
	public ArrayList<PeerRecord> getPeerList() throws IOException {
		List<RouteTable> routes = RouteTable.getRoutes();
		ArrayList<PeerRecord> peers = new ArrayList<PeerRecord>();
		for (int i = 0; i < routes.size(); i++) {
			RouteTable route = routes.get(i);
			if (!route.isHost())
				continue;
			PeerRecord p = new PeerRecord(route.getAddr(), 0);
			peers.add(p);
		}
		return peers;
	}

	@Override
	public int getPeerCount() throws IOException {
		return getPeerList().size() + 1;
	}

}
