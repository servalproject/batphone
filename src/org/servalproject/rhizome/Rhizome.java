package org.servalproject.rhizome;

import android.util.Log;
import org.servalproject.dna.SubscriberId;
import org.servalproject.rhizome.RhizomeMain;

public class Rhizome {

	/** TAG for debugging */
	public static final String TAG = "R3";

	public static boolean appendMessage(SubscriberId sid, byte[] bytes) {
		Log.w(TAG, "Rhizome.appendMessage(sid=" + sid + ", bytes=[..." + bytes.length + "...]) NOT IMPLEMENTED");
		return false;
	}

}
