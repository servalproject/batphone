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

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;

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
	
	// declare private variables
	private String filePath = null;
	private final int maxAge = 5;
	
	private int lastTimestamp=-1;
	private int lastOffset=-1;
	
	private ArrayList<PeerRecord> peers;
	
	/**
	 * Constructor for the class
	 * 
	 * @param path the path to the batmand.peers file
	 */
	public FileParser(String path) {
		filePath = path;
	}
	
	private int readPeerCount(DataInputStream data) throws IOException{
		// get the peer list offset
		int offset = data.readInt();
		if (lastOffset!=offset){
			peers=null;
			lastOffset=offset;
		}
		int peerCount = data.readInt();
		
		// skip to the start of the data
		if(data.skip(offset - 4) != (offset - 4)) { // skip starts from the current position & offset is defined from start of file
			throw new IOException("unable to skip to the required position in the file");
		}
		
		int timestamp=data.readInt();
		
		// drop the cached peer list if the file has changed
		if (timestamp!=lastTimestamp)
			peers=null;
		
		lastTimestamp=timestamp;
		
		// compare to current time
		long dateInSeconds = new Date().getTime() / 1000;
		if (dateInSeconds > (timestamp + maxAge))
			return -1;
		
		return peerCount;
	}
	
	/**
	 * Read the file at the path specified at the time of instantiation and return the number of peers
	 * 
	 * @return the number of peers or -1 if the information is stale
	 * 
	 * @throws IOException if any IO operation on the file fails
	 */
	
	public int getPeerCount() throws IOException {
		
		DataInputStream data = new DataInputStream(new FileInputStream(filePath));
		try{
			return readPeerCount(data);
		}finally{
			data.close();
		}
	}
	
	/**
	 * Read the file at the path specified at the time of instantiation and return a list of peer records
	 * 
	 * @return a list of peer records or null if the list is stale
	 * 
	 * @throws IOException if any IO operation on the file fails
	 */
	public ArrayList<PeerRecord> getPeerList() throws IOException {
		
		DataInputStream data = new DataInputStream(new FileInputStream(filePath));
		try{
			int peerCount=readPeerCount(data);
			if (peerCount<0)
				throw new IOException("the peer list file is stale");
			
			if (peers==null){
				peers = new ArrayList<PeerRecord>();
				for(int i = 0; i < peerCount; i++) {
					int addressType=data.read();
					byte addr[];
					switch (addressType){
					case 4:
						addr=new byte[4];
						break;
					case 6:
						addr=new byte[16];
						break;
						default:
							throw new IOException("Invalid address type "+addressType);
					}
					data.read(addr);
					data.skip(32 - addr.length);
					
					int linkScore=data.read();
					
					peers.add(new PeerRecord(InetAddress.getByAddress(addr), linkScore));
				}
			}
			return peers;
		}finally{
			data.close();
		}
	}
	
}
