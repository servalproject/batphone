package org.servalproject.dna;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.util.Log;

public class DataFile {

	private static FileInputStream hlrFile = null;

	private static boolean getFileHandle() {
		if (hlrFile != null) {
			try {
				hlrFile.close();
			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}
			hlrFile = null;
		}

		File f = new File("/data/data/org.servalproject/var/hlr.dat");
		try {
			hlrFile = new FileInputStream(f);
		} catch (FileNotFoundException e) {
			hlrFile = null;
		}
		return (hlrFile != null);
	}

	public static SubscriberId getSid(int record_offset) {

		if (!getFileHandle())
			return null;

		byte[] sid = new byte[32];
		try {
			hlrFile.skip(record_offset + 4);
			if (hlrFile.read(sid, 0, 32) < 32)
				return null;
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		return new SubscriberId(sid);
	}

	public static String getDid(int record_offset) {

		String did = "";

		if (!getFileHandle())
			return null;

		byte[] bytes = new byte[64];
		try {
			hlrFile.skip(record_offset + 4 + 32);
			int b = hlrFile.read(bytes, 0, 64);
			if (b<5) return null;
			if (bytes[0] == -128 /* 0x80 */) {
				int len = (bytes[1] << 8) + bytes[2];
				int maxdigits = len * 2;
				// int instance = bytes[3];
				int digit = -1;
				int n = 0;
				while (digit < 15 && did.length() < maxdigits) {
					digit = bytes[4 + (n >> 1)];
					if ((n & 1) == 0)
						digit = digit >> 4;
					digit &= 15;
					if (digit < 10) {
						did = did + digit;
					} else {
						switch (digit) {
						case 10:
							did = did + "*";
							break;
						case 11:
							did = did + "#";
							break;
						case 12:
							did = did + "+";
							break;
						}
					}
					n++;
				}
			}
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}



		return did;
	}

}
