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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import org.servalproject.ServalBatPhoneApplication;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;
import android.util.Log;

public class ServalDMonitor implements Runnable {
	private LocalSocket socket = null;
	private LocalSocketAddress serverSocketAddress = new LocalSocketAddress(
			"org.servalproject.servald.monitor.socket",
			LocalSocketAddress.Namespace.ABSTRACT);

	// Use a filesystem binding point from inside our app dir at our end,
	// so that no one other than the server can send us messages.
	private LocalSocketAddress clientSocketAddress = new LocalSocketAddress(
			"/data/data/org.servalproject/var/serval-node/servald-java-client.socket",
			LocalSocketAddress.Namespace.FILESYSTEM);

	private OutputStream os = null;
	private InputStream is = null;
	private boolean stopMe = false;
	// WARNING, absolutely kills phone calls logging every audio packet in both
	// directions
	private boolean logMessages = false;

	int dataBytes = 0;
	private Messages messages;

	public static final int MONITOR_VOMP = (1 << 0);
	public static final int MONITOR_RHIZOME = (1 << 1);
	public static final int MONITOR_PEERS = (1 << 2);
	public static final int MONITOR_DNAHELPER = (1 << 3);

	// sigh, Integer.parseInt is a bit slow...
	public static int parseInt(String value) {
		int len = value.length();
		if (len == 0)
			throw new NumberFormatException("Invalid int: \"" + value + "\"");

		int ret = 0;
		boolean neg = false;

		for (int i = 0; i < len; i++) {
			char c = value.charAt(i);
			if (c >= '0' && c <= '9')
				ret = (ret << 1) + (ret << 3) + (c - '0');
			else if (c == '-')
				neg = true;
			else if (i == 0)
				throw new NumberFormatException("Invalid int: \"" + value
						+ "\"");
		}
		if (neg)
			return -ret;
		return ret;
	}

	// sigh, Integer.parseInt is a bit slow...
	public static int parseIntHex(String value) {
		int len = value.length();
		if (len == 0)
			throw new NumberFormatException("Invalid int: \"" + value + "\"");

		int ret = 0;
		boolean neg = false;

		for (int i = 0; i < len; i++) {
			char c = value.charAt(i);
			if (c >= '0' && c <= '9')
				ret = (ret << 4) + (c - '0');
			else if (c >= 'a' && c <= 'f')
				ret = (ret << 4) + 10 + (c - 'a');
			else if (c >= 'A' && c <= 'F')
				ret = (ret << 4) + 10 + (c - 'A');
			else if (c == '-')
				neg = true;
			else if (i == 0)
				throw new NumberFormatException("Invalid int: \"" + value
						+ "\"");
		}
		if (neg)
			return -ret;
		return ret;
	}

	public static long parseLong(String value) {
		int len = value.length();
		boolean neg = false;
		int i = 0;
		if (len != 0 && value.charAt(i) == '-') {
			neg = true;
			++i;
		}
		if (i >= len)
			throw new NumberFormatException("Invalid long: \"" + value + "\"");
		long ret = 0;
		for (; i < len; i++) {
			char c = value.charAt(i);
			if (c >= '0' && c <= '9')
				ret = (ret << 1) + (ret << 3) + (c - '0');
			else
				throw new NumberFormatException("Invalid long: \"" + value + "\"");
		}
		return neg ? -ret : ret;
	}

	public ServalDMonitor(Messages messages) {
		this.messages = messages;
	}

	public interface Message {
		public int message(String cmd, Iterator<String> iArgs,
				InputStream in, int dataLength) throws IOException;
	}

	public Map<String, Message> handlers = new HashMap<String, Message>();

	public interface Messages {
		public void connected();

		public int message(String cmd, Iterator<String> iArgs,
				InputStream in, int dataLength) throws IOException;
	}

	// Attempt to connect to the servald monitor interface
	private synchronized void createSocket() throws IOException {
		if (socket != null)
			return;

		if (stopMe)
			throw new IOException("Stopping");

		Log.v("ServalDMonitor", "Creating socket " + clientSocketAddress.getName());
		LocalSocket socket = new LocalSocket();
		try {
			socket.bind(clientSocketAddress);
			socket.setSoTimeout(1000);
			socket.connect(serverSocketAddress);
			socket.setSoTimeout(60000);
			is = new BufferedInputStream(
					socket.getInputStream(), 640);
			if (logMessages) {
				is = new DumpInputStream(is);
			}
			os = new BufferedOutputStream(socket.getOutputStream(), 640);
			this.socket = socket;

			if (this.messages != null)
				messages.connected();

			return;
		} catch (IOException e) {
			try {
				if (socket != null)
					socket.close();
			} catch (IOException e1) {
				Log.e("ServalDMonitor", e1.getMessage(), e1);
			}
			throw e;
		}
	}

	private void cleanupSocket() {
		close(is);
		is = null;
		close(os);
		os = null;
		try {
			if (socket != null)
				socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		socket = null;
	}

	private void close(Closeable c) {
		if (c == null)
			return;
		try {
			c.close();
		} catch (IOException e) {
			Log.e("ServalDMonitor", e.getMessage(), e);
		}
	}

	private Thread currentThread;

	private void reconnect() throws IOException, InterruptedException,
			ServalDFailureException, ServalDInterfaceError {
		while (socket == null) {
			try {
				createSocket();
				return;
			} catch (IOException e) {
				if (ServalD.uptime() > 5000) {
					// assume servald is dead and must be restarted
					Log.v("ServalDMonitor",
							"servald appears to have died, I can't reconnect to it. Forcing a restart");
					try {
						ServalD.serverStop();
					} catch (Exception e2) {
						// ignore all failures, at least we tried...
						Log.e("ServalDMonitor", e2.toString(), e2);
					}
					ServalD.serverStart();
					continue;
				}

				// throttle connection attempts
				Thread.sleep(100);
				throw e;
			}
		}
	}

	@Override
	public void run() {
		Log.d("ServalDMonitor", "Starting");
		currentThread = Thread.currentThread();
		// boost the priority so we can read incoming audio frames with low
		// latency
		Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
		while (!stopMe) {

			try {
				// Make sure we have the sockets we need
				if (socket == null)
					reconnect();

				// See if there is anything to read
				processInput();
			} catch (ServalDFailureException e){
				ServalBatPhoneApplication.context.displayToastMessage("Unable to control servald deamon");
			} catch (ServalDInterfaceError e) {
				ServalBatPhoneApplication.context
						.displayToastMessage("Unable to control servald deamon");
			} catch (EOFException e) {
				cleanupSocket();
			} catch (IOException e) {
				if ("Try again".equals(e.getMessage()))
					continue;

				Log.e("ServalDMonitor", e.getMessage(), e);
				cleanupSocket();
			} catch (Exception e) {
				Log.e("ServalDMonitor", e.getMessage(), e);
			}
		}
		currentThread = null;
	}

	// one set of buffers for parsing incoming commands
	// note that these fields can only be accessed from within a synchronised
	// block in processInput()
	private final char fieldBuffer[] = new char[128];
	private final String fields[] = new String[32];
	private int argsIndex = 0;
	private int numFields;
	private Iterator<String> iArgs = new Iterator<String>() {
		@Override
		public boolean hasNext() {
			return argsIndex < numFields;
		}

		@Override
		public String next() {
			if (!hasNext())
				throw new NoSuchElementException();
			return fields[argsIndex++];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	};

	private void readCommand(InputStream in) throws IOException {
		int value;
		int fieldPos = 0;
		int fieldCount = 0;

		while ((value = in.read()) >= 0) {
			switch (value) {
			default:
				if (fieldPos < fieldBuffer.length)
					fieldBuffer[fieldPos++] = (char) value;
				break;
			case ':':
				fields[fieldCount++] = new String(fieldBuffer, 0, fieldPos);
				fieldPos = 0;
				break;
			case '\n':
				// ignore empty lines
				if (fieldPos == 0 && fieldCount == 0)
					break;
				fields[fieldCount++] = new String(fieldBuffer, 0, fieldPos);

				char first = fields[0].charAt(0);
				if ((first >= 'a' && first <= 'z')
						|| (first >= 'A' && first <= 'Z')
						|| first == '*') {
					numFields = fieldCount;
					argsIndex = 0;
					return;
				}

				Log.v("ServalDMonitor", "Ignoring invalid command \""
						+ fields[0] + "\"");
				fieldPos = 0;
				fieldCount = 0;
			case '\r':
				// ignore
			}
		}
		throw new EOFException();
	}

	static final Pattern delim = Pattern.compile(":");
	private void processInput() throws IOException {
		String cmd;

		// (we don't need to worry about NPE from this.is changing in another
		// thread if we keep a local reference)
		InputStream in = is;
		if (in == null)
			return;

		synchronized (iArgs) {
			readCommand(in);

			cmd = iArgs.next();

			if (cmd.charAt(0) == '*') {

				// Message with data
				String len = cmd.substring(1);
				dataBytes = parseInt(len);

				if (dataBytes < 0)
					throw new IOException(
							"Message has data block with negative length: "
									+ len);

				// Okay, we know about the data, get the real command
				cmd = iArgs.next();
			} else
				dataBytes = 0;

			int read = 0;

			try {
				Message handler = handlers.get(cmd);
				if (handler != null) {
					read = handler.message(cmd, iArgs, in, dataBytes);
				} else if (cmd.equals("ERROR")) {
					while (iArgs.hasNext())
						Log.e("ServalDMonitor", iArgs.next());
				} else if (this.messages != null)
					read = messages.message(cmd, iArgs, in, dataBytes);

			} finally {
				// always read up to the end of the data block, even if the
				// Messages instance did not.
				while (read < dataBytes) {
					if (logMessages)
						Log.v("ServalDMonitor", "Skipping "
								+ (dataBytes - read) + " unread data bytes");
					read += in.skip(dataBytes - read);
				}
			}

			if (read > dataBytes)
				throw new IOException("Read too many bytes");
		}
	}

	private void write(OutputStream out, String str) throws IOException {
		if (str == null)
			return;

		byte buff[] = new byte[str.length()];
		for (int i = 0; i < str.length(); i++) {
			char chr = str.charAt(i);
			if (chr > 0xFF)
				throw new IOException("Unexpected character " + chr);
			buff[i] = (byte) chr;
		}
		out.write(buff);
	}

	private void write(OutputStream out, String... x) throws IOException {
		for (int i = 0; i < x.length; i++)
			write(out, x[i]);
	}

	// this interface is specified as varargs so we can write characters
	// directly into the output stream without building a string buffer first
	public void sendMessage(String... string) throws IOException {
		try {
			if (socket == null)
				createSocket();
			OutputStream out = os;
			if (out == null)
				throw new IOException();

			if (logMessages)
				Log.v("ServalDMonitor",
						"Sending " + Arrays.deepToString(string));
			synchronized (out) {
				socket.setSoTimeout(500);
				write(out, string);
				write(out, "\n");
				out.flush();
				socket.setSoTimeout(60000);
			}
		} catch (IOException e) {
			cleanupSocket();
			throw e;
		}
	}

	public void sendMessageAndLog(String... string) {
		try {
			this.sendMessage(string);
		} catch (IOException e) {
			Log.e("ServalDMonitor", e.getMessage(), e);
		}
	}

	public boolean ready() {
		return socket != null;
	}

	public void stop() {
		stopMe = true;
		if (currentThread != null)
			currentThread.interrupt();
		cleanupSocket();
	}

	public void sendMessageAndData(byte[] block, int len, String... string)
			throws IOException {
		try {
			if (socket == null)
				createSocket();
			OutputStream out = os;
			if (out == null)
				throw new IOException();

			if (logMessages)
				Log.v("ServalDMonitor",
						"Sending " + Arrays.deepToString(string));

			synchronized (out) {
				socket.setSoTimeout(500);
				write(out, "*", Integer.toString(len), ":");
				write(out, string);
				write(out, "\n");
				out.write(block, 0, len);
				out.flush();
				socket.setSoTimeout(60000);
			}
		} catch (IOException e) {
			cleanupSocket();
			throw e;
		}
	}
}
