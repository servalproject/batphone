package org.servalproject.servald;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

public class ServalDMonitor implements Runnable {
	private LocalSocket socket = null;
	private LocalSocketAddress serverSocketAddress = null;
	private LocalSocketAddress clientSocketAddress = null;

	private OutputStream os = null;

	public void createSocket() {
		if (serverSocketAddress == null)
			serverSocketAddress = new LocalSocketAddress(
					"org.servalproject.servald.monitor.socket",
					LocalSocketAddress.Namespace.ABSTRACT);
		if (serverSocketAddress == null) {
			Log.e("BatPhone", "Could not create ServalD server socket address");
			return;
		}
		// Use a filesystem binding point from inside our app dir at our end,
		// so that no one other than the server can send us messages.
		if (clientSocketAddress == null)
			clientSocketAddress = new LocalSocketAddress(
					"/data/data/org.servalproject/var/serval-node/servald-java-client.socket",
					LocalSocketAddress.Namespace.FILESYSTEM);
		if (clientSocketAddress == null) {
			Log.e("BatPhone",
					"Could not create ServalD monitor client socket address");
			return;
		}

		if (socket == null)
			socket = new LocalSocket();
		if (socket == null) {
			Log.e("BatPhone", "Could not create ServalD monitor client socket");
			return;
		}
		if (socket.isBound() == false)
			try {
				socket.bind(clientSocketAddress);
			} catch (IOException e) {
				Log.e("BatPhone",
						"Could not bind to ServalD monitor client socket: "
								+ e.toString(),
						e);
				try {
					socket.close();
					socket = null;
				} catch (IOException e1) {
					// ignore exceptions while closing, since we are just trying
					// to tidy up
				}
				return;
			}
		if (socket.isConnected() == false)
			try {
				socket.connect(serverSocketAddress);
			} catch (IOException e) {
				Log.e("BatPhone",
						"Could not connect to ServalD monitor server socket '"
								+ serverSocketAddress.toString() + "': "
								+ e.toString(),
						e);
				try {
					socket.close();
					socket = null;
				} catch (IOException e1) {
					// ignore exceptions while closing, since we are just trying
					// to tidy up
				}
				return;
			}
		try {
			os = socket.getOutputStream();
		} catch (IOException e) {
			Log.e("ServalDMonitor",
					"Failed to get output stream for socket." + e.toString(), e);
			os = null;
		}

		Log.d("MDPMonitor", "Setup MDP client socket");
		return;
	}

	public void monitorVomp(boolean yesno) {
		if (yesno)
			sendMessage("monitor vomp");
		else
			sendMessage("ignore vomp");
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
				if (socket == null || is == null || os == null) {
					createSocket(); is=null; os=null;
					// Wait a while if we can't open the socket
					if (socket == null) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							// do nothing if interrupted.
						}
					}
				}
				if (is==null) is = socket.getInputStream();
				if (os==null) os = socket.getOutputStream();

				Log.d("ServalDMonitor", "Ready to read bytes from socket");
				// See if there is anything to read
				socket.setSoTimeout(5000);
				int bytes = is.read(buffer);
				if (bytes > 0) {
					byte[] lineBytes = new byte[bytes];
					for (int i = 0; i < bytes; i++)
						if (buffer[i] >= 0)
							lineBytes[i] = buffer[i];
						else
							lineBytes[i] = '.';
					String line = new String(lineBytes, "US-ASCII");
					Log.d("ServalDMonitor",
							"Read: " + line);
					processLine(line);
				}
			} catch (Exception e) {
				Log.d("ServalDMonitor", "Failed to read bytes: " + e.toString());
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {

				}
				continue;
			}


		}

	}

	private void processLine(String line) {
		String[] words = line.split(":");
		if (words.length < 2)
			return;
		if (words[0].equals("MONITOR")) {

		} else if (words[0].equals("MONITOR")) {

		}
	}

	public void sendMessage(String string) {
		try {
			while (os == null) {
				try {
					Thread.sleep(100);
				} catch (Exception e) {
				}
			}
			os.write(string.getBytes("US-ASCII"));
			os.write('\n');
			os.flush();
			Log.e("MDPMonitor", "Wrote " + string);
		} catch (Exception e1) {
			Log.e("MDPMonitor", "Failed to send message to servald"
					+ e1.toString(), e1);
		}
	}

}
