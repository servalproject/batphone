package android.tether.system;

import android.util.Log;

public class NativeTask {
    
	public static final String MSG_TAG = "TETHER -> NativeTask";
	
	static {
        try {
            Log.i(MSG_TAG, "Trying to load libNativeTask.so");
            System.load("/data/data/android.tether/library/libNativeTask.so");
        }
        catch (UnsatisfiedLinkError ule) {
            Log.e(MSG_TAG, "Could not load libNativeTask.so");
        }
    }
    public static native String getProp(String name);
    public static native int runCommand(String command);
}
