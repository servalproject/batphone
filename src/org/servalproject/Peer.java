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

import org.servalproject.servald.DidResult;
import org.servalproject.servald.SubscriberId;

public class Peer extends DidResult {
	public boolean displayed = false;
	public int score;
	public long contactId = -1;
	private String contactName;
	public boolean resolved = false;

	public Peer() {
		super();
	}

	public Peer(SubscriberId sid, String did, String name, long contactId,
			String contactName, boolean resolved) {
		super();
		this.sid = sid;
		this.did = did;
		this.name = name;
		this.contactId = contactId;
		this.contactName = contactName;
		this.resolved = resolved;
	}

	public String getSortString() {
		return getContactName() + did + sid;
	}

	public String getName() {
		if (name == null)
			return "";
		return name;
	}

	public String getContactName() {
		if (contactName == null)
			return getName();
		return contactName;
	}

	public void setContactName(String contactName) {
		this.contactName = contactName;
	}

	@Override
	public String toString() {
		if (contactName != null && !contactName.equals(""))
			return contactName;
		if (name != null && !name.equals(""))
			return name;
		if (did != null && !did.equals(""))
			return did;
		return sid.abbreviation();
	}
}
