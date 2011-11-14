/**
 *
 */
package org.servalproject.rhizome;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.servalproject.rhizome.peers.BatmanPeerList;

import android.util.Log;

/**
 * This class checks the peers Rhizome repository watching for new files.
 *
 * @author rbochet
 */
public class PeerWatcher extends Thread {

	/** Time between two checks in milliseconds */
	private static final long SLEEP_TIME = 15 * 1000;

	/** TAG for debugging */
	public static final String TAG = "R2";

	/** If the thread works or not */
	private boolean run = true;

	/** The peer list used to get repos. */
	private BatmanPeerList peerList;

	/**
	 * Constructor. Initialize the service that gets the peers.
	 *
	 * @param peerList An intanciated (and updatable) BatmanPeerList.
	 */
	public PeerWatcher(BatmanPeerList peerList) {
		this.peerList = peerList;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		super.run();
		List<String> repos;
		// Works forever
		while (run) {
			Log.v(TAG,
					"Update procedure launched @ "
							+ new Date().toLocaleString());

			repos = getPeersRepo();

			for (String repo : repos) {
				// For each repo, download the interesting content
				new StuffDownloader(repo);
			}
			// Wait before the new lookup
			try {
				Thread.sleep(SLEEP_TIME);
			} catch (InterruptedException e) {
			}
		}
		Log.i(TAG, "Updates stop.");
	}

	/**
	 * List the peers and return their URLs for using with StuffDownloader.
	 *
	 * @return The list of all the peers' servers.
	 */
	private List<String> getPeersRepo() {
		List<String> ret = new ArrayList<String>();

		String[] peers = peerList.getPeerList();
		for (String peer : peers) {
			if (peer.indexOf(" ")!=-1) peer = peer.substring(0,peer.indexOf(" "));
			Log.v(TAG, "PEER : " + peer);
			ret
					.add("http://" + peer + ":" + RhizomeRetriever.SERVER_PORT
							+ "/");
		}

		return ret;
	}

	/**
	 * Stop the thread on the next iteration.
	 */
	public void stopUpdate() {
		run = false;
	}

}
