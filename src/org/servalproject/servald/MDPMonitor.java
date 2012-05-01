package org.servalproject.servald;

import java.io.IOException;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

public class MDPMonitor {
	private static LocalSocket socket = null;
	private static LocalSocketAddress serverSocketAddress = null;
	private static LocalSocketAddress clientSocketAddress = null;

	public static void createSocket() {
		if (serverSocketAddress == null)
			serverSocketAddress = new LocalSocketAddress(
					"/data/data/org.servalproject/var/serval-node/mdp.socket",
					LocalSocketAddress.Namespace.FILESYSTEM);
		if (serverSocketAddress == null) {
			Log.e("BatPhone", "Could not create MDP server socket address");
			return;
		}
		// Use a filesystem binding point from inside our app dir at our end,
		// so that noone other than the server can send us messages.
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
		return;
	}


}
