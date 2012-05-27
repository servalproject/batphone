/*
 * Copyright (c) 2012, The Serval Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the The Serval Project nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE SERVAL PROJECT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.servalproject.meshms;

import org.servalproject.servald.SubscriberId;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * A class used to represent a simple MeshMS message
 */
public class SimpleMeshMS implements Parcelable
{
	public final SubscriberId sender;
	public final SubscriberId recipient;
	public final String senderDid;
	public final String recipientDid;
	public final long timestamp;
	public final String content;

	/** Maximum allowed length of the content field in characters
	 */
	public final static int MAX_CONTENT_LENGTH = 1000;

	/** Constructor.
	 *
	 * @param sender the senders SID
	 * @param recipient the recipients SID
	 * @param senderDid the recipients DID
	 * @param recipientDid the recipients DID
	 * @param millis the message date as milliseconds since 1970 epoch
	 * @param content the content of the message
	 *
	 * @throws IllegalArgumentException if the content field is empty or null
	 * @throws IllegalArgumentException if the content length exceeds MAX_CONTENT_LENGTH
	 */
	public SimpleMeshMS(SubscriberId sender, SubscriberId recipient, String senderDid, String recipientDid, long millis, String content) {
		if (TextUtils.isEmpty(content)) {
			throw new IllegalArgumentException("missing content field");
		}
		if (content.length() > MAX_CONTENT_LENGTH) {
			throw new IllegalArgumentException("content too long, " + content.length() + " bytes exceeds " + MAX_CONTENT_LENGTH);
		}
		this.sender = sender;
		this.recipient = recipient;
		this.senderDid = senderDid;
		this.recipientDid = recipientDid;
		this.timestamp = millis;
		this.content = content;
	}

	/*
	 * (non-Javadoc)
	 * @see android.os.Parcelable#describeContents()
	 */
	@Override
	public int describeContents() {
		return 0;
	}

	/*
	 * write the contents of the object into a parcel
	 * (non-Javadoc)
	 * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
	 */
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(this.sender.toHex().toUpperCase());
		dest.writeString(this.recipient.toHex().toUpperCase());
		dest.writeString(this.senderDid);
		dest.writeString(this.recipientDid);
		dest.writeLong(this.timestamp);
		dest.writeString(this.content);
	}

	/*
	 * This defines how to regenerate the object
	 */
	public static final Parcelable.Creator<SimpleMeshMS> CREATOR = new Parcelable.Creator<SimpleMeshMS>() {
		@Override
		public SimpleMeshMS createFromParcel(Parcel in) {
			return new SimpleMeshMS(in);
        }
		@Override
		public SimpleMeshMS[] newArray(int size) {
            return new SimpleMeshMS[size];
        }
    };

    /*
     * undertake the process of regenerating the object
     */
    private SimpleMeshMS(Parcel in) {
		try {
			this.sender = new SubscriberId(in.readString());
			this.recipient = new SubscriberId(in.readString());
			this.senderDid = in.readString();
			this.recipientDid = in.readString();
			this.timestamp = in.readLong();
			this.content = in.readString();
		}
		catch (SubscriberId.InvalidHexException e) {
			throw new IllegalStateException("Malformed parcel, invalid SID, " + e);
		}
    }

}
