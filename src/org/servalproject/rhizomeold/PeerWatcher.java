/**
 *
 */
package org.servalproject.rhizomeold;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jibble.simplewebserver.SimpleWebServer;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.batman.PeerRecord;
import org.servalproject.rhizomeold.peers.BatmanPeerList;
import org.servalproject.rhizomeold.peers.BatmanServiceClient;
import org.servalproject.servald.Identities;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

/**
 * This class checks the peers Rhizome repository watching for new files.
 *
 * @author rbochet
 */
public class PeerWatcher extends Thread {

	static SimpleWebServer server;

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
	public PeerWatcher() {
		// Setup and start the peer list stuff
		peerList = new BatmanPeerList();
		BatmanServiceClient bsc = new BatmanServiceClient(
				ServalBatPhoneApplication.context.getApplicationContext(),
				peerList);
		new Thread(bsc).start();
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
			// Start the web server
			try {
				// Get the wifi address
				WifiManager wifiManager = (WifiManager) ServalBatPhoneApplication.context
						.getSystemService(Context.WIFI_SERVICE);
				WifiInfo wifiInfo = wifiManager.getConnectionInfo();
				int ipAddress = wifiInfo.getIpAddress();
				String stringIP = Formatter.formatIpAddress(ipAddress);
				if (server == null)
					server = new SimpleWebServer(RhizomeUtils.dirRhizome, 6666);
			} catch (IOException e) {
				// goToast("Error starting webserver. Only polling.");
				e.printStackTrace();
			}

			Log.v(TAG,
					"Update procedure launched @ "
							+ new Date().toLocaleString());

			repos = getPeersRepo();
			Log.d(TAG, "Repo list: " + repos);

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

		ArrayList<PeerRecord> peers;
		try {
			peers = Identities.getPeers();
			for (PeerRecord peerrecord : peers) {
				String peer = peerrecord.getAddress().toString();
				Log.v(TAG, "PEER(raw) : " + peer);
				if (peer.indexOf("/") != -1)
					peer = peer.substring(peer.indexOf("/") + 1);
				Log.v(TAG, "PEER : " + peer);
				ret.add("http://" + peer + ":" + RhizomeRetriever.SERVER_PORT
						+ "/");
			}
		} catch (Exception e) {
			peers = null;
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
