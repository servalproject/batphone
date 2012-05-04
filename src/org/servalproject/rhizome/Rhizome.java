package org.servalproject.rhizome;

import java.io.File;
import java.io.IOException;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.SubscriberId;
import org.servalproject.servald.ServalD.RhizomeExtractManifestResult;
import org.servalproject.servald.ServalD.RhizomeExtractFileResult;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDInterfaceError;

import android.util.Log;
import android.os.Environment;

public class Rhizome {

	/** TAG for debugging */
	public static final String TAG = "R3";

	/** Display a toast message in a toast.
	 */
	public static void goToast(String text) {
		ServalBatPhoneApplication.context.displayToastMessage(text);
	}

	public static boolean appendMessage(SubscriberId sid, byte[] bytes) {
		Log.w(TAG, "Rhizome.appendMessage(sid=" + sid + ", bytes=[..." + bytes.length + "...]) NOT IMPLEMENTED");
		return false;
	}

	/** Add a file (payload) to the rhizome store, creating a basic manifest for it.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean addFile(File path) {
		Log.w(TAG, "Rhizome.addFile(path=" + path + ")");
		return false;
	}

	/** Return the path of the directory where saved rhizome files are stored.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static File getSaveDirectory() {
		return new File(Environment.getExternalStorageDirectory(), "/serval/rhizome/saved");
	}

	/** Return the path of the directory where saved rhizome files are stored, after ensuring that
	 * the directory exists.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
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

	/** Extract a manifest and its payload (a "bundle") from the rhizome database.  Stores them
	 * in a pair of files in the rhizome "saved" directory, overwriting any files that may
	 * already be there with the same name.  The "saved" directory is created if it does not yet
	 * exist.  Leading dots are stripped from the name, to ensure that the file is visible to most
	 * file browsers and to avoid collisions with the namifest files.  Manifest file names are
	 * formed as ".manifest." + strippedName.
	 *
	 * @param manifestId	The manifest ID of the bundle to extract
	 * @param name			The basename to give the payload file in the "saved" directory.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean extractFile(String manifestId, String name) {
		try {
			String strippedName = name;
			while (strippedName.startsWith("."))
				strippedName = strippedName.substring(1);
			if (strippedName.length() == 0)
				throw new IOException("pathological name '" + name + "'");
			File savedDir = Rhizome.getSaveDirectoryCreated();
			File savedManifestFile = new File(savedDir, ".manifest." + strippedName);
			File savedPayloadFile = new File(savedDir, strippedName);
			// A manifest file without a payload file is ok, but not vice versa.  So always
			// delete manifest files last and create them first.
			savedPayloadFile.delete();
			savedManifestFile.delete();
			boolean done = false;
			try {
				RhizomeExtractManifestResult mres = ServalD.rhizomeExtractManifest(manifestId, savedManifestFile);
				RhizomeExtractFileResult fres = ServalD.rhizomeExtractFile(mres.fileHash, savedPayloadFile);
				if (!mres.fileHash.equals(fres.fileHash))
					Log.w(Rhizome.TAG, "extracted file hashes differ: mres.fileHash=" + mres.fileHash + ", fres.fileHash=" + fres.fileHash);
				if (mres.fileSize != fres.fileSize)
					Log.w(Rhizome.TAG, "extracted file lengths differ: mres.fileSize=" + mres.fileSize + ", fres.fileSize=" + fres.fileSize);
				done = true;
			}
			finally {
				if (!done) {
					try {
						savedPayloadFile.delete();
					}
					catch (SecurityException ee) {
						Log.w(Rhizome.TAG, "could not delete '" + savedPayloadFile + "'", ee);
					}
					try {
						savedManifestFile.delete();
					}
					catch (SecurityException ee) {
						Log.w(Rhizome.TAG, "could not delete '" + savedManifestFile + "'", ee);
					}
				}
			}
			return done;
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
		}
		catch (IOException e) {
			Log.e(Rhizome.TAG, "cannot save manifestId=" + manifestId + ", name=" + name, e);
		}
		return false;
	}

}
