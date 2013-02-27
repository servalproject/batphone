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
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UTFDataFormatException;

import android.util.Log;

public class RhizomeMessageLogEntry {

	public static interface Filling {
		byte getSwitchByte();
		void writeTo(DataOutput dout) throws IOException;
	}

	public static class TooLongException extends Exception {
		private static final long serialVersionUID = 1L;
		final long length;
		public TooLongException(long length) {
			super("Message is too long (" + length + " bytes)");
			this.length = length;
		}
	}

	public static class FormatException extends Exception {
		private static final long serialVersionUID = 1L;
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

	/** Read a rhizome message from a random-access file.  If 'reverse' is false, then reads the
	 * message that starts at the current file position and leaves the file positioned at the first
	 * byte immediately following the message.  If 'reverse' is true, then reads the message which
	 * ends on the byte immediately preceding the current file position, and leaves the file
	 * positioned at the first byte of the read message.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public RhizomeMessageLogEntry(RandomAccessFile ra, boolean reverse) throws IOException, FormatException {
		long origin = ra.getFilePointer();
		try {
			long start;
			long end;
			int length1;
			int length2;

			if (reverse) {
				end = origin;
				ra.seek(end - 2);
				length2 = ra.readShort();
				start = end - 5 - length2;
				if (start < 0)
					throw new FormatException("malformed envelope, start=" + start);
				ra.seek(start);
				length1 = ra.readShort();
				if (length1 != length2)
					throw new FormatException("malformed envelope, length1=" + length1 + ", length2=" + length2);
			} else {
				start = origin;
				length1 = ra.readShort();
				ra.seek(start + 3 + length1);
				length2 = ra.readShort();
				if (length1 != length2)
					throw new FormatException("malformed envelope, length1=" + length1 + ", length2=" + length2);
				end = ra.getFilePointer();
				ra.seek(start + 2);
			}

			try {
				byte switchByte = ra.readByte();
				switch (switchByte) {
				case RhizomeAck.SWITCH_BYTE:
					this.filling = new RhizomeAck(ra, length1);
					break;
				case RhizomeMessage.SWITCH_BYTE:
					this.filling = new RhizomeMessage(ra, length1);
					break;
				default:
					this.filling = null;
					Log.w(Rhizome.TAG, "unsupported rhizome log entry, switchByte=" + switchByte);
					break;
				}

				long end_filling = ra.getFilePointer();
				if (this.filling != null && end_filling > end - 2)
					throw new FormatException("malformed entry, end_filling=" + end_filling + ", end=" + end);

				// allow for future message formats to get longer with
				// additional optional fields
				if (this.filling != null && end_filling < end - 2)
					Log.w("MessageLog",
							"Entry may contain unexpected fields, end_filling="
									+ end_filling + ", end=" + end);

				if (reverse)
					ra.seek(start);
				else
					ra.seek(end);
			}
			catch (EOFException e) {
				throw new FormatException("too short", e);
			}
			catch (UTFDataFormatException e) {
				throw new FormatException("bad UTF string", e);
			}
		}
		catch (FormatException e) {
			ra.seek(origin); // IOException here has priority over FormatException
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
			dos.writeByte(this.filling.getSwitchByte());
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
