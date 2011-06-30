package org.servalproject.batman;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.dna.Dna;
import org.servalproject.dna.Packet;
import org.servalproject.dna.PeerConversation;
import org.servalproject.dna.SubscriberId;
import org.servalproject.dna.VariableResults;
import org.servalproject.dna.VariableType;

import android.util.Log;

// this class actively polls neighbor peers to find instances of dna.
public class PeerFinder extends Thread implements PeerParser {

	private final class PeerResult implements VariableResults {

		@Override
		public void result(PeerConversation peer, SubscriberId sid,
				VariableType varType, byte instance, InputStream value) {

			// skip any responses with our own id
			if (ourPrimary != null && ourPrimary.equals(sid))
				return;

			InetAddress addr = peer.getAddress().addr;

			PeerRecord p = peerMap.get(addr);
			if (p == null) {
				Log.v("BatPhone", "Found peer @" + addr);
				p = new PeerRecord(addr, 0);
				peerMap.put(addr, p);
				peers.add(p);
			}
			p.lastHeard = System.currentTimeMillis();
			try {
				p.did = Packet.unpackDid(value);
			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}
		}
	}

	Dna dna;
	ArrayList<PeerRecord> peers;
	Map<InetAddress, PeerRecord> peerMap;
	ServalBatPhoneApplication app;
	PeerResult result;
	SubscriberId ourPrimary;
	static final int timer = 10000;

	public PeerFinder(ServalBatPhoneApplication app) {
		dna = new Dna();
		dna.broadcast = true;
		peers = new ArrayList<PeerRecord>();
		peerMap = new HashMap<InetAddress, PeerRecord>();
		this.app = app;
		result = new PeerResult();
	}

	@Override
	public void run() {
		try {
			Log.v("BatPhone", "PeerFinder is running");
			while (!this.isInterrupted()) {

				ourPrimary = app.getSubscriberId();
				dna.clearPeers();
				dna.broadcast = true;

				dna.readVariable(null, "", VariableType.DIDs, (byte) -1, result);
				boolean tryUnicasts = false;

				for (int i = peers.size() - 1; i >= 0; i--) {
					PeerRecord p = peers.get(i);
					if (p.lastHeard + timer * 3 < System.currentTimeMillis()) {
						Log.v("BatPhone", "Removing peer @" + p.getAddress());
						peers.remove(i);
						peerMap.remove(p.getAddress());
						continue;
					}
					if (p.lastHeard + timer < System.currentTimeMillis()) {
						Log.v("BatPhone",
								"Trying unicast for peer @" + p.getAddress());
						dna.addStaticPeer(p.getAddress());
						tryUnicasts = true;
					}
				}

				if (tryUnicasts) {
					dna.broadcast = false;
					dna.readVariable(null, "", VariableType.DIDs, (byte) -1,
							result);
				}

				Thread.sleep(timer);
			}
		} catch (Exception e) {
			Log.e("BatPhone", e.toString(), e);
		}

		Log.v("BatPhone", "PeerFinder stopped");
		peers.clear();
		peerMap.clear();
	}

	@Override
	public int getPeerCount() throws IOException {
		return peers.size() + 1;
	}

	@Override
	public ArrayList<PeerRecord> getPeerList() throws IOException {
		return peers;
	}
}
