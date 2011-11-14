/*
 * This file is part of the Serval Mapping Services app.
 *
 *  Serval Mapping Services app is free software: you can redistribute it 
 *  and/or modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation, either version 3 of 
 *  the License, or (at your option) any later version.
 *
 *  Serval Mapping Services app is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Serval Mapping Services app.  
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package org.servalproject.rhizome.peers;

import java.util.ArrayList;

/**
 * A class to represent a list of batman peers that can be shared
 * between threads
 *
 */
public class BatmanPeerList {
	
	/*
	 * class level variables
	 */
	private volatile ArrayList<String> peerList = new ArrayList<String>();
	
	
	/**
	 * update the peer list with new values
	 * 
	 * @param newValues a list of new batman peers
	 */
	public synchronized void updatePeerList(String[] newValues) {
		
		// update the list as necessary
		if(newValues == null) {
			peerList.clear();
			return;
		} 
		
		if(newValues.length == 0) {
			peerList.clear();
			return;
		} else {
			peerList.clear();
			
			for(int i = 0; i < newValues.length; i++) {
				peerList.add(newValues[i]);
			}
			return;
		}
	}
	
	/**
	 * get the peer list
	 * 
	 * @return a string array containing the peer list
	 */
	public synchronized String[] getPeerList() {
			if(peerList.size() == 0) {
				return new String[0];
			} else {
				return peerList.toArray(new String[0]);
			}
	}
	
	/**
	 * get the number of peers in the list
	 * @return a integer specifying the number of peers
	 */
	public synchronized int getPeerCount() {
		return peerList.size();
	}
}
