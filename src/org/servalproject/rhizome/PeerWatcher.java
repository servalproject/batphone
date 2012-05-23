package org.servalproject.rhizome;

import android.util.Log;

/**
 * Thread to watch for new files in the Rhizome store.
 */
public class PeerWatcher extends Thread {

	private boolean run = true;

	public PeerWatcher() {
		Log.w(Rhizome.TAG, "PeerWatcher() NOT IMPLEMENTED");
	}

	@Override
	public void run() {
		Log.i(Rhizome.TAG, "rhizome.PeerWatcher.run()");
		super.run();
		while (run) {
			try {
				sleep(500);
			}
			catch (InterruptedException e) {
			}
		}
		Log.i(Rhizome.TAG, "rhizome.PeerWatcher.run() EXIT");
	}

	public void ceaseAndDesist() {
		run = false;
	}

}
