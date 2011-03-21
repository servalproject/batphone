package org.servalproject.system;

import java.io.*;

public class BatmanService {
    
	public static final String MSG_TAG = "ADHOC -> batmanPeerCount";

    public static long getPeerCount()
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
