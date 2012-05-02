package org.servalproject.rhizome;

import java.io.File;
import java.io.IOException;

import org.servalproject.servald.SubscriberId;
import android.util.Log;
import android.os.Environment;

public class Rhizome {

	/** TAG for debugging */
	public static final String TAG = "R3";

	public static boolean appendMessage(SubscriberId sid, byte[] bytes) {
		Log.w(TAG, "Rhizome.appendMessage(sid=" + sid + ", bytes=[..." + bytes.length + "...]) NOT IMPLEMENTED");
		return false;
	}

	/** Return the path of the directory where saved rhizome files are stored.
	 */
	public static File getSaveDirectory() {
		return new File(Environment.getExternalStorageDirectory(), "/serval/rhizome/saved");
	}

	/** Return the path of the directory where saved rhizome files are stored, after ensuring that
	 * the directory exists.
	 */
	public static File getSaveDirectoryCreated() throws IOException {
		File dir = getSaveDirectory();
		try {
			if (!dir.isDirectory() && !dir.mkdir())
				throw new IOException("cannot mkdir " + dir);
			return dir;
		}
		catch (SecurityException e) {
			throw new IOException("no permission to create " + dir);
		}
	}

}
