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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;

import android.util.Log;

/**
 * A class that reads a batmand.peers file and constructs objects that are easier to use
 * 
 * @author corey.wallis@servalproject.org
 *
 */
public class FileParser {
	
	/*
	 * private class constants
	 */
	
	private final boolean V_LOG = true;
	private final String TAG = "ServalBatman-FileParser";
	
	// declare private variables
	private String filePath = null;
	private int maxAge;
	
	/**
	 * Constructor for the class
	 * 
	 * @param path the path to the batmand.peers file
	 * @param maxAge the allowed age of the file in seconds
	 */
	public FileParser(String path, int maxAge) {
		
		// check on the parameters
		if(path.trim().equals("") == true) {
			throw new IllegalArgumentException("path parameter requires a non null string");
		}
		
		if(maxAge < 1) {
			throw new IllegalArgumentException("maxAge parameter must be greater than zero");
		}
		
		filePath = path;
		this.maxAge = maxAge;
		
	}
	
	/**
	 * Read the file at the path specified at the time of instantiation and return status of batman
	 * @return true if batman is running, and false if it isn't
	 * 
	 * @throws IOException if any IO operation on the file fails
	 */
	
	public boolean getStatus() throws IOException {
		
		// declare local variables
		boolean mStatus = true;
		
		// open the file
		InputStream mInput = new BufferedInputStream(new FileInputStream(filePath), 1024);
		
		// get the peer list offset
		int mInteger = byteArrayToInt(getFourBytes(mInput));
		
		//debug code
		if(V_LOG) {
			Log.v(TAG, "file offset: " + mInteger);
		}
		
		// skip to the start of the data
		if(mInput.skip(mInteger - 4) != (mInteger - 4)) { // skip starts from the current position & offset is defined from start of file
			throw new IOException("unable to skip to the required position in the file");
		}
		
		// get the time stamp
		mInteger = byteArrayToInt(getFourBytes(mInput));
		
		//debug code
		if(V_LOG) {
			Log.v(TAG, "time stamp: " + mInteger);
		}
		
		// get the current time and compare
		Date mNow = new Date();
		long mNowAsLong = mNow.getTime(); // current date as milliseconds
		mNowAsLong = mNowAsLong / 1000; // current date as seconds
		
		//debug code
		if(V_LOG) {
			Log.v(TAG, "now as seconds: " + mInteger);
		}
		
		if(mNowAsLong > (mInteger + maxAge)) {
			// file is stale assume not running
			mStatus = false;
		}
		
		// close the file
		mInput.close();
		
		return mStatus;
	}
	
	/**
	 * Read the file at the path specified at the time of instantiation and return the number of peers
	 * 
	 * @return the number of peers or -1 if the information is stale
	 * 
	 * @throws IOException if any IO operation on the file fails
	 */
	
	public int getPeerCount() throws IOException {
		
		//declare local variables
		int mPeerCount;
		
		// open the file
		InputStream mInput = new BufferedInputStream(new FileInputStream(filePath), 1024);
		
		// get the peer list offset
		int mInteger = byteArrayToInt(getFourBytes(mInput));
		
		//debug code
		if(V_LOG) {
			Log.v(TAG, "file offset: " + mInteger);
		}
		
		// get the peer count
		mPeerCount = byteArrayToInt(getFourBytes(mInput));
		
		// skip to the start of the data
		if(mInput.skip(mInteger - 8) != (mInteger - 8)) { // skip starts from the current position & offset is defined from start of file
			throw new IOException("unable to skip to the required position in the file");
		}
		
		// get the time stamp
		mInteger = byteArrayToInt(getFourBytes(mInput));
		
		//debug code
		if(V_LOG) {
			Log.v(TAG, "time stamp: " + mInteger);
		}
		
		// get the current time and compare
		Date mNow = new Date();
		long mNowAsLong = mNow.getTime(); // current date as milliseconds
		mNowAsLong = mNowAsLong / 1000; // current date as seconds
		
		//debug code
		if(V_LOG) {
			Log.v(TAG, "now as seconds: " + mInteger);
		}
		
		if(mNowAsLong > (mInteger + maxAge)) {
			// file is stale assume not running
			throw new IOException("the peer list file is stale");
		}
		
		// close the file
		mInput.close();
		
		
		// return the peerCount
		return mPeerCount;
	}
	
	/**
	 * Read the file at the path specified at the time of instantiation and return a list of peer records
	 * 
	 * @return a list of peer records or null if the list is stale
	 * 
	 * @throws IOException if any IO operation on the file fails
	 */
	public ArrayList<PeerRecord> getPeerList() throws IOException {
		
		// declare local variables
		ArrayList<PeerRecord> mPeerRecords = new ArrayList<PeerRecord>();
		int mPeerCount;
		
		// open the file
		InputStream mInput = new BufferedInputStream(new FileInputStream(filePath), 1024);
		
		// get the peer list offset
		int mInteger = byteArrayToInt(getFourBytes(mInput));
		
		//debug code
		if(V_LOG) {
			Log.v(TAG, "file offset: " + mInteger);
		}
		
		// get the peer count
		mPeerCount = byteArrayToInt(getFourBytes(mInput));
		
		// skip to the start of the data
		if(mInput.skip(mInteger - 4) != (mInteger - 4)) { // skip starts from the current position & offset is defined from start of file
			throw new IOException("unable to skip to the required position in the file");
		}
		
		// get the time stamp
		mInteger = byteArrayToInt(getFourBytes(mInput));
		
		//debug code
		if(V_LOG) {
			Log.v(TAG, "time stamp: " + mInteger);
		}
		
		// get the current time and compare
		Date mNow = new Date();
		long mNowAsLong = mNow.getTime(); // current date as milliseconds
		mNowAsLong = mNowAsLong / 1000; // current date as seconds
		
		//debug code
		if(V_LOG) {
			Log.v(TAG, "now as seconds: " + mInteger);
		}
		
		if(mNowAsLong > (mInteger + maxAge)) {
			// file is stale assume not running
			throw new IOException("the peer list file is stale");
		} else {
		
			// loop and get all of the records
			int mAddressType;
			String mAddress;
			int mLinkScore;
			
			for(int i = 0; i < mPeerCount; i++) {
				
				// get the address type
				mAddressType = mInput.read();
				
				// get the address
				mAddress = mInput.read() + ".";
				mAddress += mInput.read() + ".";
				mAddress += mInput.read() + ".";
				mAddress += mInput.read();
				
				// skip the next 28 bytes
				mInput.skip(28);
				
				// get the link score;
				mLinkScore = mInput.read();
				
				// build a new peer record object
				mPeerRecords.add(new PeerRecord(mAddressType, mAddress, mLinkScore));
			}
		}
		
		// close the file
		mInput.close();
		
		// return the list of records
		return mPeerRecords;
	}
	
	/*
	 * private method to get 4 bytes from the file
	 */
	private byte[] getFourBytes(InputStream mInput) throws IOException{
		
		byte[] mBytes = new byte[4];
		
		if(mInput.read(mBytes) != 4) {
			throw new IOException("unable to read the required number of bytes");
		} 
		
		return mBytes;
	}
	
	/*
	 * private method to convert a byte array into an int
	 */
	private int byteArrayToInt(byte[] bytes) {
		
		ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, bytes.length);
		buffer = buffer.order(ByteOrder.BIG_ENDIAN);
		return buffer.getInt();
		
	}
}
