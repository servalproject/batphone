package org.servalproject.servald;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.batphone.VoMP;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.SystemClock;
import android.util.Log;

public class ServalDMonitor implements Runnable {
	private LocalSocket socket = null;
	private LocalSocketAddress serverSocketAddress = null;
	private LocalSocketAddress clientSocketAddress = null;

	private OutputStream os = null;
	private InputStream is = null;
	private boolean stopMe = false;
	private long dontReconnectUntil = 0;
	private long socketConnectTime;

	public void createSocket() {
		cleanupStreams();
		if (dontReconnectUntil > SystemClock.elapsedRealtime())
			return;
		if (serverSocketAddress == null)
			serverSocketAddress = new LocalSocketAddress(
					"org.servalproject.servald.monitor.socket",
					LocalSocketAddress.Namespace.ABSTRACT);
		if (serverSocketAddress == null) {
			Log.e("BatPhone", "Could not create ServalD server socket address");
			reconnectBackoff *= 2;
			if (reconnectBackoff > 120000)
				reconnectBackoff = 120000;
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
			reconnectBackoff *= 2;
			if (reconnectBackoff > 120000)
				reconnectBackoff = 120000;
			return;
		}

		if (socket == null)
			socket = new LocalSocket();
		if (socket == null) {
			Log.e("BatPhone", "Could not create ServalD monitor client socket");
			reconnectBackoff *= 2;
			if (reconnectBackoff > 120000)
				reconnectBackoff = 120000;
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
				cleanupSocket();
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
				cleanupSocket();
				return;
			}
		try {
			if (is == null)
				is = socket.getInputStream();
			if (os == null)
				os = socket.getOutputStream();
		} catch (IOException e) {
			Log.e("ServalDMonitor",
					"Failed to get input &/or output stream for socket."
							+ e.toString(), e);
			cleanupStreams();
		}

		Log.d("MDPMonitor", "Setup MDP client socket");

		socketConnectTime = SystemClock.elapsedRealtime();
		return;
	}

	long lastCleanupTime = 0;
	int cleanupCount = 0;
	int reconnectBackoff = 1000;
	private void cleanupSocket()
	{
		if (lastCleanupTime == (SystemClock.elapsedRealtime() / 3000))
			cleanupCount++;
		else {
			lastCleanupTime = (SystemClock.elapsedRealtime() / 3000);
			cleanupCount = 1;
		}
		if (cleanupCount > 5) {
			Log.d("ServalDMonitor",
					"Excessive monitor socket shutdowns.  Waiting a while before reconnecting.");
			dontReconnectUntil = SystemClock.elapsedRealtime()
					+ reconnectBackoff;
			reconnectBackoff *= 2;
			if (reconnectBackoff > 120000)
				reconnectBackoff = 120000;
		}
		cleanupStreams();
		try {
			socket.close();
		} catch (IOException e1) {
			// ignore exceptions while closing, since we are just trying
			// to tidy up
		}
		socket = null;
	}

	private void cleanupStreams()
	{
		if (is != null) {
			try {
				is.close();
			} catch (Exception e2) {
			}
		}
		os = null;
		if (os != null) {
			try {
				os.close();
			} catch (Exception e2) {
			}
		}
		os = null;
	}

	public void monitorVomp(boolean yesno) {
		if (yesno)
			sendMessage("monitor vomp");
		else
			sendMessage("ignore vomp");
	}

	public void monitorRhizome(boolean yesno) {
		if (yesno)
			sendMessage("monitor rhizome");
		else
			sendMessage("ignore rhizome");
	}

	@Override
	public void run() {
		byte[] buffer = new byte[8192];

		Log.d("ServalDMonitor", "Starting");

		StringBuilder line = new StringBuilder(256);

		while (stopMe == false) {

			try {
				// Make sure we have the sockets we need
				while (stopMe == false
						&& (socket == null || is == null || os == null)) {
					if (SystemClock.elapsedRealtime()<dontReconnectUntil) {
						try {
							Thread.sleep(dontReconnectUntil
									- SystemClock.elapsedRealtime());
						} catch (Exception e) {
							// interrupted during sleep, which is okay.
						}
					}
					createSocket();
					// Wait a while if we can't open the socket
					if (socket != null) {
						ServalBatPhoneApplication.context.servaldMonitor
								.monitorVomp(true);
						ServalBatPhoneApplication.context.servaldMonitor
								.monitorRhizome(true);
					}
				}

				// See if there is anything to read
				socket.setSoTimeout(60000); // sleep for a long time
				int bytes = is.read(buffer);
				if (socketConnectTime > SystemClock.elapsedRealtime() + 5000)
					// Reset reconnection backoff, but only after we have
					// been connected for a while
					reconnectBackoff = 1000;
				if (bytes > 0) {
					for (int i = 0; i < bytes; i++)
						if (buffer[i] >= 0) {
							if (buffer[i]=='\n') {
								processLine(line.toString(), buffer, i + 1);
								line.setLength(0);
							} else
								line.append((char) buffer[i]);
						}
						else
							line.append('.');

				} else if (bytes == -1) {
					// Socket appears to have died.
					// Clean everything up so that we will re-open the socket
					Log.d("ServalDMonitor",
							"Looks like monitor socket died, re-connecting.");
					cleanupSocket();
				}
			} catch (Exception e) {
				if (e instanceof IOException) {
					if (e.getMessage().equals("socket closed")) {
						Log.d("ServalDMonitor",
								"Looks like monitor socket died, re-connecting.");
						cleanupSocket();
						continue;
					} else if (e.getMessage().equals("Try again")) {
						Log.d("ServalDMonitor",
								"Timeout reading from monitor socket, which is not a problem.");
						continue;
					}
				}
				Log.d("ServalDMonitor",
						"Unhandled exception while reading from monitor interface: "
								+ e.toString(), e);
				cleanupSocket();
				continue;
			}


		}

	}

	private int processLine(String line, byte[] buffer, int bufferOffset) {
		Log.d("ServalDMonitor", "Read monitor message: " + line);
		String[] words = line.split(":");
		int ofs = 0;
		if (words.length < 2)
			return bufferOffset;
		if (words[0].charAt(0) == '*') {
			// Message with data
			int dataBytes = Integer.parseInt(words[0].substring(1));
			if (dataBytes<0) {
				Log.d("ServalDMonitor","Message has data block with negative length: "+line);
				return bufferOffset;
			} else if (dataBytes > VoMP.MAX_AUDIO_BYTES) {
				// Read bytes and discard
				Log.d("ServalDMonitor",
						"Message has data block with excessive length: " + line);
				while (dataBytes>0) {
					// Use up what data we have first
					while (bufferOffset<buffer.length&&dataBytes>0) {
						dataBytes--; bufferOffset++;
					}
					int thisBytes=dataBytes;
					if (thisBytes>8192) thisBytes=8192;
					byte[] thisBuffer = new byte[thisBytes];
					int r = 0;
					try {
						r = is.read(thisBuffer);
					} catch (Exception e) {
						// Stop trying if we get an error
						break;
					}
					if (r > 0)
						dataBytes -= r;
				}
				return bufferOffset;
			}
			// We have a reasonable amount of data to read
			byte[] data = new byte[dataBytes];
			int r = 0;
			try {
				r = is.read(data);
			} catch (Exception e) {
				Log.d("ServalDMonitor",
						"Failed to read data associated with monitor message");
				return bufferOffset;
			}

			// Okay, we have the data, so shuffle words down, and keep parsing
			ofs = 1;
		}

		if (words[ofs].equals("CLOSE")) {
			// servald doesn't want to talk to us
			// don't retry for a minute
			cleanupSocket();
			dontReconnectUntil = SystemClock.elapsedRealtime();
		} else if (words[ofs].equals("MONITOR")) {
			// returns monitor status
		} else if (words[ofs].equals("CALLSTATUS")) {
			int local_session,remote_session;
			int local_state,remote_state;
			SubscriberId local_sid, remote_sid;
			String local_did, remote_did;
			try {
				local_session = Integer.parseInt(words[ofs + 1], 16);
				remote_session = Integer.parseInt(words[ofs + 2], 16);
				local_state = Integer.parseInt(words[ofs + 3]);
				remote_state = Integer.parseInt(words[ofs + 4]);
				local_sid = new SubscriberId(words[ofs + 5]);
				remote_sid = new SubscriberId(words[ofs + 6]);
				if (words.length > (ofs + 7))
					local_did = words[ofs + 7];
				else
					local_did = "<unknown>";
				if (words.length > (ofs + 8))
					remote_did = words[ofs + 8];
				else
					remote_did = "<no caller id>";
				notifyCallStatus(local_session, remote_session, local_state,
						remote_state, local_sid, remote_sid, local_did,
						remote_did);
			} catch (Exception e) {
				// catch parse errors
				Log.d("ServalDMonitor",
						"Failed to parse and announce revised call status: "
								+ e.toString(), e);
				Log.d("ServalDMonitor", "words = " + words);
			}
			// localtoken:remotetoken:localstate:remotestate
		}
		return bufferOffset;
	}

	public void sendMessage(String string) {
		try {
			while (os == null) {
				createSocket();
				if (os != null)
					break;
				try {
					Thread.sleep(100);
				} catch (Exception e) {
				}
			}
			os.write(string.getBytes("US-ASCII"));
			os.write('\n');
			os.flush();
			Log.e("ServalDMonitor", "Wrote " + string);
		} catch (Exception e1) {
			if (e1.getMessage().equals("Broken pipe")) {
				// servald closed the socket (or got clobbered)
				Log.d("ServalDMonitor",
						"Looks like servald shut our monitor socket.");
				cleanupSocket();
				return;
			}
			Log.e("ServalDMonitor",
					"Failed to send message to servald, reopening socket");
			cleanupSocket();
		}
	}

	public boolean ready() {
		if (os != null)
			return true;
		else
			return false;
	}

	// Methods for overriding
	protected void notifyCallStatus(int local_id, int remote_id,
			int local_state,
			int remote_state, SubscriberId local_sid, SubscriberId remote_sid,
			String local_did, String remote_did) {
		return;
	}

	public void stop() {
		// TODO Auto-generated method stub
		stopMe = true;
	}
}
