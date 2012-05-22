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
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.servalproject.ServalBatPhoneApplication;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.SystemClock;
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
	private DataInputStream is = null;
	private boolean stopMe = false;
	private long socketConnectTime;
	private boolean logMessages = false;

	int dataBytes = 0;
	private Messages messages;

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
		int ret = 0;
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

	public interface Messages {
		public void connected();

		public int message(String cmd, Iterator<String> iArgs,
				DataInputStream in,
				int dataLength) throws IOException;
	}

	// Attempt to connect to the servald monitor interface, try to restart
	// servald on the first failure and try up to 10 times
	private synchronized void createSocket() throws IOException {
		if (socket != null)
			return;

		boolean restarted = false;

		for (int i = 0; i < 10; i++) {

			if (i > 0) {
				// 100 to 10000ms. 10s should be plenty of time for servald to
				// start
				int wait = 100 * i * i;
				Log.v("ServalDMonitor", "Waiting for " + wait + "ms");
				try {
					Thread.sleep(wait);
				} catch (InterruptedException e) {
					Log.e("ServalDMonitor", e.getMessage(), e);
				}
			}

			Log.v("ServalDMonitor", "Creating socket");
			LocalSocket socket = new LocalSocket();
			try {
				socket.bind(clientSocketAddress);
				socket.connect(serverSocketAddress);

				int tries = 15;
				while (!socket.isConnected() && --tries > 0)
					try {
						Thread.sleep(250);
					} catch (InterruptedException e) {
						throw new InterruptedIOException();
					}

				if (!socket.isConnected())
					throw new IOException("Connection timed out");

				socket.setSoTimeout(60000);
				is = new DataInputStream(new BufferedInputStream(
						socket.getInputStream(), 640));
				os = socket.getOutputStream();
				socketConnectTime = SystemClock.elapsedRealtime();
				this.socket = socket;

				if (this.messages != null)
					messages.connected();

				return;
			} catch (IOException e) {
				Log.e("ServalDMonitor", e.getMessage(), e);

				try {
					if (socket != null)
						socket.close();
				} catch (IOException e1) {
					Log.e("ServalDMonitor", e1.getMessage(), e1);
				}
			}

			if (!restarted) {
				// try to stop and start servald after the first failure
				try {
					ServalD.serverStop();
					ServalD.serverStart(ServalBatPhoneApplication.context.coretask.DATA_FILE_PATH
							+ "/bin/servald");
				} catch (Exception e) {
					Log.e("ServalDMonitor", e.getMessage(), e);
				}
				restarted = true;
			}
		}
		throw new IOException("Giving up trying to connect to servald");
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

	@Override
	public void run() {
		Log.d("ServalDMonitor", "Starting");
		currentThread = Thread.currentThread();

		while (!stopMe) {

			try {
				// Make sure we have the sockets we need
				if (socket == null)
					createSocket();

				// See if there is anything to read
				processInput();
			} catch (Exception e) {
				Log.e("ServalDMonitor", e.getMessage(), e);
			}
		}
		currentThread = null;
	}

	private void processInput() throws IOException {
		String cmd;

		DataInputStream in = is;
		if (in == null)
			return;

		synchronized (in) {
			try {
				String line = in.readLine();
				if (line == null)
					throw new EOFException();
				if (line.equals(""))
					return;

				if (logMessages)
					Log.v("ServalDMonitor", "Read monitor message: " + line);

				final String args[] = line.split(":");

				Iterator<String> iArgs = new Iterator<String>() {
					int index = 0;

					@Override
					public boolean hasNext() {
						return index < args.length;
					}

					@Override
					public String next() {
						if (!hasNext())
							throw new NoSuchElementException();
						return args[index++];
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
				cmd = iArgs.next();

				if (cmd.charAt(0) == '*') {

					// Message with data
					dataBytes = parseInt(cmd.substring(1));

					if (dataBytes < 0)
						throw new IOException(
								"Message has data block with negative length: "
										+ line);

					// Okay, we know about the data, get the real command
					cmd = iArgs.next();
				} else
					dataBytes = 0;

				int read = 0;

				if (cmd.equals("CLOSE")) {
					// servald doesn't want to talk to us
					// don't retry for a second
					cleanupSocket();
				} else if (this.messages != null)
					read = messages.message(cmd, iArgs, in, dataBytes);

				while (read < dataBytes) {
					if (logMessages)
						Log.v("ServalDMonitor", "Skipping "
								+ (dataBytes - read) + " unread data bytes");
					read += in.skip(dataBytes - read);
				}

				if (read > dataBytes)
					throw new IOException("Read too many bytes");

			} catch (IOException e) {
				if ("Try again".equals(e.getMessage()))
					return;

				cleanupSocket();
				throw e;
			}
		}
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
				int v = 0xFF & data[offset + i + j];
				sb.append(" ");
				if (i + j < length) {
					sb.append(Integer.toHexString(v));
				} else
					sb.append("   ");
			}
			sb.append(" ");
			for (j = 0; j < 16; j++) {
				int v = 0xFF & data[offset + i + j];
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

	private void write(String str) throws IOException {
		byte buff[] = new byte[str.length()];
		for (int i = 0; i < str.length(); i++) {
			char chr = str.charAt(i);
			if (chr > 0xFF)
				throw new IOException("Unexpected character " + chr);
			buff[i] = (byte) chr;
		}
		os.write(buff);
	}

	public void sendMessage(String string) throws IOException {
		try {
			if (socket == null)
				createSocket();
			if (logMessages)
				Log.v("ServalDMonitor", "Sending " + string);
			synchronized (os) {
				socket.setSoTimeout(500);
				write(string + "\n");
				socket.setSoTimeout(60000);
			}
			os.flush();
		} catch (IOException e) {
			cleanupSocket();
			throw e;
		}
	}

	public void sendMessageAndLog(String string) {
		try {
			this.sendMessage(string);
		} catch (IOException e) {
			Log.e("ServalDMonitor", e.getMessage(), e);
		}
	}
	public boolean ready() {
		if (socket != null)
			return true;
		else
			return false;
	}

	public void stop() {
		stopMe = true;
		if (currentThread != null)
			currentThread.interrupt();
		cleanupSocket();
	}

	public void sendMessageAndData(String string, byte[] block, int len)
			throws IOException {
		try {
			if (socket == null)
				createSocket();
			StringBuilder sb = new StringBuilder();
			sb
					.append("*")
					.append(len)
					.append(":")
					.append(string)
					.append("\n");
			if (logMessages)
				Log.v("ServalDMonitor", "Sending " + string + " +"
						+ len + " data");
			synchronized (os) {
				socket.setSoTimeout(500);
				write(sb.toString());
				os.write(block, 0, len);
				socket.setSoTimeout(60000);
			}
			os.flush();
		} catch (IOException e) {
			cleanupSocket();
			throw e;
		}
	}
}
