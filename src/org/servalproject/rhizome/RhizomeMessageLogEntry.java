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
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;

import android.util.Log;

import org.servalproject.servald.SubscriberId;

public class RhizomeMessageLogEntry {

	public static interface Filling {
		byte getSwitchByte();
		void writeTo(DataOutput dout) throws IOException;
	}

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

	public final Filling filling;

	/** Construct a rhizome message from its filling.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public RhizomeMessageLogEntry(Filling filling) {
		this.filling = filling;
	}

	/** Read a rhizome message from the current position in a file.  Leaves the file positioned at
	 * the first byte immediately following the message.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public RhizomeMessageLogEntry(RandomAccessFile ra) throws IOException, FormatException {
		long offset = ra.getFilePointer();
		try {
			short length = ra.readShort();
			ra.skipBytes(length);
			if (ra.readShort() != length)
				throw new FormatException("malformed envelope");
			try {
				ra.seek(offset + 2);
				byte switchByte = ra.readByte();
				switch (switchByte) {
				case RhizomeAck.SWITCH_BYTE:		this.filling = new RhizomeAck(ra); break;
				case RhizomeMessage.SWITCH_BYTE:	this.filling = new RhizomeMessage(ra); break;
				default:
					this.filling = null;
					Log.w(Rhizome.TAG, "unsupported rhizome log entry, switchByte=" + switchByte);
					break;
				}
				if (this.filling != null && ra.getFilePointer() != offset + 2 + length)
					throw new FormatException("malformed entry");
				ra.seek(offset + 5 + length);
			}
			catch (EOFException e) {
				throw new FormatException("too short", e);
			}
			catch (UTFDataFormatException e) {
				throw new FormatException("bad UTF string", e);
			}
		}
		catch (FormatException e) {
			ra.seek(offset); // IOException has priority over FormatException
			throw e;
		}
	}

	/** Move a file position backward over an immediately preceding rhizome message.  Leaves the
	 * file positioned ready to read the message with the RandomAccessFile constructor.
	 * 
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void rewindOne(RandomAccessFile ra) throws IOException, FormatException {
		long offset = ra.getFilePointer();
		try {
			ra.seek(offset - 2);
			short length = ra.readShort();
			long start = offset - 5 - length;
			if (start < 0)
				throw new FormatException("malformed envelope");
			ra.seek(start);
			if (ra.readShort() != length)
				throw new FormatException("malformed envelope");
			ra.seek(start);
		}
		catch (FormatException e) {
			ra.seek(offset); // IOException has priority over FormatException
			throw e;
		}
	}

	/** Convert an entry into an array of bytes that can be appended to a message log.  The array
	 * will be decoded by the RandomAccessFile constructor.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public byte[] toBytes() throws TooLongException {
		if (this.filling == null) {
			Log.w(Rhizome.TAG, "empty RhizomeMessage produces zero-length packet");
			return new byte[0];
		}
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		ByteArrayOutputStream envelope;
		try {
			DataOutputStream dos = new DataOutputStream(body);
			this.filling.writeTo(dos);
			dos.close();
			envelope = new ByteArrayOutputStream(body.size() + 5);
			dos = new DataOutputStream(envelope);
			short length = (short) body.size();
			if (length != body.size())
				throw new TooLongException(body.size());
			dos.writeShort(length);
			dos.writeByte((int) this.filling.getSwitchByte());
			body.writeTo(dos);
			dos.writeShort(length);
			dos.close();
		}
		catch (IOException e) {
			// should not occur for ByteArrayOutputStream
			Log.e(Rhizome.TAG, "unexpected exception", e);
			throw new AssertionError();
		}
		if (envelope.size() != body.size() + 5) {
			Log.e(Rhizome.TAG, "envelope.size()=" + envelope.size() + ", body.size()=" + body.size());
			throw new AssertionError();
		}
		return envelope.toByteArray();
	}

	@Override
	public String toString() {
		return this.filling == null ? getClass().getName() + "(null)" : this.filling.toString();
	}

}
