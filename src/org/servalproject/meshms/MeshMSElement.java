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
 * This class represents an element of content in a complex MeshMS message
 */
public class MeshMSElement implements Parcelable {
	
	// store the required fields
	private String type;
	private String content;
	
	/**
	 * Construct a MeshMS element record
	 * 
	 * @param type the mime type of this record
	 * @param content  the content of this record
	 * 
	 * @throws IllegalArgumentException if the type parameter is empty or null
	 */
	public MeshMSElement(String type, String content) {
		
		// validate the parameters
		if(TextUtils.isEmpty(type) == true) {
			throw new IllegalArgumentException("type is required");
		}

		this.type = type;
		this.content  = content;
		
	}
	
	/**
	 * return the type of this element
	 * @return the type of the element
	 */
	public String getType() {
		return type;
	}
	
	/**
	 * get the content of this content element
	 * @return the content
	 */
	public String getContent() {
		return content;
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
		dest.writeString(type);
		dest.writeString(content);
	}
	
	/*
	 * This defines how to regenerate the object
	 */
	public static final Parcelable.Creator<MeshMSElement> CREATOR = new Parcelable.Creator<MeshMSElement>() {
        public MeshMSElement createFromParcel(Parcel in) {
            return new MeshMSElement(in);
        }

        public MeshMSElement[] newArray(int size) {
            return new MeshMSElement[size];
        }
    };
    
    /*
     * undertake the process of regenerating the object
     */
    private MeshMSElement(Parcel in) {
    	type = in.readString();
    	content  = in.readString();
    }

}
