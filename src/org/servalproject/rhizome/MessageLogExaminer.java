package org.servalproject.rhizome;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.dna.DataFile;
import org.servalproject.meshms.SimpleMeshMS;

import android.content.Intent;
import android.util.Log;

public class MessageLogExaminer {

	public static void examineLog(String file) {
		Log.i("RhizomeMessageLogExaminer", "Examining log file: " + file);

		long lastOffset = readOffsetFile(file);
		File of = new File(file);
		long size = of.length();

		// Don't read the same message more than once
		writeOffsetFile(file, size);

		long currentOffset = size;
		RandomAccessFile in = null;
		try {
			in = new RandomAccessFile(file, "rw");
			while (currentOffset > lastOffset) {
				long messageLength = RhizomeMessage.parseSizeField(in,
						currentOffset);
				if (messageLength < 1)
					break;
				long lengthLength = RhizomeMessage
						.sizeFieldLength(messageLength);
				long messageOffset = currentOffset - messageLength
						- lengthLength;
				in.seek(messageOffset);
				RhizomeMessage m = new RhizomeMessage(in, messageOffset,
						messageLength);
				Log.i("Rhizome", "Saw SMS from " + m.getSender() + "to "
						+ m.getRecipient() + ", my number is "
						+ DataFile.getDid(0));

				// construct a new SimpleMeshMS from the message
				// TODO deal with other message types
				SimpleMeshMS mMessage = new SimpleMeshMS(m.getSender(),
						m.getRecipient(), m.getBody());
				Intent mIntent;

				// decide on the intent to send
				if (m.getRecipient().equalsIgnoreCase(DataFile.getDid(0))
						|| m.getRecipient().equalsIgnoreCase("*")) {
					// send the standard intent
					// fix the recipient number so it is no longer star
					mMessage.setRecipient(DataFile.getDid(0));

					mIntent = new Intent(
							"org.servalproject.meshms.RECEIVE_MESHMS");
					mIntent.putExtra("simple", mMessage);
					ServalBatPhoneApplication.context.sendBroadcast(mIntent);

					Log.i("Rhizome", "Sent a SimpleMeshM using RECEIVE_MESHMS");
				} else {
					// send the broadcast intent

					mIntent = new Intent(
							"org.servalproject.meshms.RECEIVE_BROADCASTS");
					mIntent.putExtra("simple", mMessage);
					ServalBatPhoneApplication.context.sendBroadcast(mIntent);

					Log.i("Rhizome",
							"Sent a SimpleMeshM using RECEIVE_BROADCASTS");
				}

				// if (m.getRecipient().equalsIgnoreCase(DataFile.getDid(0))
				// || m.getRecipient().equalsIgnoreCase("*"))
				// Receiver.writeSMS(m.getSender(), m.getBody(),
				// ServalBatPhoneApplication.context);

				currentOffset = messageOffset;
			}
			in.close();
		} catch (Exception e) {

		} finally {
			try {
				if (in != null)
					in.close();
			} catch (Exception e) {

			}
		}

	}

	private static long readOffsetFile(String file) {
		// Get last offset of this file that we have read
		File f = new File(file);
		String offsetFile = RhizomeUtils.dirRhizome + "/." + f.getName()
				+ ".offset";
		File of = new File(offsetFile);
		byte[] offsetBytes;
		try {
			offsetBytes = RhizomeUtils.readFileBytes(of);
		} catch (Exception e) {
			return 0;
		}
		long offset = 0;
		if (offsetBytes != null) {
			for (int i = 0; i < offsetBytes.length; i++) {
				int b = offsetBytes[i];
				if (b < 0)
					b += 0x100;
				offset = offset << 8;
				offset |= b;
			}
		}
		return offset;
	}

	private static void writeOffsetFile(String file, long offset) {
		File f = new File(file);
		String offsetFile = RhizomeUtils.dirRhizome + "/." + f.getName()
				+ ".offset";
		byte[] bytes = new byte[8];
		bytes[0] = (byte) ((offset >> 56) & 0xff);
		bytes[1] = (byte) ((offset >> 48) & 0xff);
		bytes[2] = (byte) ((offset >> 40) & 0xff);
		bytes[3] = (byte) ((offset >> 32) & 0xff);
		bytes[4] = (byte) ((offset >> 24) & 0xff);
		bytes[5] = (byte) ((offset >> 16) & 0xff);
		bytes[6] = (byte) ((offset >> 8) & 0xff);
		bytes[7] = (byte) ((offset >> 0) & 0xff);
		try {
			FileOutputStream o = new FileOutputStream(offsetFile, false);
			o.write(bytes);
			o.close();
		} catch (Exception e) {
			Log.e("Rhizome", "Could not write new offset to " + offsetFile);
		}
	}

}
