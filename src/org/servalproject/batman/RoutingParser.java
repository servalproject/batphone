package org.servalproject.batman;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RoutingParser {
	// get a list of known peers from the routing table (that way we don't care
	// what protocol is used)
	public ArrayList<PeerRecord> getPeerList() throws IOException {
		DataInputStream data = new DataInputStream(new FileInputStream(
				"/proc/net/route"));
		// drop the header
		data.readLine();
		ArrayList<PeerRecord> peers = new ArrayList<PeerRecord>();
		byte addr[] = new byte[4];
		ByteBuffer b = ByteBuffer.wrap(addr);
		while (true) {
			String line = data.readLine();
			if (line == null)
				break;

			String fields[] = line.split("\\s+");

			if (!fields[7].equals("FFFFFFFF"))
				continue;

			b.clear();
			long l = Long.parseLong(fields[1], 16);

			b.putInt(Integer.reverseBytes((int) l));

			PeerRecord p = new PeerRecord(Inet4Address.getByAddress(addr), 0);
			peers.add(p);
		}
		return peers;
	}

}
