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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.servalproject.account.AccountService;

import android.content.ContentResolver;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

/**
 *
 * @author brendon
 *
 *         Service responsible for periodically fetching peer list from ServalD.
 *         Activities and other components can register listeners to receive new
 *         peer updates.
 *
 */
public class PeerListService {
	private PeerListService() {

	}

	public static ConcurrentMap<SubscriberId, Peer> peers = new ConcurrentHashMap<SubscriberId, Peer>();

	private static class BroadcastPeer extends Peer {
		private BroadcastPeer() {
			super(SubscriberId.broadcastSid());
			contactId = Long.MAX_VALUE;
			did = "*";
			name = "Broadcast/Everyone";
			setContactName(name);
			cacheUntil = Long.MAX_VALUE;
			lastSeen = 0;
			reachable = false;
		}

		@Override
		public String getSortString() {
			// ensure this peer always sorts to the top
			return "";
		}
	}

	public static final Peer broadcast = new BroadcastPeer();
	static {
		clear();
	}

	public static Peer getPeer(ContentResolver resolver, SubscriberId sid) {
		return getPeer(resolver, sid, true);
	}

	private static Peer getPeer(ContentResolver resolver, SubscriberId sid,
			boolean alwaysResolve) {
		boolean changed = false;

		Peer p = peers.get(sid);
		if (p == null) {
			p = new Peer(sid);
			peers.put(sid, p);
			changed = true;
			Log.v("PeerListService", "Discovered peer " + sid.abbreviation());
		} else if (!alwaysResolve)
			return p;

		if (checkContacts(resolver, p))
			changed = true;

		if (changed)
			notifyListeners(p);

		return p;
	}

	public static void peerReachable(ContentResolver resolver,
			SubscriberId sid, boolean reachable) {
		Peer p = peers.get(sid);
		if (!reachable && p == null)
			return;
		if (p == null)
			p = getPeer(resolver, sid, false);
		if (p.reachable == reachable)
			return;
		Log.v("PeerListService", p + " reachable " + reachable);
		p.reachable = reachable;
		notifyListeners(p);
	}

	private static boolean checkContacts(ContentResolver resolver, Peer p) {
		if (p.sid.isBroadcast())
			return false;

		long contactId = AccountService.getContactId(
				resolver, p.sid);

		boolean changed = false;
		String contactName = null;

		if (contactId >= 0) {
			contactName = AccountService
					.getContactName(
							resolver,
							p.contactId);
		}

		if (p.contactId != contactId) {
			changed = true;
			p.contactId = contactId;
		}

		if (!(p.contactName == null ? "" : p.contactName)
				.equals(contactName == null ? "" : contactName)) {
			changed = true;
			p.setContactName(contactName);
		}
		return changed;
	}

	static final int CACHE_TIME = 60000;
	private static List<IPeerListListener> listeners = new ArrayList<IPeerListListener>();

	public static boolean resolve(Peer p) {
		if (p == null)
			return false;

		// The special broadcast sid never gets resolved, as it
		// is specially created.
		if (p.sid.isBroadcast()
				|| p.cacheUntil >= SystemClock.elapsedRealtime())
			return true;

		Log.v("BatPhone",
				"Fetching details for " + p.sid.abbreviation());

		ServalDResult result = ServalD.command("reverse", "lookup",
				p.sid.toString());

		boolean resolved = false;

		for (int i = 0; i + 1 < result.outv.length; i += 2) {
			String label = new String(result.outv[i]);
			if (label.equalsIgnoreCase("did")) {
				p.did = new String(result.outv[i + 1]);
				resolved = true;
			} else if (label.equalsIgnoreCase("name")) {
				p.name = new String(result.outv[i + 1]);
				resolved = true;
			}
		}

		if (resolved) {
			p.lastSeen = SystemClock.elapsedRealtime();
			p.cacheUntil = SystemClock
					.elapsedRealtime() + CACHE_TIME;
			notifyListeners(p);
			return true;
		}

		return false;
	}

	public static void addListener(Context context, IPeerListListener callback) {
		listeners.add(callback);
		// send the peers that may already have been found. This may result
		// in the listener receiving a peer multiple times
		for (Peer p : peers.values()) {
			// recheck android contacts before informing this new listener
			// about peer info
			if (checkContacts(context.getContentResolver(), p))
				notifyListeners(p);
			else
				callback.peerChanged(p);
		}
	}

	public static void removeListener(IPeerListListener callback) {
		listeners.remove(callback);
	}

	public static void clear() {
		peers.clear();
		peers.put(broadcast.sid, broadcast);
	}

	public static void notifyListeners(Peer p) {
		for (IPeerListListener l : listeners) {
			l.peerChanged(p);
		}
	}
}
