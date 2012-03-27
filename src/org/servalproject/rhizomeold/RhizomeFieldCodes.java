package org.servalproject.rhizomeold;

public enum RhizomeFieldCodes {

	recipientSID(0x00), senderSID(0x01), recipientDID(0x10), senderDID(0x11), messageBody(
			0x3e), messageSignature(0x3f), illegal(0xff);

	private int code;

	private RhizomeFieldCodes(int c) {
		code = c;
	}

		 public int getCode() {
		return code;
	}

	public static RhizomeFieldCodes byteToCode(int b) {
		switch (b & 0x3f) {
		case 0x00:
			return recipientSID;
		case 0x01:
			return senderSID;
		case 0x10:
			return recipientDID;
		case 0x11:
			return senderDID;
		case 0x3e:
			return messageBody;
		case 0x3f:
			return messageSignature;
		default:
			return illegal;
		}
	}

}
