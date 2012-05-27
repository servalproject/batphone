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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.servalproject.IPeerListListener;
import org.servalproject.IPeerListMonitor;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.account.AccountService;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
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
public class PeerListService extends Service {

	public static ConcurrentMap<SubscriberId, Peer> peers = new ConcurrentHashMap<SubscriberId, Peer>();

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
		} else if (!alwaysResolve)
			return p;

		if (checkContacts(resolver, p))
			changed = true;

		if (changed)
			notifyListeners(p);

		return p;
	}

	private static boolean checkContacts(ContentResolver resolver, Peer p) {
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

	static final int CACHE_TIME = 30000;
	private static List<IPeerListListener> listeners = new ArrayList<IPeerListListener>();

	public static boolean resolve(Peer p) {
		if (p == null)
			return false;

		if (p.cacheUntil >= SystemClock.elapsedRealtime())
			return true;

		// The special broadcast sid never gets resolved, as it
		// is specially created.
		if (p.sid.isBroadcast()) {
			p.lastSeen = SystemClock.elapsedRealtime();
			p.cacheUntil = SystemClock
					.elapsedRealtime() + CACHE_TIME;
			notifyListeners(p);
			return true;
		}

		Log.v("BatPhone",
				"Fetching details for " + p.sid.toString());

		ServalDResult result = ServalD.command("node", "info",
				p.sid.toString(), "resolvedid");

		if (result != null
				&& result.outv != null
				&& result.outv.length > 10
				&& result.outv[0].equals("record")
				&& result.outv[3].equals("found")) {
			try {
				SubscriberId returned = new SubscriberId(result.outv[4]);
				if (p.sid.equals(returned)) {

					p.score = Integer.parseInt(result.outv[8]);
					boolean resolved = false;

					if (!result.outv[10]
							.equals("name-not-resolved")) {
						p.name = result.outv[10];
						resolved = true;
					}
					if (!result.outv[5].equals("did-not-resolved")) {
						p.did = result.outv[5];
						resolved = true;
					}

					if (resolved) {
						p.cacheUntil = SystemClock
								.elapsedRealtime() + CACHE_TIME;
						notifyListeners(p);
						return true;
					}
				}
			} catch (SubscriberId.InvalidHexException e) {
				Log.e("BatPhone", "Received invalid SID: " + result.outv[4], e);
			}
		}
		return false;
	}

	private final Binder binder = new LocalBinder();

	private boolean running;

	public class LocalBinder extends Binder implements IPeerListMonitor {
		@Override
		public void registerListener(
				IPeerListListener callback) {
			listeners.add(callback);
			// send the peers that may already have been found. This may result
			// in the listener receiving a peer multiple times
			for (Peer p : peers.values()) {
				// recheck android contacts before informing this new listener
				// about peer info
				if (checkContacts(PeerListService.this.getContentResolver(), p))
					notifyListeners(p);
				else
					callback.peerChanged(p);
			}
		}

		@Override
		public void removeListener(IPeerListListener callback) {
			listeners.remove(callback);
		}
	}

	@Override
	public void onCreate() {
		new Thread(refresh).start();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		running = false;
	}

	private Runnable refresh = new Runnable() {
		@Override
		public void run() {
			running = true;
			Log.i("PeerListService", "searching...");
			while (running) {
				refresh();
				SystemClock.sleep(1000);
			}
		}
	};

	private void refresh() {
		// Log.i("BatPhone", "Fetching subscriber list");
		getSpecialPeers();
		if (((ServalBatPhoneApplication) getApplication()).test) {
			getRandomPeers();
		} else {
			ServalD.command(new ResultCallback() {
				@Override
				public boolean result(String value) {
					try {
						SubscriberId sid = new SubscriberId(value);
						getPeer(getContentResolver(), sid, false).lastSeen = SystemClock
								.elapsedRealtime();
						return true;
					}
					catch (SubscriberId.InvalidHexException e) {
						Log.e("PeerListService", "Received invalid SID: " + value, e);
						return false;
					}
				}
			}, "id", "peers");
		}
	}

	private void getRandomPeers() {
		int numPeersToGenerate = (int) (Math.floor(Math.random() * 20));

		for (int i = 0; i < numPeersToGenerate; i++) {
			SubscriberId sid = SubscriberId.randomSid();
			Log.i("PeerListService", sid.abbreviation());
			Peer p = getPeers().get(sid);
			if (p == null) {
				p = new Peer(sid);
				getPeers().put(sid, p);

				p.contactId = 11111111 * i;
				p.did = "" + 11111111 * i;
				p.name = "Agent Smith " + i;
				p.setContactName("Agent Smith " + i);
				Log.i("PeerListService", "Fake peer found: "
						+ p.getContactName()
						+ ", " + p.contactId + ", sid " + p.sid);

				notifyListeners(p);
			}
		}

	}

	private void getSpecialPeers() {

		SubscriberId sid = SubscriberId.broadcastSid();
		Log.i("PeerListService", sid.abbreviation());
		Peer p = getPeers().get(sid);
		if (p == null) {
			p = new Peer(sid);
			getPeers().put(sid, p);

			p.contactId = 9999999999999L;
			p.did = "*";
			p.name = "Broadcast/Everyone";
			p.setContactName("Broadcast/Everyone");
			Log.i("PeerListService", "Fake peer found: "
					+ p.getContactName()
					+ ", " + p.contactId + ", sid " + p.sid);

			notifyListeners(p);

		}

	}


	public static void notifyListeners(Peer p) {
		for (IPeerListListener l : listeners) {
			l.peerChanged(p);
		}
	}

	private static Map<SubscriberId, Peer> getPeers() {
		return peers;
	}
}
