package org.servalproject.rhizome;

import org.servalproject.servald.SubscriberId;

import android.util.Log;

public class Rhizome {

	/** TAG for debugging */
	public static final String TAG = "R3";

	public static boolean appendMessage(SubscriberId sid, byte[] bytes) {
		Log.w(TAG, "Rhizome.appendMessage(sid=" + sid + ", bytes=[..." + bytes.length + "...]) NOT IMPLEMENTED");
		return false;
	}

}
