package org.servalproject.rhizome;

import android.util.Log;

public class RhizomeMessage {

	String senderNumber;
	String number;
	String message;

	public RhizomeMessage(String senderNumber, String number, String message) {
	}


	public byte[] toBytes() {
		return "Wah".getBytes();
	}

}
