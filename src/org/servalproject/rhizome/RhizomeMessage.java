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

import java.io.DataOutput;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.servalproject.meshms.SimpleMeshMS;
import org.servalproject.servald.SubscriberId;

public class RhizomeMessage implements RhizomeMessageLogEntry.Filling {

	public static final byte SWITCH_BYTE = 0x02;

	public final String senderDID;
	public final String recipientDID;
	public final long millis;
	public final String message;

	/** Create a rhizome message from all of its properties.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public RhizomeMessage(String senderDID, String recipientDID, long millis, String message) {
		this.senderDID = senderDID;
		this.recipientDID = recipientDID;
		this.millis = millis;
		this.message = message;
	}

	public RhizomeMessage(RandomAccessFile ra, int length) throws IOException {
		this.millis = ra.readLong();
		this.senderDID = ra.readUTF();
		this.recipientDID = ra.readUTF();
		this.message = ra.readUTF();
	}

	public SimpleMeshMS toMeshMs(SubscriberId sender, SubscriberId recipient) {
		return new SimpleMeshMS(sender, recipient, senderDID, recipientDID, millis, message);
	}

	@Override
	public byte getSwitchByte() {
		return SWITCH_BYTE;
	}

	@Override
	public void writeTo(DataOutput dout) throws IOException {
		dout.writeLong(this.millis);
		dout.writeUTF(this.senderDID == null ? "" : this.senderDID);
		dout.writeUTF(this.recipientDID == null ? "" : this.recipientDID);
		dout.writeUTF(this.message == null ? "" : this.message);
	}

	@Override
	public String toString() {
		return this.getClass().getName()
			+ "(senderDID=" + this.senderDID
			+ ", recipientDID=" + this.recipientDID
			+ ", millis=" + this.millis
			+ ", message='" + this.message + "'"
			+ ")";
	}

}
