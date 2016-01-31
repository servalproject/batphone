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

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;
import android.util.Log;

import org.servalproject.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

public class ServalDMonitor implements Runnable {
	private final ServalD server;
	private LocalSocket socket = null;
	private final LocalSocketAddress serverSocketAddress;

	// Use a filesystem binding point from inside our app dir at our end,
	// so that no one other than the server can send us messages.
	private final LocalSocketAddress clientSocketAddress;

	private OutputStream os = null;
	private InputStream is = null;
	private boolean stopMe = false;

	int dataBytes = 0;

	public static final int MONITOR_VOMP = (1 << 0);
	public static final int MONITOR_RHIZOME = (1 << 1);
	public static final int MONITOR_PEERS = (1 << 2);
	public static final int MONITOR_DNAHELPER = (1 << 3);

	private static final String TAG = "ServalDMonitor";

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

	public ServalDMonitor(ServalD server) {
		this.server = server;
		String instancePath = server.getInstancePath();

		this.clientSocketAddress = new LocalSocketAddress(
				instancePath.substring(1)+"/servald-java-client.socket",
				LocalSocketAddress.Namespace.ABSTRACT);
		this.serverSocketAddress = new LocalSocketAddress(
				instancePath.substring(1)+"/monitor.socket",
				LocalSocketAddress.Namespace.ABSTRACT);
	}

	private Map<String, Messages> handlers = new HashMap<String, Messages>();
	private Set<Messages> uniqueHandlers = new HashSet<Messages>();

	public void addHandler(String cmd, Messages handler){
		handlers.put(cmd.toUpperCase(), handler);
		if (!uniqueHandlers.contains(handler)){
			uniqueHandlers.add(handler);
			if (socket!=null)
				handler.onConnect(this);
		}
	}

	public interface Messages {
		public void onConnect(ServalDMonitor monitor);
		public void onDisconnect(ServalDMonitor monitor);

		public int message(String cmd, Iterator<String> iArgs,
				InputStream in, int dataLength) throws IOException;
	}

	// Attempt to connect to the servald monitor interface
	private void createSocket() throws IOException {
		synchronized(this){
			if (socket != null)
				return;

			if (stopMe)
				throw new IOException("Stopping");

			Log.v(TAG, "Creating socket");
			LocalSocket socket = new LocalSocket();
			try {
				Log.v(TAG, "Binding socket " + clientSocketAddress.getName());
				socket.bind(clientSocketAddress);
				socket.setSoTimeout(1000);
				Log.v(TAG, "Connecting socket " + serverSocketAddress.getName());
				socket.connect(serverSocketAddress);
				socket.setSoTimeout(60000);
				is = new BufferedInputStream(
						socket.getInputStream(), 640);
				os = new BufferedOutputStream(socket.getOutputStream(), 640);
				this.socket = socket;

			} catch (IOException e) {
				try {
					if (socket != null)
						socket.close();
				} catch (IOException e1) {
					Log.e(TAG, e1.getMessage(), e1);
				}
				throw e;
			}
		}

		// tell servald to quit if this connection closes
		sendMessage("monitor quit");

		for (Messages m : uniqueHandlers){
			Log.v(TAG, "onConnect " + m.toString());
			m.onConnect(this);
		}
		Log.v(TAG, "Connected");
	}

	private void cleanupSocket() {
		close(is);
		is = null;
		close(os);
		os = null;
		try {
			if (socket != null){
				socket.close();
				for (Messages m : uniqueHandlers)
					m.onDisconnect(this);
				stopMe = true;
				server.updateStatus(R.string.server_off);
			}
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
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private Thread currentThread;

	@Override
	public void run() {
		Log.d(TAG, "Starting");
		currentThread = Thread.currentThread();
		// boost the priority so we can read incoming audio frames with low
		// latency
		Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
		while (!stopMe) {

			try {
				// Make sure we have the sockets we need
				if (socket == null)
					createSocket();

				// See if there is anything to read
				try{
					processInput();
				} catch (IOException e) {
					if (!"Try again".equals(e.getMessage()))
						throw e;
				}
			} catch (EOFException e) {
				cleanupSocket();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
				stopMe = true;
				cleanupSocket();
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
		currentThread = null;
		Log.d(TAG, "Stopped");
		cleanupSocket();
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

				Log.v(TAG, "Ignoring invalid command \""
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
				Messages handler = handlers.get(cmd.toUpperCase());
				if (handler == null)
					handler = handlers.get("");
				if (handler != null)
					read = handler.message(cmd, iArgs, in, dataBytes);
			} finally {
				// always read up to the end of the data block, even if the
				// Messages instance did not.
				while (read < dataBytes)
					read += in.skip(dataBytes - read);
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
			Log.e(TAG, e.getMessage(), e);
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

	public boolean hasStopped() {
		return stopMe;
	}

	public void sendMessageAndData(byte[] block, int len, String... string)
			throws IOException {
		try {
			if (socket == null)
				createSocket();
			OutputStream out = os;
			if (out == null)
				throw new IOException();

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
