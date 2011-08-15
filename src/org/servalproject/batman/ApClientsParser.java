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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.util.ArrayList;

import android.util.Log;

public class ApClientsParser implements PeerParser {

	public static final String PEER_CLIENT_FILE_LOCATION = "/data/misc/dhcp/dnsmasq.leases";
	private long lastModified = 0;
	private ArrayList<PeerRecord> lastPeers;

	// get a list of Clients connected to the Access Point
	@Override
	public ArrayList<PeerRecord> getPeerList() throws IOException {

		// TODO arp? ping? or something to make sure the peers are still there.
		// dnsmasq leases seem to default to 12 hours. I don't think we should
		// wait around that long for a client to come back....

		// check if the cached records are still relevant
		File leases = new File(PEER_CLIENT_FILE_LOCATION);
		long modified = leases.lastModified();

		if (modified == lastModified) {
			// check if any leases have expired
			for (int i = lastPeers.size() - 1; i >= 0; i--) {
				if (lastPeers.get(i).expiryTime <= System.currentTimeMillis())
					lastPeers.remove(i);

			}
			return lastPeers;
		}

		ArrayList<PeerRecord> peers = new ArrayList<PeerRecord>();
		DataInputStream data = new DataInputStream(new FileInputStream(leases));

		try {
			while (true) {
				String line = data.readLine();
				if (line == null)
					break;
				try {
					String fields[] = line.split("\\s");
					long expiryTime = Long.parseLong(fields[0]) * 1000;
					if (expiryTime <= System.currentTimeMillis())
						continue;

					PeerRecord p = new PeerRecord(
							Inet4Address.getByName(fields[2]), 0);
					p.expiryTime = expiryTime;
					peers.add(p);
				} catch (Exception e) {
					Log.v("BatPhone", "Failed to parse " + line);
				}
			}
		} finally {
			data.close();
		}

		lastPeers = peers;
		lastModified = modified;

		return peers;
	}

	@Override
	public int getPeerCount() throws IOException {
		return getPeerList().size() + 1;
	}

}
