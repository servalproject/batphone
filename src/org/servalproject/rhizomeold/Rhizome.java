package org.servalproject.rhizomeold;

import java.io.File;
import java.io.IOException;

import org.servalproject.servald.SubscriberId;

import android.util.Log;

public class Rhizome {

	public static boolean appendMessage(SubscriberId sid, byte[] bytes) {
		// Actually write the specified message into the primary subscribers

		String messageLogName;
		String manifestFileName;
		String messageLogFileName;

		messageLogName = sid.toString() + ".rpml";

		try {
			// XXX Should use monotonic version numbers, not system time,
			// as it is possible for someone to conceivably send 2 messages
			// in one second, and in that case recipients may not get the
			// messages after the first, and may generally get upset about
			// mis-matched file hashes and sizes etc.
			long version = System.currentTimeMillis();
			RhizomeFile r;
			try {
				r = new RhizomeFile(messageLogName, bytes, true);
			} catch (IOException e) {
				r = new RhizomeFile(messageLogName, bytes, false);
			}
			RhizomeFile.GenerateManifestForFilename(new File(
					RhizomeUtils.dirRhizome, messageLogName), sid
					.toString(), version);
			// Create silently the meta data
			RhizomeFile.GenerateMetaForFilename(messageLogName, version);

			return true;
		} catch (IOException e) {
			Log.e("BatphoneRhizome", e.toString(), e);
			return false;
		}
	}

}
