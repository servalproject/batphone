package org.servalproject.system;

import android.util.Log;

public class BatmanPeerCount {
    
	public static final String MSG_TAG = "ADHOC -> batmanPeerCount";

	static {
        try {
            Log.i(MSG_TAG, "Trying to load libbatmanclient.so");
            //System.load("/data/org.servalproject/library/libbatmanclient.so");
            System.loadLibrary("batmanclient");
        }
        catch (UnsatisfiedLinkError ule) {
            Log.e(MSG_TAG, "Could not load libbatmanclient.so");
        }
    }
    public static native long BatmanPeerCount(String name);
}
