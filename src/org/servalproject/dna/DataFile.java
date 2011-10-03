package org.servalproject.dna;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import android.util.Log;

public class DataFile {

	private static FileInputStream hlrFile = null;

	private static boolean getFileHandle() {
		if (hlrFile == null)
 {
			File f = new File("/data/data/org.servalproject/var/hlr.dat");
			try {
				hlrFile = new FileInputStream(f);
			} catch (FileNotFoundException e) {
				Log.e("BatPhone", e.toString(), e);
				hlrFile = null;
			}
		}

		if (hlrFile == null)
			return false;
		else
			return true;
	}

	/*
	 * public static SubscriberId getSid(int record_offset) {
	 * 
	 * if (getFileHandle() == false) return null;
	 * 
	 * if (hlrFile == null)
	 * 
	 * return null; }
	 */
}
