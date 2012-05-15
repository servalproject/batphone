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
package org.servalproject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.servalproject.account.AccountService;
import org.servalproject.servald.ResultCallback;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.SubscriberId;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
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

	public Map<SubscriberId, Peer> peers = new HashMap<SubscriberId, Peer>();

	private final Binder binder = new LocalBinder();

	private List<IPeerListListener> listeners = new ArrayList<IPeerListListener>();

	private boolean searching;

	public class LocalBinder extends Binder implements IPeerListMonitor {
		@Override
		public void registerListener(
				IPeerListListener callback) {
			listeners.add(callback);
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
		searching = false;
	}

	private Runnable refresh = new Runnable() {
		@Override
		public void run() {
			searching = true;
			Log.i("PeerListService", "searching...");
			while (searching) {
				refresh();
				SystemClock.sleep(1000);
			}
		}
	};

	private void refresh() {
		new AsyncTask<Void, Peer, Void>() {
			@Override
			protected void onProgressUpdate(Peer... values) {
				notifyListeners(values);
			}

			@Override
			protected void onPostExecute(Void result) {
				// searching = false;
			}

			@Override
			protected Void doInBackground(Void... params) {
				Log.i("BatPhone", "Fetching subscriber list");
				if (((ServalBatPhoneApplication) getApplication()).test) {
					getRandomPeers();
				} else {
					ServalD.command(new ResultCallback() {
						@Override
						public boolean result(String value) {
							SubscriberId sid = new SubscriberId(value);
							Peer p = getPeers().get(sid);
							if (p == null) {
								p = new Peer();
								p.sid = sid;
								getPeers().put(sid, p);

								p.contactId = AccountService.getContactId(
										getContentResolver(), sid);

								if (p.contactId >= 0)
									p.contactName = AccountService
											.getContactName(
													getContentResolver(),
													p.contactId);

								publishProgress(p);
								// unresolved.add(p);
							}
							return true;
						}
					}, "id", "peers");
				}
				// TODO these queries need to be done in parallel!!!!
				// perhaps internal to servald?

				// Iterator<Peer> i = unresolved.iterator();
				// while (i.hasNext()) {
				// Peer p = i.next();
				//
				// Log.v("BatPhone",
				// "Fetching details for " + p.sid.toString());
				//
				// ServalDResult result = ServalD.command("node", "info",
				// p.sid.toString(), "resolvedid");
				//
				// StringBuilder sb = new StringBuilder("{");
				// for (int j = 0; j < result.outv.length; j++) {
				// if (j > 0)
				// sb.append(", ");
				// sb.append(result.outv[j]);
				// }
				// sb.append('}');
				// Log.v("BatPhone", "Output: " + sb);
				// if (result != null
				// && result.outv != null
				// && result.outv.length > 10
				// && result.outv[0].equals("record")
				// && result.outv[3].equals("found")) {
				// p.score = Integer.parseInt(result.outv[8]);
				// boolean resolved = false;
				//
				// if (!result.outv[10].equals("name-not-resolved")) {
				// p.name = result.outv[10];
				// resolved = true;
				// }
				// if (!result.outv[5].equals("did-not-resolved")) {
				// p.did = result.outv[5];
				// resolved = true;
				// }
				//
				// publishProgress(p);
				// if (resolved)
				// i.remove();
				// }
				// }

				return null;
			}

			private void getRandomPeers() {
				int numPeersToGenerate = (int) (Math.floor(Math.random() * 20));

				for (int i = 0; i < numPeersToGenerate; i++) {

					// generate a 64 char fake sid
					String sidString = "";
					for (int j = 0; j < 32; j++) {
						if (j < 5) {
							if (i < 10) {
								sidString += "0" + i;
							} else {
								sidString += i;
							}
						} else {
							sidString += "00";
						}
					}
					Log.i("PeerListService", sidString.length() + "-"
							+ sidString);
					SubscriberId sid = new SubscriberId(sidString);
					Peer p = getPeers().get(sid);
					if (p == null) {
						p = new Peer();
						p.sid = sid;
						getPeers().put(sid, p);

						p.contactId = 11111111 * i;

						if (p.contactId >= 0)
							p.contactName = "Agent Smith " + i;
						Log.i("PeerListService", "Fake peer found: "
								+ p.contactName
								+ ", " + p.contactId + ", sid " + p.sid);

						publishProgress(p);
					}
				}

			}

		}.execute();
	}

	private void notifyListeners(Peer[] values) {
		getPeers().put(values[0].sid, values[0]);
		for (IPeerListListener l : listeners) {
			l.newPeer(values[0]);
		}
	}



	private Map<SubscriberId, Peer> getPeers() {
		return peers;
	}
}