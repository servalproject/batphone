package org.servalproject.rhizome;

public enum RhizomeFieldCodes {

	recipientSID(0x00), senderSID(0x01), recipientDID(0x10), senderDID(0x11), messageBody(
			0x3e), messageSignature(0x3f), ;

	private int code;

	private RhizomeFieldCodes(int c) {
		code = c;
	}

		 public int getCode() {
		return code;
	}

}
