/**
 *  This software is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Use this application at your own risk.
 *  
 *  Copyright (c) 2009 by Harald Mueller.
 */

package android.tether.system;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import android.tether.data.ClientData;

public class CoreTask {

	private static final String DATA_FILE_PATH = "/data/data/android.tether";
	
    public static boolean whitelistExists() {
    	File file = new File(DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    	if (file.exists() && file.canRead()) {
    		return true;
    	}
    	return false;
    }
    
    public static boolean removeWhitelist() {
    	File file = new File(DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    	if (file.exists()) {
	    	return file.delete();
    	}
    	return false;
    }
	
    public static void saveWhitelist(ArrayList<String> whitelist) throws Exception {
    	FileOutputStream fos = null;
    	File file = new File(DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    	try {
			fos = new FileOutputStream(file);
			for (String mac : whitelist) {
				fos.write((mac+"\n").getBytes());
			}
		} 
		finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					// nothing
				}
			}
		}
    }
    
    public static ArrayList<String> getWhitelist() throws Exception {
    	ArrayList<String> returnList = new ArrayList<String>();
    	File file = new File(DATA_FILE_PATH+"/conf/whitelist_mac.conf");
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        DataInputStream dis = null;
	    if (file.exists() && file.canRead()) {
	    	fis = new FileInputStream(file);
			bis = new BufferedInputStream(fis);
			dis = new DataInputStream(bis);
			while (dis.available() != 0) {
				returnList.add(dis.readLine().trim());
			}
			fis.close();
			bis.close();
			dis.close();
	    }
        return returnList;
    }
    
    public static Hashtable<String,ClientData> getLeases() throws Exception {
        Hashtable<String,ClientData> returnHash = new Hashtable<String,ClientData>();
    	File file = new File(DATA_FILE_PATH+"/var/dnsmasq.leases");
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        DataInputStream dis = null;
        if (file.exists() && file.canRead()) {
			fis = new FileInputStream(file);
			bis = new BufferedInputStream(fis);
			dis = new DataInputStream(bis);
			ClientData clientData = null;
			while (dis.available() != 0) {
				clientData = new ClientData();
				String[] data = dis.readLine().split(" ");
				Date connectTime = new Date(Long.parseLong(data[0] + "000"));
				String macAddress = data[1];
				String ipAddress = data[2];
				String clientName = data[3];
				clientData.setConnectTime(connectTime);
				clientData.setClientName(clientName);
				clientData.setIpAddress(ipAddress);
				clientData.setMacAddress(macAddress);
				clientData.setConnected(true);
				returnHash.put(macAddress, clientData);
			}
			fis.close();
			bis.close();
			dis.close();
		}
    	return returnHash;
    }
  
    public static void chmodBin(List<String> filenames) throws Exception {
        Process process = null;
		process = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
    	for (String tmpFilename:filenames) {
    		os.writeBytes("chmod 4755 "+DATA_FILE_PATH+"/bin/"+tmpFilename+"\n");
    	}
    	os.writeBytes("exit\n");
        os.flush();
        os.close();
        process.waitFor();
    }    
    
    public static boolean isNatEnabled() {
    	boolean natEnabled = false; 
        Process process = null;
		try {
			process = Runtime.getRuntime().exec("cat /proc/sys/net/ipv4/ip_forward");
	        BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
	        String line = null;
	        while ((line = in.readLine()) != null) {
	            if (line.trim().equals("1")) {
	            	natEnabled = true;
	            	break;
	            }
	        }
	        in.close();
	        process.waitFor();
		} catch (Exception e) {
			// Nothing
		}
		return natEnabled;
    }
    
    public static boolean isProcessRunning(String processName) throws Exception {
        boolean running = false;
    	Process process = null;
		process = Runtime.getRuntime().exec("ps");
        BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = null;
        while ((line = in.readLine()) != null) {
            if (line.contains(processName)) {
            	running = true;
            	break;
            }
        }
        in.close();
        process.waitFor();
		return running;
    }

    public static boolean runRootCommand(String command) {
        Process process = null;
		try {
			process = Runtime.getRuntime().exec("su");
	        DataOutputStream os = new DataOutputStream(process.getOutputStream());
	        os.writeBytes(command+"\n");
	        os.writeBytes("exit\n");
	        os.flush();
	        os.close();
	        process.waitFor();
		} catch (Exception e) {
			return false;
		}
		return true;
    }
    
    public static String getMD5String(String filename) throws Exception {
    	String mdsum;
    	MessageDigest digest = MessageDigest.getInstance("MD5");
    	File f = new File(filename);
    	InputStream is = new FileInputStream(f);				
    	byte[] buffer = new byte[8192];
    	int read = 0;
    	try {
    		while( (read = is.read(buffer)) > 0) {
    			digest.update(buffer, 0, read);
    		}		
    		byte[] md5sum = digest.digest();
    		BigInteger bigInt = new BigInteger(1, md5sum);
    		mdsum = bigInt.toString(16);
    	}
    	finally {
    		try {
    			is.close();
    		}
    		catch(Exception e) {
    			// nothing
    		}
    	}
    	return mdsum;
    }
}
