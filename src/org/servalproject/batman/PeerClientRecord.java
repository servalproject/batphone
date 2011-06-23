package org.servalproject.batman;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class PeerClientRecord {

	public static final String PEER_CLIENT_FILE_LOCATION = "/data/data/misc/dhcp/dnsmasq.leases";

	// get a list of Clients connected to the Access Point
	public ArrayList<PeerRecord> getPeerList() throws IOException {
		DataInputStream data = new DataInputStream(new FileInputStream(
				PEER_CLIENT_FILE_LOCATION));
		data.readLine();
		ArrayList<PeerRecord> peers = new ArrayList<PeerRecord>();
		byte addr[] = new byte[4];
		ByteBuffer b = ByteBuffer.wrap(addr);
		while (true) {
			String line = data.readLine();
			if (line == null)
				break;

			String fields[] = line.split("\\s+");
			b.clear();
			long l = Long.parseLong(fields[3]);
			b.putInt(Integer.reverseBytes((int) l));
			PeerRecord p = new PeerRecord(Inet4Address.getByAddress(addr), 0);
			peers.add(p);
		}
		return peers;
	}

}
