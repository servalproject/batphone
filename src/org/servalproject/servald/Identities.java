package org.servalproject.servald;

import java.io.IOException;
import java.util.ArrayList;

import org.servalproject.Control;
import org.servalproject.batman.PeerRecord;

import android.util.Log;

public class Identities {

	boolean initialisedP = false;
	static SubscriberId sids[] = null;
	static SubscriberId current_sid = null;
	static String current_did = null;

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
		ServalDResult result = servald.command("id", "self");
		Log.d("BatPhone", result.toString());
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

	public static void setDid(SubscriberId currentIdentity, String newNumber) {
		// TODO Auto-generated method stub

	}

	public static int getPeerCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	public static ArrayList<PeerRecord> getPeers() {
		// TODO Auto-generated method stub
		return null;
	}

	// Need functions to enter PINs, release identities and select current
	// identity.

}
