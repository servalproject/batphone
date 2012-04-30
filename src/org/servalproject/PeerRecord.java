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

package org.servalproject;

import org.servalproject.batman.ServiceStatus;
import org.servalproject.servald.SubscriberId;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A class the defines a parcelable object that represents a peer record
 *
 */
public class PeerRecord implements Parcelable {

	// declare private level variables
	private SubscriberId sid;
	private int mLinkScore;
	long expiryTime;
	long lastHeard;
	String did;

	/**
	 * constructor for this class takes the three parameters and constructs a new object
	 *
	 * @param address     the address of the peer
	 * @param linkScore   the link score for this peer
	 *
	 * @throws IllegalArgumentException if any of the parameters do not pass validation
	 */
	public PeerRecord(SubscriberId sid, int linkScore)
			throws IllegalArgumentException {

		if (sid == null)
			throw new IllegalArgumentException("sid must be valid");

		if(linkScore < ServiceStatus.MIN_LINK_SCORE || linkScore > ServiceStatus.MAX_LINK_SCORE) {
			throw new IllegalArgumentException("link score must be in the range " + ServiceStatus.MIN_LINK_SCORE + " - " + ServiceStatus.MAX_LINK_SCORE);
		}

		// store these values for later
		this.sid = sid;
		mLinkScore = linkScore;
	}

	/**
	 * Construct a PeerParcel object from a parcel object
	 *
	 * @param source the parcel to use as the source of data
	 */
	public PeerRecord(Parcel source) {

		// read in the same order that data was written
		int addrType=source.readInt();

		byte []addrBytes;

		switch (addrType){
		case 32:
			addrBytes = new byte[32];
			break;
		default:
			throw new IllegalStateException("Unhandled address type");
		}
		source.readByteArray(addrBytes);
		sid = new SubscriberId(addrBytes);

		mLinkScore = source.readInt();
	}

	/**
	 * Field that specifies a creator that generates instances of your PeerParcel from a Parcel.
	 */
	public static final Parcelable.Creator<PeerRecord> CREATOR = new Parcelable.Creator<PeerRecord>() {

		@Override
		public PeerRecord createFromParcel(Parcel source) {
			return new PeerRecord(source);
		}

		@Override
		public PeerRecord[] newArray(int size) {
			return new PeerRecord[size];
		}
	};

	/* (non-Javadoc)
	 * @see android.os.Parcelable#describeContents()
	 */
	@Override
	public int describeContents() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
	 */
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// output the contents of this parcel
		byte[] addr = sid.getSid();
		switch(addr.length){
		case 32:
			dest.writeInt(32);
			break;
		}
		dest.writeByteArray(addr);
		dest.writeInt(mLinkScore);
	}

	/**
	 * @return the Address
	 */
	public SubscriberId getSid() {
		return sid;
	}

	/**
	 * @return the LinkScore
	 */
	public int getLinkScore() {
		return mLinkScore;
	}

	@Override
	public String toString() {
		String sidstr = sid.toString().substring(0, 9) + "*";
		return sidstr.substring(sidstr.indexOf('/') + 1)
				+ (mLinkScore == 0 ? "" : " (" + mLinkScore + ")");
	}
}