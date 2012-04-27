package org.servalproject.servald;

import java.io.IOException;
import java.util.ArrayList;

import org.servalproject.Control;
import org.servalproject.PeerRecord;

import android.util.Log;

public class Identities {

	boolean initialisedP = false;
	static SubscriberId sids[] = null;
	static SubscriberId current_sid = null;
	static String current_did = null;
	static ArrayList<PeerRecord> peers = null;

	public Identities() {
		if (!initialisedP)
			readIdentities();
		initialisedP = true;
	}

	static void readIdentities() {
		// ask servald for list of identities
		try {
			Control.startServalD();
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		ServalD servald = new ServalD();
		String args[] = {
				"id", "self"
		};
		ServalDResult result = servald.command(args);
		sids = new SubscriberId[result.outv.length];
		for(int i =0; i< result.outv.length;i++)
			// Parse sid and put in sids array;
			sids[i] = new SubscriberId(result.outv[i]);
		setCurrentIdentity(0);

	}

	private static void setCurrentIdentity(int i) {
		current_did = null;
		current_sid = null;
		if (sids.length > 0)
			current_sid = sids[0];
		if (current_sid != null) {
			ServalD servald = new ServalD();
			String args[] = {
					"node", "info", current_sid.toString(), "resolvedid"
			};
			ServalDResult result = servald.command(args);
			if (result.outv.length >= 10) {
				if ((result.outv[0].equals("record")
				&& result.outv[3].equals("found")) == false) {
					// Couldn't find the specified identity, so no did.
					return;
				}
				if (result.outv[5].equals("did-not-resolved") == true) {
					// no known did;
					return;
				}
				// Get DID
				current_did = result.outv[5];
			} else {
				// Couldn't find the specified identity, so no did.
				return;
			}
		}
	}

	public static SubscriberId getCurrentIdentity()
	{
		if (current_sid == null)
			readIdentities();
		return current_sid;
	}

	public static String getCurrentDid()
	{
		if (current_did == null)
			readIdentities();
		return current_did;
	}

	public static void setDid(SubscriberId sid, String did) {
		// Need to stop servald, write did into keyring file, and then
		// restart it.
		// XXX - Eventually it would be nice to be able to do this without
		// shutting servald down.
		try {
			Control.stopServalD();
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		ServalD servald = new ServalD();
		String args[] = {
				"set", "did", sid.toString(), did
		};
		ServalDResult result = servald.command(args);
		// Restart servald and re-read identities
		readIdentities();
		return;
	}

	private static void populatePeerList()
	{
		// XXX - Only re-fetch list if some time interval
		// has passed?
		try {
			Control.startServalD();
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		ServalD servald = new ServalD();
		String args[] = {
				"id", "list"
		};
		ServalDResult result = servald.command(args);
		peers.clear();
		// XXX - actually add the peers, with some information
	}

	public static ArrayList<PeerRecord> getPeers() {
		// TODO Auto-generated method stub
		populatePeerList();
		return peers;
	}

	// Need functions to enter PINs, release identities and select current
	// identity.

}
