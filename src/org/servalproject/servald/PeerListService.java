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

import android.content.ContentResolver;
import android.os.SystemClock;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.account.AccountService;
import org.servalproject.servaldna.AsyncResult;
import org.servalproject.servaldna.MdpDnaLookup;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.SubscriberId;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
	private static final String TAG="PeerListService";

	public static Peer getPeer(SubscriberId sid) {
		boolean changed = false;

		Peer p = peers.get(sid);
		if (p == null) {
			p = new Peer(sid);
			peers.put(sid, p);
			changed = true;
		}

		if (checkContacts(p))
			changed = true;

		if (p.cacheUntil < SystemClock.elapsedRealtime())
			resolve(p);

		if (changed)
			notifyListeners(p);

		return p;
	}

	private static boolean checkContacts(Peer p) {
		ContentResolver resolver = ServalBatPhoneApplication.context.getContentResolver();

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
		p.cacheContactUntil = SystemClock.elapsedRealtime()+60000;
		return changed;
	}

	static final int CACHE_TIME = 60000;
	private static List<IPeerListListener> listeners = new ArrayList<IPeerListListener>();

	public static void resolve(final Peer p){
		if (!p.isReachable())
			return;

		if (ServalBatPhoneApplication.context.isMainThread()){
			new Thread(new Runnable() {
				@Override
				public void run() {
					resolve(p);
				}
			}).start();
			return;
		}

		Log.v(TAG, "Attempting to fetch details for " + p.getSubscriberId().abbreviation());
		try {
			if (lookupSocket==null){
				lookupSocket = new MdpDnaLookup(
						ServalBatPhoneApplication.context.selector,
						new AsyncResult<ServalDCommand.LookupResult>() {
							@Override
							public void result(ServalDCommand.LookupResult nextResult) {
								Log.v(TAG, "Resolved; "+nextResult.toString());
								boolean changed = false;

								Peer p = peers.get(nextResult.subscriberId);
								if (p==null){
									p = new Peer(nextResult.subscriberId);
									peers.put(nextResult.subscriberId, p);
									changed = true;
								}

								if (!nextResult.did.equals(p.did)) {
									p.did = nextResult.did;
									changed = true;
								}
								if (!nextResult.name.equals(p.name)) {
									p.name = nextResult.name;
									changed = true;
								}

								if (p.cacheContactUntil < SystemClock.elapsedRealtime()){
									if (checkContacts(p))
										changed = true;
								}

								p.cacheUntil = SystemClock.elapsedRealtime() + CACHE_TIME;
								if (changed)
									notifyListeners(p);
							}
						});
			}

			try {
				lookupSocket.sendRequest(p.getSubscriberId(), "");
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private static void closeSocket(){
		if (ServalBatPhoneApplication.context.isMainThread()){
			new Thread(new Runnable() {
				@Override
				public void run() {
					closeSocket();
				}
			}).start();
			return;
		}
		if (lookupSocket!=null){
			lookupSocket.close();
			lookupSocket = null;
		}
	}

	public static void addListener(IPeerListListener callback) {
		listeners.add(callback);
		// send the peers that may already have been found. This may result
		// in the listener receiving a peer multiple times
		for (Peer p : peers.values()) {
			boolean changed = false;

			if (p.cacheContactUntil < SystemClock.elapsedRealtime()){
				if (checkContacts(p))
					changed = true;
			}

			if (changed)
				notifyListeners(p);
			else
				callback.peerChanged(p);

			if (p.cacheUntil < SystemClock.elapsedRealtime())
				resolve(p);
		}
	}

	public static void removeListener(IPeerListListener callback) {
		listeners.remove(callback);
	}

	public static void notifyListeners(Peer p) {
		for (IPeerListListener l : listeners) {
			l.peerChanged(p);
		}
	}

	private static void clear(){
		for (Peer p:peers.values()){
			if (p.isReachable()){
				p.linkChanged(null, -1);
				notifyListeners(p);
			}
		}
		peers.clear();
	}

	private static MdpDnaLookup lookupSocket = null;
	public static void registerMessageHandlers(ServalDMonitor monitor){
		ServalDMonitor.Messages handler = new ServalDMonitor.Messages(){
			@Override
			public void onConnect(ServalDMonitor monitor) {
				try {
					monitor.sendMessage("monitor links");
				} catch (IOException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}

			@Override
			public void onDisconnect(ServalDMonitor monitor) {
				closeSocket();
				clear();
			}

			@Override
			public int message(String cmd, Iterator<String> iArgs, InputStream in, int dataLength) throws IOException {
				ServalBatPhoneApplication app = ServalBatPhoneApplication.context;

				if(cmd.equalsIgnoreCase("LINK")) {
					try{
						int hop_count = ServalDMonitor.parseInt(iArgs.next());
						String sid = iArgs.next();
						SubscriberId transmitter = sid.equals("")?null:new SubscriberId(sid);
						SubscriberId receiver = new SubscriberId(iArgs.next());

						Log.v(TAG, "Link; "+receiver.abbreviation()+" "+(transmitter==null?"":transmitter.abbreviation())+" "+hop_count);
						boolean changed = false;

						Peer p = peers.get(receiver);
						if (p == null) {
							p = new Peer(receiver);
							peers.put(receiver, p);
							changed = true;
						}

						if (p.cacheContactUntil < SystemClock.elapsedRealtime()){
							if (checkContacts(p))
								changed = true;
						}

						boolean wasReachable = p.getTransmitter() != null;
						p.linkChanged(transmitter, hop_count);
						boolean isReachable = p.getTransmitter() != null;
						if (wasReachable!=isReachable){
							app.controlService.updatePeerCount();
							changed = true;
						}

						if (changed)
							notifyListeners(p);

						if (p.cacheUntil < SystemClock.elapsedRealtime())
							resolve(p);

					} catch (SubscriberId.InvalidHexException e) {
						IOException t = new IOException(e.getMessage());
						t.initCause(e);
						throw t;
					}
				}
				return 0;
			}
		};
		monitor.addHandler("LINK", handler);
	}
}
