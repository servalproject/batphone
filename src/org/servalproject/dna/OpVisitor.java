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

import java.nio.ByteBuffer;

import org.servalproject.Instrumentation;
import org.servalproject.dna.OpDT.DTtype;

public abstract class OpVisitor {

	public void onPacketArrived(Packet packet, PeerConversation peer){

	}
	public boolean onSimpleCode(Packet packet, OpSimple.Code code){
		throw new UnsupportedOperationException(code.name());
	}
	public boolean onError(Packet packet, String error){
		throw new IllegalStateException(error);
	}
	public boolean onGet(Packet packet, VariableRef reference){
		throw new UnsupportedOperationException(reference.toString());
	}
	public boolean onStat(Packet packet, Instrumentation.Variable field,int value){
		throw new UnsupportedOperationException(""+field+"="+value+"");
	}
	public boolean onData(Packet packet, VariableRef reference, short varLen, ByteBuffer buffer){
		throw new UnsupportedOperationException(reference.toString());
	}
	public boolean onSet(Packet packet, VariableRef reference, OpSet.Flag flag, ByteBuffer buffer){
		throw new UnsupportedOperationException(reference.toString());
	}
	public boolean onWrote(Packet packet, VariableRef reference){
		throw new UnsupportedOperationException(reference.toString());
	}
	public boolean onTTL(Packet packet, int ttl) {
		// PGS BUG XXX Has to be commented out, otherwise setting phone number
		// throws this exception. Not sure why that happens
		// throw new UnsupportedOperationException("TTL: " +
		// Integer.toString(ttl));
		return false;
	}
	public boolean onDone(Packet packet, byte count){
		throw new UnsupportedOperationException("Count: "+Integer.toString(count));
	}
	public boolean onDT(Packet packet, DTtype messageType, String emitterPhoneNumber, String message){
		throw new UnsupportedOperationException("DT type "+messageType+" from "+emitterPhoneNumber+" : '"+message+"'");
	}

}
