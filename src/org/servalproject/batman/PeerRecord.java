/*
 * This file is part of the Serval Batman Inspector Library.
 *
 *  Serval Batman Inspector Library is free software: you can redistribute it 
 *  and/or modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation, either version 3 of 
 *  the License, or (at your option) any later version.
 *
 *  Serval Batman Inspector Library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Serval Batman Inspector Library.  
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package org.servalproject.batman;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A class the defines a parcelable object that represents a peer record
 * 
 * @author corey.wallis@servalproject.org
 *
 */
public class PeerRecord implements Parcelable {
	
	// declare private level variables
	private int mAddressType;
	private String mAddress = null;
	private int mLinkScore;
	
	/**
	 * constructor for this class takes the three parameters and constructs a new object
	 * 
	 * @param addressType the type of address
	 * @param address     the ip address of the peer
	 * @param linkScore   the link score for this peer 
	 * 
	 * @throws IllegalArgumentException if any of the parameters do not pass validation
	 */
	public PeerRecord(int addressType, String address, int linkScore) throws IllegalArgumentException {
		
		/*
		 *  check on the parameters
		 */
		// addressType
		boolean mValid = false;
		for(int i = 0; i < ServiceStatus.VALID_ADDRESS_TYPES.length; i++) {
			if(addressType == ServiceStatus.VALID_ADDRESS_TYPES[i]) {
				mValid = true;
				i = ServiceStatus.VALID_ADDRESS_TYPES.length + 1;
			}
		}
		
		if(mValid != true) {
			throw new IllegalArgumentException("supplied address type '" + addressType + "' is not valid");
		}
		
		// address
		if(address.trim().equals("") == true) {
			throw new IllegalArgumentException("address must be a non null string");
		}
		
		//TODO is further validation necessary?
		
		// link score
		if(linkScore < ServiceStatus.MIN_LINK_SCORE || linkScore > ServiceStatus.MAX_LINK_SCORE) {
			throw new IllegalArgumentException("link score must be in the range " + ServiceStatus.MIN_LINK_SCORE + " - " + ServiceStatus.MAX_LINK_SCORE);
		}
		
		// store these values for later
		mAddressType = addressType;
		mAddress = address;
		mLinkScore = linkScore;
	}
	
	/**
	 * Construct a PeerParcel object from a parcel object
	 * 
	 * @param source the parcel to use as the source of data
	 */
	public PeerRecord(Parcel source) {
		
		// read in the same order that data was written
		mAddressType = source.readInt();
		mAddress = source.readString();
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
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
	 */
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// output the contents of this parcel
		dest.writeInt(mAddressType);
		dest.writeString(mAddress);
		dest.writeInt(mLinkScore);
	}

	/**
	 * @return the AddressType
	 */
	public int getAddressType() {
		return mAddressType;
	}

	/**
	 * @param addressType the AddressType to set
	 */
	public void setAddressType(int addressType) {
		boolean mValid = false;
		for(int i = 0; i < ServiceStatus.VALID_ADDRESS_TYPES.length; i++) {
			if(addressType == ServiceStatus.VALID_ADDRESS_TYPES[i]) {
				mValid = true;
				i = ServiceStatus.VALID_ADDRESS_TYPES.length + 1;
			}
		}
		
		if(mValid != true) {
			throw new IllegalArgumentException("supplied address type '" + addressType + "' is not valid");
		} else {
			mAddressType = addressType;
		}
	}

	/**
	 * @return the Address
	 */
	public String getAddress() {
		return mAddress;
	}

	/**
	 * @param address the Address to set
	 */
	public void setAddress(String address) {
		if(address.trim().equals("") == true) {
			throw new IllegalArgumentException("address must be a non null string");
		} else {
			mAddress = address;
		}
	}

	/**
	 * @return the LinkScore
	 */
	public int getLinkScore() {
		return mLinkScore;
	}

	/**
	 * @param linkScore the linkScore to set
	 */
	public void setLinkScore(int linkScore) {
		if(linkScore < ServiceStatus.MIN_LINK_SCORE || linkScore > ServiceStatus.MAX_LINK_SCORE) {
			throw new IllegalArgumentException("link score must be in the range " + ServiceStatus.MIN_LINK_SCORE + " - " + ServiceStatus.MAX_LINK_SCORE);
		} else {
			mLinkScore = linkScore;
		}
	}
}
