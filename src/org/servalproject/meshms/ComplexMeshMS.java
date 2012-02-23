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

import java.util.ArrayList;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * The main class for the sending and receiving complex MeshMS messages
 */
public class ComplexMeshMS implements Parcelable {
	
	// store the required fields
	private String sender;
	private String recipient;
	private String type;
	
	private long timestamp;
	
	private ArrayList<MeshMSElement> content;
	
	/**
	 * construct a new Complex MeshMS object. The currently configured phone number will be used in the sender field
	 * 
	 * @param recipient the recipients phone number
	 * @param type the MeshMS type identifier
	 * 
	 * @throws IllegalArgumentException if the recipient and type fields are null
	 */
	public ComplexMeshMS(String recipient, String type) {
		this(null, recipient, type);
		
	}
	
	/**
	 * construct a new Complex MeshMS object
	 * 
	 * @param sender the senders phone number, if null / empty the currently configured phone number will be used
	 * @param recipient the recipients phone number
	 * @param type the MeshMS type identifier
	 * 
	 * @throws IllegalArgumentException if the recipient and type fields are null
	 */
	public ComplexMeshMS(String sender, String recipient, String type) {
		
		// validate the parameters
		if(TextUtils.isEmpty(sender) == true) {
			this.sender = null;
		} else {
			this.sender = sender;
		}
		
		if(TextUtils.isEmpty(recipient) == true) {
			throw new IllegalArgumentException("recipient is required");
		}
		
		if(TextUtils.isEmpty(type) == true) {
			throw new IllegalArgumentException("type is required");
		}
		
		this.recipient = recipient;
		this.type = type;
		
		//set a default timestamp
		timestamp = System.currentTimeMillis();
		
		content = new ArrayList<MeshMSElement>();
	}
	
	/**
	 * get the senders phone number
	 * @return the senders phone number
	 */
	public String getSender() {
		return sender;
	}

	/**
	 * set the senders phone number
	 * @param sender the senders phone number, if null / empty the currently configured phone number will be used
	 */
	public void setSender(String sender) {
		if(TextUtils.isEmpty(sender) == true) {
			this.sender = null;
		} else {
			this.sender = sender;
		}
		
		this.sender = sender;
	}

	/**
	 * return the recipients phone number
	 * @return the recipients phone number
	 */
	public String getRecipient() {
		return recipient;
	}

	/**
	 * set the recipients phone number
	 * @param recipient the new recipients phone number
	 * @throws IllegalArgumentException if the recipient field is empty or null
	 */
	public void setRecipient(String recipient) {
		if(TextUtils.isEmpty(recipient) == true) {
			throw new IllegalArgumentException("recipient is required");
		}
		this.recipient = recipient;
	}

	/**
	 * get the type of MeshMS message
	 * @return the type of MeshMS message
	 */
	public String getType() {
		return type;
	}

	/**
	 * set the type of MeshMS message
	 * @param type the new MeshMS type
	 * 
	 * @throws IllegalArgumentException if the type field is empty or null
	 */
	public void setType(String type) {
		if(TextUtils.isEmpty(type) == true) {
			throw new IllegalArgumentException("type is required");
		}
		this.type = type;
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

	/**
	 * get the list of content elements
	 * @return the list of content elements
	 */
	public ArrayList<MeshMSElement> getContent() {
		return content;
	}

	/**
	 * replace the list of content elements with a new list
	 * @param content the new list of content elements
	 * 
	 * @throws IllegalArgumentException if the content field is null
	 */
	public void replaceElementList(ArrayList<MeshMSElement> content) {
		if(content == null) {
			throw new IllegalArgumentException("content cannot be null");
		}
		this.content = content;
	}
	
	/**
	 * add a new element of content
	 * @param element a new element of content
	 * 
	 * @throws IllegalArgumentException if the element field is null
	 */
	public void addElement(MeshMSElement element) {
		if(element != null) {
			content.add(element);
		} else {
			throw new IllegalArgumentException("element is required");
		}
	}
	
	/*
	 * parcelable specific methods below here
	 */

	/*
	 * (non-Javadoc)
	 * @see android.os.Parcelable#describeContents()
	 */
	public int describeContents() {
		return 0;
	}

	/*
	 * write the content of the object to a parcel
	 * (non-Javadoc)
	 * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
	 */
	public void writeToParcel(Parcel dest, int flags) {
		// write the content to a parcel
		dest.writeString(sender);
		dest.writeString(recipient);
		dest.writeString(type);
		dest.writeLong(timestamp);
		dest.writeTypedList(content);
	}
	
	/*
	 * This defines how to regenerate the object
	 */
	public static final Parcelable.Creator<ComplexMeshMS> CREATOR = new Parcelable.Creator<ComplexMeshMS>() {
        public ComplexMeshMS createFromParcel(Parcel in) {
            return new ComplexMeshMS(in);
        }

        public ComplexMeshMS[] newArray(int size) {
            return new ComplexMeshMS[size];
        }
    };
    
    /*
     * undertake the process of regenerating the object
     */
    private ComplexMeshMS(Parcel in) {
    	sender = in.readString();
    	recipient = in.readString();
    	type = in.readString();
    	timestamp = in.readLong();
    	content = new ArrayList<MeshMSElement>();
    	in.readTypedList(content, MeshMSElement.CREATOR);
    }
}
