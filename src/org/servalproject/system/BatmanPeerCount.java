package org.servalproject.system;

import android.util.Log;
import java.io.*;

public class BatmanPeerCount {
    
	public static final String MSG_TAG = "ADHOC -> batmanPeerCount";

	//static {
    //    try {
    //        Log.i(MSG_TAG, "Trying to load libbatmanclient.so");
    //        //System.load("/data/org.servalproject/library/libbatmanclient.so");
    //        System.loadLibrary("batmanclient");
    //    }
    //    catch (UnsatisfiedLinkError ule) {
    //        Log.e(MSG_TAG, "Could not load libbatmanclient.so");
    //    }
    //}
    // public static native long BatmanPeerCount(String name);
    
    public static long BatmanPeerCount()
    {    	
    	File f = new File("/data/data/org.servalproject/var/batmand.peers");
    	InputStream is;
    	try {
    	is = new FileInputStream(f);
    	} catch (FileNotFoundException e) {
    		return -1;
    	}
    	byte[] bytes= new byte[8];
    	try {
    		int bytesread=is.read(bytes,0,8);
    		if (bytesread==8) {
    			long peers= (bytes[4]<<24)+(bytes[5]<<16)+(bytes[6]<<8)+(bytes[7]<<0);
    			return peers;
    		}
    	} catch (IOException e) {
    		return -2;
    	}
    	
    	return -3;
    }
}
