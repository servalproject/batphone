package org.servalproject.servald;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

public class MDPMonitor implements Runnable {
	private LocalSocket socket = null;
	private LocalSocketAddress serverSocketAddress = null;
	private LocalSocketAddress clientSocketAddress = null;

	private OutputStream os = null;

	public void createSocket() {
		if (serverSocketAddress == null)
			serverSocketAddress = new LocalSocketAddress(
					"/data/data/org.servalproject/var/serval-node/mdp.socket",
					LocalSocketAddress.Namespace.FILESYSTEM);
		if (serverSocketAddress == null) {
			Log.e("BatPhone", "Could not create MDP server socket address");
			return;
		}
		// Use a filesystem binding point from inside our app dir at our end,
		// so that no one other than the server can send us messages.
		if (clientSocketAddress == null)
			clientSocketAddress = new LocalSocketAddress(
					"/data/data/org.servalproject/var/serval-node/mdp-java-client.socket",
					LocalSocketAddress.Namespace.FILESYSTEM);
		if (clientSocketAddress == null) {
			Log.e("BatPhone", "Could not create MDP client socket address");
			return;
		}

		if (socket == null)
			socket = new LocalSocket();
		if (socket == null) {
			Log.e("BatPhone", "Could not create MDP socket");
			return;
		}
		if (socket.isBound() == false)
			try {
				socket.bind(clientSocketAddress);
			} catch (IOException e) {
				Log.e("BatPhone",
						"Could not bind to MDP client socket: " + e.toString(),
						e);
			}
		if (socket.isConnected() == false)
			try {
				socket.connect(serverSocketAddress);
			} catch (IOException e) {
				Log.e("BatPhone",
						"Could not connect to MDP server socket: "
								+ e.toString(),
						e);
			}
		try {
			os = socket.getOutputStream();
		} catch (IOException e) {
			Log.e("MDPMonitor",
					"Failed to get output stream for socket." + e.toString(), e);
		}

		Log.d("MDPMonitor", "Setup MDP client socket");
		return;
	}

	public void monitorVomp(boolean yesno) {
		if (os == null) {
			Log.i("MDPMonitor", "MDP client socket not available");
			return;
		}
		MDPVoMPEvent e = new MDPVoMPEvent();
		e.flags = MDPVoMPEvent.VOMPEVENT_REGISTERINTEREST;
		try {
			os.write(e.toByteArray());
		} catch (IOException e1) {
			Log.e("MDPMonitor", "Failed to send VOMPEVENT_REGISTERINTEREST: "
					+ e1.toString(), e1);
		}
	}

	public void monitorRhizome(boolean yesno) {
	}

	@Override
	public void run() {
		InputStream is=null;
		byte[] buffer = new byte[8192];

		while (true) {

			try {
				// Make sure we have the sockets we need
				if (socket == null) {
					createSocket(); is=null; os=null;
				}
				if (is==null) is = socket.getInputStream();
				if (os==null) os = socket.getOutputStream();

				Log.d("MDPMonitor", "Ready to read bytes from socket");
				// See if there is anything to read
				int bytes = is.read(buffer);
				Log.d("MDPMonitor", "Read " + bytes + " bytes.");

			} catch (Exception e) {
				Log.d("MDPMonitor", "Failed to read bytes: " + e.toString());
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {

				}
				continue;
			}


		}

	}

}
