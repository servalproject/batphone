package org.servalproject.system;

import android.util.Log;

public class NativeTask {
    
	public static final String MSG_TAG = "ADHOC -> NativeTask";

	static {
        try {
            Log.i(MSG_TAG, "Trying to load libnativetask.so");
            System.loadLibrary("nativetask");
        }
        catch (UnsatisfiedLinkError ule) {
            Log.e(MSG_TAG, "Could not load libnativetask.so");
        }
    }
    public static native String getProp(String name);
    public static native int runCommand(String command);
}
