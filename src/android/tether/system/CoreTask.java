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
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import android.tether.data.ClientData;
import android.util.Log;

public class CoreTask {

	private static final String FILESET_VERSION = "1";
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
    	for (String tmpFilename : filenames) {
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
        BufferedReader in = null;
		try {
			process = Runtime.getRuntime().exec("cat /proc/sys/net/ipv4/ip_forward");
	        in = new BufferedReader(new InputStreamReader(process.getInputStream()));
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
			Log.d("*** DEBUG ***", "Unexpected error - Here is what I know: "+e.getMessage());
		}
		finally {
			try {
				if (in != null) {
					in.close();
				}
				process.destroy();
			} catch (Exception e) {
				// nothing
			}
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

    public static boolean hasRootPermission() {
    	Process process = null;
    	DataOutputStream os = null;
		try {
			process = Runtime.getRuntime().exec("su");
	        os = new DataOutputStream(process.getOutputStream());
	        BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
	        String line = null;
	        while ((line = in.readLine()) != null) {
	            Log.d("*** DEBUG ***", line);
	        }
	        os.writeBytes("exit\n");
	        os.flush();
	        in.close();
	        process.waitFor();
		} catch (Exception e) {
			Log.d("*** DEBUG ***", "Can't obtain root - Here is what I know: "+e.getMessage());
			return false;
		}
		finally {
			if (os != null) {
				try {
					os.close();
					process.destroy();
				} catch (Exception e) {
					// nothing
				}
			}
		}
		return true;
    }
    
    public static boolean runRootCommand(String command) {
        Process process = null;
        DataOutputStream os = null;
		try {
			process = Runtime.getRuntime().exec("su");
	        os = new DataOutputStream(process.getOutputStream());
	        os.writeBytes(command+"\n");
	        os.writeBytes("exit\n");
	        os.flush();
	        process.waitFor();
		} catch (Exception e) {
			Log.d("*** DEBUG ***", "Unexpected error - Here is what I know: "+e.getMessage());
			return false;
		}
		finally {
			try {
				if (os != null) {
					os.close();
				}
				process.destroy();
			} catch (Exception e) {
				// nothing
			}
		}
		return true;
    }
    
    public static boolean filesetOutdated(){
    	boolean outdated = true;
    	InputStream is = null;
    	BufferedReader br = null;
    	File inFile = new File(DATA_FILE_PATH+"/bin/tether");
    	try{
        	is = new FileInputStream(inFile);
        	br = new BufferedReader(new InputStreamReader(is));
    		String s = br.readLine();
    		int linecount = 0;
	    	while (s!=null){
	    		if (s.contains("@Version")){
	    			String instVersion = s.split("=")[1];
	    			if (instVersion != null && FILESET_VERSION.equals(instVersion.trim()) == true) {
	    				outdated = false;
	    			}
	    			break;
	    		}
	    		linecount++;
	    		if (linecount > 1) {
	    			break;
	    		}
	    		s = br.readLine();
	    	}
    	}
    	catch (Exception e){
    		Log.d("*** DEBUG ***", "Unexpected error - Here is what I know: "+e.getMessage());
    		outdated = true;
    	}
    	finally {
   			try {
				is.close();
	   			br.close();
   			} catch (IOException e) {
				// nothing
			}
    	}
    	return outdated;
    }    
}
