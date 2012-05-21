/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.servald;

import java.util.ArrayList;

import org.servalproject.Control;
import org.servalproject.PeerRecord;

import android.util.Log;

public class Identities {

	boolean initialisedP = false;
	static SubscriberId sids[] = null;
	static SubscriberId current_sid = null;
	static String current_did = null;
	static String current_name = null;
	static ArrayList<PeerRecord> peers = null;
	private static long last_peer_fetch_time = 0;

	public Identities() {
		if (!initialisedP)
			readIdentities();
		initialisedP = true;
	}

	static void readIdentities() {
		// ask servald for list of identities
		try {
			Control.startServalD();
		} catch (ServalDFailureException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		ServalDResult result = ServalD.command("id", "self");
		sids = new SubscriberId[result.outv.length];
		for(int i =0; i< result.outv.length;i++) {
			try {
				sids[i] = new SubscriberId(result.outv[i]);
			}
			catch (SubscriberId.InvalidHexException e) {
				Log.e("BatPhone", "got an invalid SID: " + result.outv[i], e);
			}
		}
		setCurrentIdentity(0);
	}

	private static void setCurrentIdentity(int i) {
		current_did = null;
		current_sid = null;
		current_name = null;
		if (sids.length > 0)
			current_sid = sids[0];
		if (current_sid != null) {
			ServalDResult result = ServalD.command("node", "info",
					current_sid.toString(), "resolvedid");
			if (result.outv.length >= 10) {
				if ((result.outv[0].equals("record")
				&& result.outv[3].equals("found")) == false) {
					// Couldn't find the specified identity, so no did.
					return;
				}
				if (result.outv[5].equals("did-not-resolved") == false)
					// Get DID
					current_did = result.outv[5];
				if (result.outv[10].equals("name-not-resolved") == false)
					// Get name
					current_name = result.outv[10];
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

	public static String getCurrentName()
	{
		if (current_name == null)
			readIdentities();
		return current_name;
	}

	public static void setDid(SubscriberId sid, String did, String name) {
		// Need to stop servald, write did into keyring file, and then
		// restart it.
		// XXX - Eventually it would be nice to be able to do this without
		// shutting servald down.
		try {
			Control.stopServalD();
		} catch (ServalDFailureException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		// XXX Doesn't check for failure
		ServalDResult result = ServalD.command("set", "did", sid.toString(),
				did, name); // empty name for now

		// Restart servald and re-read identities
		try {
			Control.stopServalD();
		} catch (ServalDFailureException e) {
			Log.e("BatPhone", "Failed to stop ServalD" + e.toString(), e);
		}
		readIdentities();
		return;
	}


	public static int getPeerCount() {
		ServalDResult result = ServalD.command("id", "peers");
		return result.outv.length;
	}

	public static long getLastPeerListUpdateTime() {
		return last_peer_fetch_time;
	}

	// Need functions to enter PINs, release identities and select current
	// identity.

}
