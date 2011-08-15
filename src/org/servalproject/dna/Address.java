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

package org.servalproject.dna;

import java.net.InetAddress;

public class Address {
	public InetAddress addr;
	public int port;

	public Address(InetAddress addr, int port){
		this.addr=addr;
		this.port=port;
	}
	@Override
	public String toString() {
		return addr.toString()+":"+port;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Address){
			Address a=(Address) o;
			return a.addr.equals(this.addr)&&a.port==this.port;
		}
		return false;
	}
	@Override
	public int hashCode() {
		return this.port ^ addr.hashCode();
	}

}
