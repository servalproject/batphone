/**
 * Copyright (C) 2012 The Serval Project
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

package org.servalproject.rhizome;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;

import android.util.Log;

import org.servalproject.servald.SubscriberId;

public class RhizomeMessage {

	public static class TooLongException extends Exception {
		final long length;
		public TooLongException(long length) {
			super("Too long (" + length + " bytes)");
			this.length = length;
		}
	}

	public static class FormatException extends Exception {
		public FormatException(Throwable e) {
			super(e);
		}
		public FormatException(String message) {
			super(message);
		}
		public FormatException(String message, Throwable e) {
			super(message, e);
		}
	}

	public final SubscriberId sender;
	public final SubscriberId recipient;
	public final String senderDID;
	public final String recipientDID;
	public final long millis;
	public final String message;

	/** Create a rhizome message from all of its properties.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public RhizomeMessage(SubscriberId sender, SubscriberId recipient, String senderDID, String recipientDID, long millis, String message) {
		this.sender = sender;
		this.recipient = recipient;
		this.senderDID = senderDID;
		this.recipientDID = recipientDID;
		this.millis = millis;
		this.message = message;
	}

	/** Read a rhizome message from the current position in a file.  Leaves the file positioned at
	 * the first byte immediately following the message.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public RhizomeMessage(SubscriberId sender, SubscriberId recipient, RandomAccessFile ra) throws IOException, FormatException {
		this.sender = sender;
		this.recipient = recipient;
		long offset = ra.getFilePointer();
		short length = ra.readShort();
		ra.skipBytes(length);
		if (ra.readShort() != length)
			throw new FormatException("malformed envelope");
		try {
			ra.seek(offset + 2);
			this.millis = ra.readLong();
			this.senderDID = ra.readUTF();
			this.recipientDID = ra.readUTF();
			this.message = ra.readUTF();
			if (ra.getFilePointer() != offset + 2 + length)
				throw new FormatException("malformed body");
			ra.skipBytes(2);
		}
		catch (EOFException e) {
			throw new FormatException("too short", e);
		}
		catch (UTFDataFormatException e) {
			throw new FormatException("bad UTF string", e);
		}
		catch (IOException e) {
			throw new FormatException(e);
		}
	}

	/** Convert the message into an array of bytes that can be appended to a message log.  The array
	 * will be decoded by the RandomAccessFile constructor.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public byte[] toBytes() throws TooLongException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(body);
		ByteArrayOutputStream envelope;
		try {
			dos.writeLong(this.millis);
			dos.writeUTF(this.senderDID == null ? "" : this.senderDID);
			dos.writeUTF(this.recipientDID == null ? "" : this.recipientDID);
			dos.writeUTF(this.message == null ? "" : this.message);
			dos.close();
			envelope = new ByteArrayOutputStream(body.size() + 4);
			dos = new DataOutputStream(envelope);
			short length = (short) body.size();
			if (length != body.size())
				throw new TooLongException(body.size());
			dos.writeShort(length);
			body.writeTo(dos);
			dos.writeShort(length);
			dos.close();
		}
		catch (IOException e) {
			// should not occur for ByteArrayOutputStream
			Log.e(Rhizome.TAG, "unexpected exception", e);
			throw new AssertionError();
		}
		if (envelope.size() != body.size() + 4)
			throw new AssertionError();
		return envelope.toByteArray();
	}

	public String toString() {
		return this.getClass().getName() + "("
			+ "sender=" + this.sender
			+ ", senderDID=" + this.senderDID
			+ ", recipient=" + this.recipient
			+ ", recipientDID=" + this.recipientDID
			+ ", millis=" + this.millis
			+ ", message='" + this.message + "'"
			+ ")";
	}

	public boolean send() {
		return Rhizome.sendMessage(this);
	}

}
