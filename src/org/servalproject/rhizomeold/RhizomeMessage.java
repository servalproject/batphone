package org.servalproject.rhizomeold;

import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import android.util.Log;

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

	public RhizomeMessage(RandomAccessFile in, long messageOffset,
			long messageLength) {
		// Read bytes of message and populate map of fields
		long bytesRemaining = messageLength;
		fields = new HashMap<RhizomeFieldCodes, byte[]>();
		try {
			in.seek(messageOffset);
			int b = in.read();
			bytesRemaining--;
			messageOffset++;
			if (b != 0x01) {
				Log.i("Rhizome",
						"Encountered a message with a newer version field ("
								+ b + "). Ignoring.");
			}
			// Now read fields
			while (bytesRemaining > 0) {
				in.seek(messageOffset);
				int fieldCode = in.read();
				// Find out how many bytes are consumed by the field length
				// specification
				int fieldLengthSize = getFieldLengthSize(fieldCode);
				byte[] field = getField(in, fieldCode, bytesRemaining);

				if (field == null)
					break;

				fields.put(RhizomeFieldCodes.byteToCode(fieldCode), field);

				// Now advance messageOffset
				messageOffset += 1; // field code
				messageOffset += fieldLengthSize; // field length specification
				messageOffset += field.length; // length of field itself
				bytesRemaining -= 1 + fieldLengthSize + field.length;
			}
		} catch (Exception e) {
			Log.e("Rhizome", "Caught exception in RhizomeMessage: "
					+ e.toString());
		}
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
		int entryNumber = 0;

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
				// If the field is short, then consider null termination
				if (fieldLength > 255)
					j = -1;
				else
					for (j = 0; j < fieldLength; j++)
						if (v[j] == 0)
							break;
				if (j < fieldLength) {
					// We can't null terminate it, so we have to include 16 bit
					// length.
					size += 2;
					nullTerminatedEntry[entryNumber] = false;
				} else {
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
		int offset = 0;
		b[offset++] = 0x01; // Rhizome Message Format

		for (entryNumber = 0; entryNumber < entryCount; entryNumber++) {
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

	private byte getFieldCode(RhizomeFieldCodes code, int fieldLength,
			boolean nullTerminatedStringP) {
		int lowBits = code.getCode();
		int formatBits = 0;
		if (fieldLength > 65535)
			formatBits = 0x80;
		if (nullTerminatedStringP == false)
			formatBits = 0x40;

		return (byte) (lowBits | formatBits);
	}

	private int getFieldLengthSize(int fieldCode) {
		if (fieldCode < 0)
			fieldCode += 0x100;
		if (fieldCode < 0x10)
			// implied length
			return 0;
		if (fieldCode < 0x40)
			// one null character
			return 1;
		if (fieldCode < 0x80)
			// 16 bit length specifier
			return 2;
		if (fieldCode < 0xc0)
			// 32 byte length specifier
			return 4;
		else
			return -1;
	}

	private byte[] getField(RandomAccessFile in, int fieldCode,
			long bytesRemaining) {

		int fieldLength = 0;

		if (fieldCode < 0)
			fieldCode += 0x100;

		// SID field, so implied length of 32 bytes
		if (fieldCode < 0x10) {
			if (bytesRemaining < 32)
 {
				Log.e("Rhizome", "SID format field was not 32 bytes");
				return null;
			}
			fieldLength = 32;
		}

		// Null terminated string - read and return in one go
		else if (fieldCode < 0x40) {
			int maxLen = 256;
			if (maxLen > bytesRemaining)
				maxLen = (int) bytesRemaining;
			byte[] field = new byte[maxLen];
			try {
				in.read(field, 0, maxLen);
				for (int i = 0; i < maxLen; i++)
					if (field[i] == 0) {
						byte[] returnField = new byte[i];
						System.arraycopy(field, 0, returnField, 0, i);
						return returnField;
					}
			} catch (Exception e) {
				Log.e("Rhizome", "Exception occurred in getField(): "
						+ e.toString());
				return null;
			}
		}

		// 16 bit length field
		else if (fieldCode < 0x80) {
			try {
				int b = in.read();
				fieldLength = b << 8;
				b = in.read();
				fieldLength |= b;
				bytesRemaining -= 2;
			} catch (Exception e) {
				Log.e("Rhizome", "Exception occurred in getField() (16 bit): "
						+ e.toString());
				return null;
			}
		}

		// 32 bit length field
		else if (fieldCode < 0xc0) {
			try {
				int b;
				b = in.read();
				fieldLength = b << 24;
				b = in.read();
				fieldLength = b << 16;
				b = in.read();
				fieldLength = b << 8;
				b = in.read();
				fieldLength |= b;
				bytesRemaining -= 4;
			} catch (Exception e) {
				Log.e("Rhizome", "Exception occurred in getField() (32bit): "
						+ e.toString());
				return null;
			}
		}

		// Illegal field length modifier on code
		else {
			Log.e("Rhizome",
					"Illegal field length modifier bits on field code "
							+ fieldCode);
			return null;
		}

		if (fieldLength < 0 || fieldLength > bytesRemaining) {
			Log
					.e("Rhizome", "Illegal field length of " + fieldLength
							+ " in message with " + bytesRemaining
							+ " of data unread.");
			return null;
		}

		// Now that we know the length, and are seeked to the right place, let's
		// read the field
		byte[] field = new byte[fieldLength];
		try {
			int bytesRead = in.read(field);
			if (bytesRead != fieldLength) {
				Log.e("Rhizome", "in.read(field) failed to read entire field.");
				return null;
			}
			return field;
		} catch (Exception e) {
			Log.e("Rhizome", "Exception encountered while reading field of "
					+ fieldLength + " bytes.");
			return null;
		}
	}

	public static long parseSizeField(RandomAccessFile s, long offset) {
		try {
			long length = 0;
			s.seek(offset - 1);
			int b = s.read();
			if (b < 255)
				return b + 1;
			s.seek(offset - 2);
			b = s.read();
			switch (b) {
			case 0xfa:
				s.seek(offset - 3);
				b = s.read();
				return b + 506;
			case 0xfb: // 16 bits
				s.seek(offset - 4);
				b = s.read();
				length |= b << 8;
				b = s.read();
				length |= b << 0;
				return length;
			case 0xfc: // 32 bits
				s.seek(offset - 6);
				b = s.read();
				length |= b << 24;
				b = s.read();
				length |= b << 16;
				b = s.read();
				length |= b << 8;
				b = s.read();
				length |= b << 0;
				return length;
			case 0xfd:
			case 0xfe:
			case 0xff:
				// Undefined codes
				return 0;
			default:
				return b + 256;
			}
		} catch (Exception e) {
			Log
					.e("Rhizome",
					"An exception occurred while trying to parse length field."
							+ e.toString());
			return 0;
		}
	}

	private byte[] createSizeField(int size) {
		byte[] b;

		if (size < 1)
			return null;
		if (size < 256) {
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
		b[4] = (byte) 0xfc;
		b[5] = (byte) 0xff;
		return b;
	}

	public static long sizeFieldLength(long s) {
		if (s < 1)
			return 0;
		if (s < 256)
			return 1;
		if (s < 506)
			return 2;
		if (s < 762)
			return 3;
		if (s < 65536)
			return 5;
		if (s < 65536 * 65536)
			return 6;
		else
			// XXX This isn't really defined yet.
			return 10;
	}

	public String getBody() {
		return new String(fields.get(RhizomeFieldCodes.messageBody));
	}

	public String getSender() {
		return new String(fields.get(RhizomeFieldCodes.senderDID));
	}

	public String getRecipient() {
		return new String(fields.get(RhizomeFieldCodes.recipientDID));
	}

}
