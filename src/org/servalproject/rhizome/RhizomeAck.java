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

import java.util.Arrays;
import java.io.DataOutput;
import java.io.RandomAccessFile;
import java.io.IOException;

import android.util.Log;

import org.servalproject.servald.Packet;
import org.servalproject.servald.BundleId;

public class RhizomeAck implements RhizomeMessageLogEntry.Filling {

	public static final byte SWITCH_BYTE = 0x01;
	public static final int BUNDLE_ID_PREFIX_BYTES = 4;

	public final byte[] bundleIdPrefix;
	public final long offset;

	/** Create a rhizome message from all of its properties.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public RhizomeAck(BundleId bundleId, long offset) throws Packet.HexDecodeException {
		this.bundleIdPrefix = new byte[BUNDLE_ID_PREFIX_BYTES];
		for (int i = 0; i != this.bundleIdPrefix.length; ++i)
			this.bundleIdPrefix[i] = bundleId.binary[i];
		this.offset = offset;
	}

	public RhizomeAck(RandomAccessFile ra) throws IOException {
		this.bundleIdPrefix = new byte[BUNDLE_ID_PREFIX_BYTES];
		ra.readFully(this.bundleIdPrefix);
		this.offset = ra.readLong();
	}

	@Override
	public byte getSwitchByte() {
		return SWITCH_BYTE;
	}

	@Override
	public void writeTo(DataOutput dout) throws IOException {
		dout.write(this.bundleIdPrefix);
		dout.writeLong(this.offset);
	}

	@Override
	public String toString() {
		return this.getClass().getName()
			+ "(bundleIdPrefix=" + Packet.binToHex(this.bundleIdPrefix)
			+ ", offset=" + this.offset
			+ ")";
	}

	public String bundleIdPrefixHex() {
		return Packet.binToHex(this.bundleIdPrefix);
	}

	/** Convenience method to check if a given bundle (manifest) ID matches this ACK.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public boolean matches(String bundleIdHex) {
		byte[] bundleIdPrefix = new byte[BUNDLE_ID_PREFIX_BYTES];
		try {
			Packet.hexToBin(bundleIdHex.substring(0, bundleIdPrefix.length * 2), bundleIdPrefix);
			return Arrays.equals(bundleIdPrefix, this.bundleIdPrefix);
		}
		catch (Packet.HexDecodeException e) {
			return false;
		}
	}

	/** Convenience method to check if a given bundle ID matches this ACK.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public boolean matches(BundleId bundleId) {
		for (int i = 0; i != this.bundleIdPrefix.length; ++i)
			if (bundleId.binary[i] != this.bundleIdPrefix[i])
				return false;
		return true;
	}

}

