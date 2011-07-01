package org.servalproject.batman;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;

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

	/*
	 * private class constants
	 */

	// declare private variables
	public static final String PEER_FILE_LOCATION = "/data/data/org.servalproject/var/batmand.peers";
	private final int maxAge = 5;

	private int lastTimestamp = -1;
	private int lastOffset = -1;

	private ArrayList<PeerRecord> peers;

	private int readPeerCount(DataInputStream data) throws IOException {
		// get the peer list offset
		int offset = data.readInt();
		if (offset == 0)
			throw new IOException("File not ready for first read");

		if (lastOffset != offset) {
			peers = null;
			lastOffset = offset;
		}
		int peerCount = data.readInt();

		// skip to the start of the data
		if (data.skip(offset - 8) != (offset - 8))
			// skip starts from the current position & offset is defined from
			// start of file
			throw new IOException(
					"unable to skip to the required position in the file");

		int timestamp = data.readInt();

		// drop the cached peer list if the file has changed
		if (timestamp != lastTimestamp)
			peers = null;

		lastTimestamp = timestamp;

		// compare to current time
		long dateInSeconds = new Date().getTime() / 1000;
		if (dateInSeconds > (timestamp + maxAge))
			throw new IOException("Batman peer file is stale");

		return peerCount;
	}

	/**
	 * Read the file at the path specified at the time of instantiation and
	 * return the number of peers
	 *
	 * @return the number of peers or -1 if the information is stale
	 *
	 * @throws IOException
	 *             if any IO operation on the file fails
	 */

	@Override
	public int getPeerCount() throws IOException {

		DataInputStream data = new DataInputStream(new FileInputStream(
				PEER_FILE_LOCATION));
		try {
			// count our phone in the returned peer count
			int peers = readPeerCount(data);
			return peers >= 0 ? peers + 1 : 1;
		} finally {
			data.close();
		}
	}

	/**
	 * Read the file at the path specified at the time of instantiation and
	 * return a list of peer records
	 *
	 * @return a list of peer records or null if the list is stale
	 *
	 * @throws IOException
	 *             if any IO operation on the file fails
	 */
	@Override
	public ArrayList<PeerRecord> getPeerList() throws IOException {

		DataInputStream data = new DataInputStream(new FileInputStream(
				PEER_FILE_LOCATION));
		try {
			int peerCount = readPeerCount(data);

			if (peers != null)
				return peers;

			ArrayList<PeerRecord> newPeers = new ArrayList<PeerRecord>();
			for (int i = 0; i < peerCount; i++) {
				int addressType = data.read();
				byte addr[];
				switch (addressType) {
				case 4:
					addr = new byte[4];
					break;
				case 6:
					addr = new byte[16];
					break;
				default:
					throw new IOException("Invalid address type " + addressType);
				}
				data.read(addr);
				data.skip(32 - addr.length);

				int linkScore = data.read();

				newPeers.add(new PeerRecord(InetAddress.getByAddress(addr),
						linkScore));
			}
			// only overwrite the peer field when were done, to avoid race
			// conditions.
			peers = newPeers;
			return newPeers;
		} finally {
			data.close();
		}
	}
}
