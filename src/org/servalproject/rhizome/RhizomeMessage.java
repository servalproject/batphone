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

package org.servalproject.rhizome;

import org.servalproject.servald.SubscriberId;

public class RhizomeMessage {

	public final SubscriberId sender;
	public final SubscriberId recipient;
	public final String message;

	public RhizomeMessage(SubscriberId sender, SubscriberId recipient, String message) {
		this.sender = sender;
		this.recipient = recipient;
		this.message = message;
	}

	public byte[] toBytes() {
		return "Wah".getBytes();
	}

	public String toString() {
		return this.getClass().getName() + "(sender=" + this.sender + ", recipient=" + this.recipient + ", message='" + this.message + "')";
	}

	public boolean send() {
		return Rhizome.sendMessage(this);
	}

}
