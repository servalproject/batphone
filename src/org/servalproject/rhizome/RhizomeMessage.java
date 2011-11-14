package org.servalproject.rhizome;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class RhizomeMessage {

	public Map<RhizomeFieldCodes, byte[]> fields;

	public RhizomeMessage(String senderNumber, String number, String message) {
		fields = new HashMap<RhizomeFieldCodes, byte[]>();

		fields
				.put(RhizomeFieldCodes.senderDID,
						stringToByteArray(senderNumber));
		fields.put(RhizomeFieldCodes.recipientDID, stringToByteArray(number));
		fields.put(RhizomeFieldCodes.messageBody, stringToByteArray(message));
	}

	private byte[] stringToByteArray(String s) {
		char[] c = s.toCharArray();
		byte[] b = new byte[c.length];
		for (int i = 0; i < c.length; i++)
			b[i] = (byte) c[i];
		return b;
	}

	public byte[] toBytes() {
		Iterator<Map.Entry<RhizomeFieldCodes, byte[]>> i = fields.entrySet()
				.iterator();

		int size = 0;
		int j;
		int entryNumber=0;

		int entryCount = fields.size();
		boolean nullTerminatedEntry[] = new boolean[entryCount];
		byte[][] bodies = new byte[entryCount][];
		byte[] fieldCodes = new byte[entryCount];

		// One byte for message format code (0x01 for now)
		size += 1;

		while (i.hasNext()) {
			Entry<RhizomeFieldCodes, byte[]> e = i.next();

			size += 1;

			bodies[entryNumber] = e.getValue();

			int fieldLength = e.getValue().length;
			size += fieldLength;
			if (fieldLength > 65535) {
				// 32 bit length field.
				nullTerminatedEntry[entryNumber] = false;
				size += 4;
			} else {
				byte[] v = e.getValue();
				for (j = 0; j < fieldLength; j++)
					if (v[j] == 0)
						break;
				if (j < fieldLength)
				{
					// We can't null terminate it, so we have to include 16 bit
					// length.
					size += 2;
					nullTerminatedEntry[entryNumber] = false;
				}
				else
				{
					// no null characters, so we can null terminate it at the
					// cost of one byte.
					size += 1;
					nullTerminatedEntry[entryNumber] = true;
				}
			}
			fieldCodes[entryNumber] = getFieldCode(e.getKey(), fieldLength,
					nullTerminatedEntry[entryNumber]);

			entryNumber++;
		}

		byte[] sizeField = createSizeField(size);

		byte[] b = new byte[size + sizeField.length];

		// XXX - Now that we know the size, populate the array
		int offset=0;
		b[offset++]=0x01; // Rhizome Message Format

		for (entryNumber = 0; entryNumber < entryCount; entryNumber++)
		{
			b[offset] = fieldCodes[entryNumber];
			offset++;
			if (fieldCodes[entryNumber] >= 0x00
					&& fieldCodes[entryNumber] < 0x10) {
				// Field is fixed 32 bytes
				if (bodies[entryNumber].length != 32) {
					throw new RuntimeException(
							"SID-type field was not SID-length (32 bytes)");
				}
				System.arraycopy(bodies[entryNumber], 0, b, offset,
						bodies[entryNumber].length);
				offset += bodies[entryNumber].length;

			} else {
				int formatBits = (fieldCodes[entryNumber] & 0xc0);
				if (formatBits < 0)
					formatBits += 0x100;
				switch (formatBits) {
				case 0x00:
					// null termianted string, so nothing to put here
					break;
				case 0x40:
					// 16 bit length identifier
					b[offset++] = (byte) ((bodies[entryNumber].length >> 8) & 0xff);
					b[offset++] = (byte) ((bodies[entryNumber].length >> 0) & 0xff);
					break;
				case 0x80:
					// 32 bit length identifier
					b[offset++] = (byte) ((bodies[entryNumber].length >> 24) & 0xff);
					b[offset++] = (byte) ((bodies[entryNumber].length >> 16) & 0xff);
					b[offset++] = (byte) ((bodies[entryNumber].length >> 8) & 0xff);
					b[offset++] = (byte) ((bodies[entryNumber].length >> 0) & 0xff);
					break;
				case 0xc0:
					throw new RuntimeException(
							"Field format 0xc0 is undefined in Rhizome message format specification");
				}

				System.arraycopy(bodies[entryNumber], 0, b, offset,
						bodies[entryNumber].length);
				offset += bodies[entryNumber].length;

				// Null terminate if required
				if (formatBits == 0x00)
					b[offset++] = 0x00;
			}
		}

		System.arraycopy(sizeField, 0, b, offset, sizeField.length);
		offset += sizeField.length;
		if (offset != b.length)
			throw new RuntimeException(
					"Rhizome message length was not as predicted");

		return b;
	}

	private byte getFieldCode(RhizomeFieldCodes code,
			int fieldLength,
			boolean nullTerminatedStringP) {
		int lowBits = code.getCode();
		int formatBits = 0;
		if (fieldLength > 65535)
			formatBits = 0x80;
		if (nullTerminatedStringP == false)
			formatBits = 0x40;

		return (byte) (lowBits | formatBits);
	}

	private byte[] createSizeField(int size) {
		byte[] b;

		if (size < 1)
			return null;
		if (size<256) {
			b = new byte[1];
			b[0] = (byte) (size - 1);
			return b;
		}
		if (size < 506) {
			b = new byte[2];
			b[0] = (byte) (size - 256);
			b[1] = (byte) 0xff;
			return b;
		}
		if (size < 762) {
			b = new byte[3];
			b[0] = (byte) (size - 506);
			b[1] = (byte) 0xfa;
			b[2] = (byte) 0xff;
			return b;
		}
		if (size < 65536) {
			b = new byte[4];
			b[0] = (byte) (size >> 8);
			b[1] = (byte) (size & 0xff);
			b[2] = (byte) 0xfb;
			b[3] = (byte) 0xff;
			return b;
		}

		b = new byte[6];
		b[0] = (byte) ((size >> 24) & 0xff);
		b[1] = (byte) ((size >> 16) & 0xff);
		b[2] = (byte) ((size >> 8) & 0xff);
		b[3] = (byte) (size & 0xff);
		b[4] = (byte) 0xfb;
		b[5] = (byte) 0xff;
		return b;
	}

}
