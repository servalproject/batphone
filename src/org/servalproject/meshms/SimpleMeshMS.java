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

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * A class used to represent a simple MeshMS message
 */
public class SimpleMeshMS implements Parcelable {

	//private class level variables
	private String senderSid;
	private String recipientSid;
	private String recipientDid;
	private String content;
	private long timestamp;

	/**
	 * the maximum allowed length of the content field in characters
	 */
	public final static int MAX_CONTENT_LENGTH = 1000;

	/**
	 *
	 * @param senderSid
	 *            the senders SID
	 * @param recipientSid
	 *            the recipients SID
	 * @param recipientDid
	 *            the recipients DID
	 * @param content
	 *            the content of the message
	 * @param humanReadable
	 *            indicates if the message is intended to be read by a human /
	 *            user
	 *
	 * @throws IllegalArgumentException
	 *             if the sender Sid field is empty or null
	 * @throws IllegalArgumentException
	 *             if the recipient Sid field is empty or null
	 * @throws IllegalArgumentException
	 *             if the content field is empty or null
	 * @throws IllegalArgumentException
	 *             if the content length > MAX_CONTENT_LENGTH
	 */
	public SimpleMeshMS(String senderSid, String recipientSid,
			String recipientDid, String content) {

		if (TextUtils.isEmpty(senderSid) == true) {
			throw new IllegalArgumentException(
					"the sender SID field is required");
		}

		if (TextUtils.isEmpty(recipientSid) == true) {
			throw new IllegalArgumentException(
					"the recipient SID field is required");
		}

		if(TextUtils.isEmpty(content) == true) {
			throw new IllegalArgumentException("the content field is required");
		}

		if(content.length() > MAX_CONTENT_LENGTH) {
			throw new IllegalArgumentException("the length of the content must be < " + MAX_CONTENT_LENGTH);
		}

		this.senderSid = senderSid;
		this.recipientSid = recipientSid;
		this.recipientDid = recipientDid;
		this.content = content;
		this.timestamp = System.currentTimeMillis();
	}

	/**
	 * get the senders sid
	 *
	 * @return the senders sid
	 */
	public String getSenderSid() {
		return senderSid;
	}

	/**
	 * set the senders phone number
	 *
	 * @param sender the senders phone number, if null / empty the currently configured phone number will be used
	 */
	public void setSenderSid(String senderSid) {
		if (TextUtils.isEmpty(senderSid) == true) {
			this.senderSid = null;
		} else {
			this.senderSid = senderSid;
		}
	}

	/**
	 * get the recipients SID
	 *
	 * @return the recipients SID
	 */
	public String getRecipientSid() {
		return recipientSid;
	}

	/**
	 * set the recipients SID
	 *
	 * @param recipient
	 *            the recipients SID
	 * @throws IllegalArgumentException
	 *             if the recipient SID field is empty or null
	 */
	public void setRecipientSid(String recipientSid) {
		if (TextUtils.isEmpty(recipientSid)) {
			throw new IllegalArgumentException(
					"the recipient SID field is required");
		}
	}

	/**
	 * get the recipients DID
	 *
	 * @return the recipients DID
	 */
	public String getRecipientDid() {
		return recipientDid;
	}

	/**
	 * set the recipients DID
	 *
	 * @param recipient
	 *            the recipients DID
	 * @throws IllegalArgumentException
	 *             if the recipient SID field is empty or null
	 */
	public void setRecipientDid(String recipientDid) {
		if (TextUtils.isEmpty(recipientDid)) {
			throw new IllegalArgumentException(
					"the recipient DID field is required");
		}
	}

	/**
	 * get the content of the message
	 *
	 * @return the content of the message
	 */
	public String getContent() {
		return content;
	}

	/**
	 * set the content of the message
	 *
	 * @param content the content of the message
	 * @throws IllegalArgumentException if the content field is empty or null
	 * @throws IllegalArgumentException if the content length > MAX_CONTENT_LENGTH
	 */
	public void setContent(String content) {
		if(TextUtils.isEmpty(content) == true) {
			throw new IllegalArgumentException("the content field is required");
		}

		if(content.length() > MAX_CONTENT_LENGTH) {
			throw new IllegalArgumentException("the length of the content must be < " + MAX_CONTENT_LENGTH);
		}

		this.content = content;
	}

	/**
	 * get the timestamp associated with this message
	 * @return
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * set the timestamp field of this object to the current system time with millisecond precision
	 */
	public void setTimeStamp() {
		timestamp = System.currentTimeMillis();
	}

	/*
	 * parcelable specific methods
	 */

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

		dest.writeString(senderSid);
		dest.writeString(recipientSid);
		dest.writeString(recipientDid);
		dest.writeLong(timestamp);
		dest.writeString(content);
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
		senderSid = in.readString();
		recipientSid = in.readString();
		recipientDid = in.readString();
    	timestamp = in.readLong();
    	content = in.readString();
    }

}
