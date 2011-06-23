package org.servalproject.batman;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.util.ArrayList;

public class ApClientsParser implements PeerParser {

	public static final String PEER_CLIENT_FILE_LOCATION = "/data/misc/dhcp/dnsmasq.leases";

	// get a list of Clients connected to the Access Point
	@Override
	public ArrayList<PeerRecord> getPeerList() throws IOException {
		DataInputStream data = new DataInputStream(new FileInputStream(
				PEER_CLIENT_FILE_LOCATION));
		ArrayList<PeerRecord> peers = new ArrayList<PeerRecord>();
		while (true) {
			String line = data.readLine();
			if (line == null)
				break;

			String fields[] = line.split("\\s+");
			PeerRecord p = new PeerRecord(Inet4Address.getByName(fields[3]), 0);
			peers.add(p);
		}
		return peers;
	}

	@Override
	public int getPeerCount() throws IOException {
		return getPeerList().size();
	}

}
