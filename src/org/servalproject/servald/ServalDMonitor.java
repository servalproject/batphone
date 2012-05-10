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

	byte[] data = new byte[VoMP.MAX_AUDIO_BYTES];
	int dataBytes = 0;

	public synchronized void createSocket() {
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

		Log.d("ServalDMonitor", "Waiting for monitor socket to connect");
		// Allow some time for socket to finish connecting
		int tries = 15;
		while (socket.isConnected() == false && (tries > 0)) {
			try {
				Thread.sleep(250);
			} catch (Exception e) {
			}
			tries--;
		}
		if (socket.isConnected() == false) {
			Log.e("ServalDMonitor", "Monitor socket failed to connect");
			cleanupStreams();
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
			try {
				Thread.sleep(100);
			} catch (Exception e2) {
			}
			cleanupStreams();
			return;
		}

		Log.d("ServalDMonitor", "Monitor socket connected and ready");

		socketConnectTime = SystemClock.elapsedRealtime();
		return;
	}

	long lastCleanupTime = 0;
	int cleanupCount = 0;
	int reconnectBackoff = 1000;

	private synchronized void cleanupSocket()
	{
		Log.d("ServalDMonitor", "Cleaning up old socket and streams");
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

	private synchronized void cleanupStreams()
	{
		if (is != null) {
			try {
				is.close();
			} catch (Exception e2) {
			}
		}
		is = null;
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

	private class ReadData {
		public byte[] buffer = new byte[8192];
		public int bufferOffset = 0;
		public int bufferBytes = 0;

		public byte nextByte(InputStream is) throws IOException {
			if (bufferOffset >= bufferBytes)
				read(is);
			if (bufferOffset >= bufferBytes)
				return 0;
			return buffer[bufferOffset++];
		}

		public void read(InputStream is) throws IOException {
			// only call this when buffer is empty
			bufferBytes = 0;
			bufferOffset = 0;
			bufferBytes = is.read(buffer);
			dump("ReadData object read", buffer, 0, bufferBytes);
		}
	}

	@Override
	public void run() {
		ReadData d = new ReadData();

		Log.d("ServalDMonitor", "Starting");

		StringBuilder line = new StringBuilder(256);

		while (stopMe == false) {

			try {
				// Make sure we have the sockets we need
				while (stopMe == false
						&& (socket == null || is == null || os == null)) {
					if (SystemClock.elapsedRealtime()<dontReconnectUntil) {
						try {
							Log.d("ServalDMonitor", "Waiting "
									+ (dontReconnectUntil
									- SystemClock.elapsedRealtime())
									+ "ms before reconnecting");
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
				socket.setSoTimeout(60000); // sleep for a long time if needed
				if (socketConnectTime > SystemClock.elapsedRealtime() + 5000)
					// Reset reconnection backoff, but only after we have
					// been connected for a while
					reconnectBackoff = 1000;
				byte b = d.nextByte(is);
				while (d.bufferOffset<d.bufferBytes) {
					if (b >= 0) {
						if (b == '\n') {
							if (line.length() > 0)
								processLine(line.toString(), d);
							line.setLength(0);
						} else
							line.append((char) b);
					}
					else
						line.append('.');
					b = d.nextByte(is);
				}
			} catch (Exception e) {
				if (e instanceof IOException) {
					if (e.getMessage().equals("socket closed")) {
						Log.d("ServalDMonitor",
								"Looks like monitor socket closed.");
						cleanupSocket();
						try {
							Thread.sleep(1000);
						} catch (Exception e1) {
						}
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

	private void processLine(String line, ReadData d) {
		Log.d("ServalDMonitor", "Read monitor message: " + line);
		String[] words = line.split(":");
		int ofs = 0;
		if (words.length < 2)
			return;
		if (words[0].charAt(0) == '*') {
			// Message with data
			dataBytes = Integer.parseInt(words[0].substring(1));
			if (dataBytes<0) {
				Log.d("ServalDMonitor","Message has data block with negative length: "+line);
				return;
			} else if (dataBytes > VoMP.MAX_AUDIO_BYTES) {
				// Read bytes and discard
				Log.d("ServalDMonitor",
						"Message has data block with excessive length: " + line);
				while (dataBytes>0) {
					try {
						d.nextByte(is);
						dataBytes--;
					} catch (Exception e) {
						// Stop trying if we get an error
						break;
					}
				}
				return;
			}
			// We have a reasonable amount of data to read
			// Log.d("ServalDMonitor", "Reading " + dataBytes
			// + " of data (ReadData has "
			// + (d.bufferBytes - d.bufferOffset) + " bytes waiting)");
			try {
				int offset = 0;
				while (offset < dataBytes) {
					data[offset++] = d.nextByte(is);
				}
				// Log.d("ServalDMonitor", "Read " + offset + " bytes.");
				// dump("read associated data", data, 0, data.length);
			} catch (Exception e) {
				Log.d("ServalDMonitor",
						"Failed to read data associated with monitor message:"
								+ e.toString(), e);
				return;
			}

			// Okay, we have the data, so shuffle words down, and keep parsing
			ofs = 1;
		} else
			dataBytes = 0;

		if (words[ofs].equals("CLOSE")) {
			// servald doesn't want to talk to us
			// don't retry for a minute
			cleanupSocket();
			dontReconnectUntil = SystemClock.elapsedRealtime();
		} else if (words[ofs].equals("KEEPALIVE")) {
			// send keep alive to anyone who cares
			try {
				int local_session = Integer.parseInt(words[ofs + 1], 16);
				keepAlive(local_session);
			} catch (Exception e) {
				// catch integer parse exceptions
			}
		} else if (words[ofs].equals("MONITOR")) {
			// returns monitor status
		} else if (words[ofs].equals("AUDIOPACKET")) {
			// AUDIOPACKET:065384:66b07a:5:5:8:2701:2720
			int local_session, remote_session;
			int local_state, remote_state;
			int codec;
			int start_time, end_time;
			try {
				local_session = Integer.parseInt(words[ofs + 1], 16);
				remote_session = Integer.parseInt(words[ofs + 2], 16);
				local_state = Integer.parseInt(words[ofs + 3]);
				remote_state = Integer.parseInt(words[ofs + 4]);
				codec = Integer.parseInt(words[ofs + 5]);
				start_time = Integer.parseInt(words[ofs + 6]);
				end_time = Integer.parseInt(words[ofs + 7]);
				receivedAudio(local_session, start_time, end_time, codec, data,
						dataBytes);
				// If we have audio, the call must be alive.
				keepAlive(local_session);
			} catch (Exception e) {
				// catch parse errors
			}

		} else if (words[ofs].equals("CALLSTATUS")) {
			int local_session,remote_session;
			int local_state, remote_state, fast_audio;
			SubscriberId local_sid, remote_sid;
			String local_did, remote_did;
			try {
				local_session = Integer.parseInt(words[ofs + 1], 16);
				remote_session = Integer.parseInt(words[ofs + 2], 16);
				local_state = Integer.parseInt(words[ofs + 3]);
				remote_state = Integer.parseInt(words[ofs + 4]);
				fast_audio = Integer.parseInt(words[ofs + 5]);
				local_sid = new SubscriberId(words[ofs + 6]);
				remote_sid = new SubscriberId(words[ofs + 7]);
				if (words.length > (ofs + 8))
					local_did = words[ofs + 8];
				else
					local_did = "<unknown>";
				if (words.length > (ofs + 9))
					remote_did = words[ofs + 9];
				else
					remote_did = "<no caller id>";
				notifyCallStatus(local_session, remote_session, local_state,
						remote_state, fast_audio,
						local_sid, remote_sid, local_did, remote_did);
			} catch (Exception e) {
				// catch parse errors
				Log.d("ServalDMonitor",
						"Failed to parse and announce revised call status: "
								+ e.toString(), e);
				Log.d("ServalDMonitor", "words = " + words);
			}
			// localtoken:remotetoken:localstate:remotestate
		}
		return;
	}

	private void dump(String string, byte[] data, int offset, int lengthIn) {
		int length=lengthIn-offset;
		int i,j;
		StringBuilder sb = new StringBuilder();
		for(i=0;i<length;i+=16) {
			sb.setLength(0);
			sb.append(Integer.toHexString(i));
			sb.append(" :");
			for (j = 0; j < 16; j++) {
				int v = data[offset + i + j];
				if (v < 0)
					v += 256;
				sb.append(" ");
				if (i + j < length) {
					sb.append(Integer.toHexString(v));
				} else
					sb.append("   ");
			}
			sb.append(" ");
			for (j = 0; j < 16; j++) {
				int v = data[offset + i + j];
				if (v < 0)
					v += 256;
				if (i + j < length) {
					if (v >= 0x20 && v < 0x7d)
						sb.append((char) v);
					else
						sb.append('.');
				}
			}
			// Log.d("Dump:" + string, sb.toString());
		}

	}

	protected void keepAlive(int local_session) {
		// Callback for overriding to get notification of VoMP call keep-alives
		return;
	}

	protected void receivedAudio(int local_session, int start_time,
			int end_time,
			int codec, byte[] data2, int dataBytes2) {
		// For overriding by parties interested in receiving audio.

	}

	public synchronized void sendMessage(String string) {
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
			// Log.e("ServalDMonitor", "Wrote " + string);
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
			int remote_state, int fast_audio, SubscriberId local_sid,
			SubscriberId remote_sid,
			String local_did, String remote_did) {
		return;
	}

	public void stop() {
		// TODO Auto-generated method stub
		stopMe = true;
	}

	public synchronized void sendMessageAndData(String string, byte[] block) {
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
			os.write('*');
			os.write(((block.length) + ":").getBytes());
			os.write(string.getBytes("US-ASCII"));
			os.write('\n');
			os.write(block);
			os.flush();
			// Log.e("ServalDMonitor", "Wrote " + "*" + block.length + ":"
			// + string + "\n<data>");
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
}
