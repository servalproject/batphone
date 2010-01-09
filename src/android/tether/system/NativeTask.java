package android.tether.system;

import android.util.Log;

public class NativeTask {
    
	public static final String MSG_TAG = "TETHER -> NativeTask";
	
	static {
        try {
            Log.i(MSG_TAG, "Trying to load libnativetask.so");
            System.loadLibrary("nativetask");
        }
        catch (UnsatisfiedLinkError ule) {
            Log.e(MSG_TAG, "Could not load libnativetask.so");
            ule.printStackTrace();
        }
    }
    public static native String getProp(String name);
    public static native int runCommand(String command);
}
