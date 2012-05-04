package org.servalproject.servald;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.servalproject.batphone.VoMP;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

public class ServalDMonitor implements Runnable {
	private LocalSocket socket = null;
	private LocalSocketAddress serverSocketAddress = null;
	private LocalSocketAddress clientSocketAddress = null;

	private OutputStream os = null;
	private InputStream is = null;
	private boolean stopMe = false;

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
		byte[] buffer = new byte[8192];

		StringBuilder line = new StringBuilder(256);

		while (stopMe == false) {

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

				// See if there is anything to read
				socket.setSoTimeout(60000); // sleep for a long time
				int bytes = is.read(buffer);
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

				}
			} catch (Exception e) {
				// Don't wait too long, in case we are in a call.
				try {
					Thread.sleep(10);
				} catch (InterruptedException e1) {

				}
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

		if (words[ofs].equals("MONITOR")) {
			// status message -- just ignore
		} else if (words[ofs].equals("MONITOR")) {
			// returns monitor status
		} else if (words[ofs].equals("CALLSTATUS")) {
			int local_session,remote_session;
			int local_state,remote_state;
			try {
				local_session = Integer.parseInt(words[ofs + 1], 16);
				remote_session = Integer.parseInt(words[ofs + 2], 16);
				local_state = Integer.parseInt(words[ofs + 3]);
				remote_state = Integer.parseInt(words[ofs + 4]);
				notifyCallStatus(local_session, remote_session, local_state,
						remote_state);
			} catch (Exception e) {
				// catch parse errors
				Log.d("ServalDMonitor",
						"Failed to parse and announce revised call status: "
								+ e.toString());
			}
			// localtoken:remotetoken:localstate:remotestate
		}
		return bufferOffset;
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

	public boolean ready() {
		if (os != null)
			return true;
		else
			return false;
	}

	// Methods for overriding
	protected void notifyCallStatus(int local_id, int remote_id,
			int local_state,
			int remote_state) {
		return;
	}

	public void stop() {
		// TODO Auto-generated method stub
		stopMe = true;
	}
}
