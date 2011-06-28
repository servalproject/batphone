package org.servalproject.batman;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.ArrayList;

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

	@Override
	public int getPeerCount() throws IOException {
		return getPeerList().size() + 1;
	}

}
