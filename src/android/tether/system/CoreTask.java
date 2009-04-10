/**
 *  This program is free software; you can redistribute it and/or modify it under 
 *  the terms of the GNU General Public License as published by the Free Software 
 *  Foundation; either version 3 of the License, or (at your option) any later 
 *  version.
 *  You should have received a copy of the GNU General Public License along with 
 *  this program; if not, see <http://www.gnu.org/licenses/>. 
 *  Use this application at your own risk.
 *
 *  Copyright (c) 2009 by Harald Mueller and Seth Lemons.
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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import android.tether.data.ClientData;
import android.util.Log;

public class CoreTask {

	public static final String MSG_TAG = "TETHER -> CoreTask";
	
	public String DATA_FILE_PATH;
	
	private static final String FILESET_VERSION = "7";
	private static final String defaultDNS1 = "208.67.220.220";
	private static final String defaultDNS2 = "208.67.222.222";
	
	public void setPath(String path){
		this.DATA_FILE_PATH = path;
	}

    public boolean whitelistExists() {
    	File file = new File(this.DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    	if (file.exists() && file.canRead()) {
    		return true;
    	}
    	return false;
    }
    
    public boolean removeWhitelist() {
    	File file = new File(this.DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    	if (file.exists()) {
	    	return file.delete();
    	}
    	return false;
    }

    public void touchWhitelist() throws IOException {
    	File file = new File(this.DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    	file.createNewFile();
    }
    
    public void saveWhitelist(ArrayList<String> whitelist) throws Exception {
    	FileOutputStream fos = null;
    	File file = new File(this.DATA_FILE_PATH+"/conf/whitelist_mac.conf");
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
    
    public ArrayList<String> getWhitelist() throws Exception {
    	ArrayList<String> returnList = new ArrayList<String>();
    	File file = new File(this.DATA_FILE_PATH+"/conf/whitelist_mac.conf");
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        DataInputStream dis = null;
        try {
		    if (file.exists() && file.canRead() && file.length() > 0) {
		    	fis = new FileInputStream(file);
				bis = new BufferedInputStream(fis);
				dis = new DataInputStream(bis);
				while (dis.available() != 0) {
					returnList.add(dis.readLine().trim());
				}
		    }
        }
        finally {
        	try {
				fis.close();
				bis.close();
				dis.close();
        	} catch (Exception ex) {
        		// nothinh
        	}
        }
        return returnList;
    }    
    
    public boolean wpaSupplicantExists() {
    	File file = new File(this.DATA_FILE_PATH+"/conf/wpa_supplicant.conf");
    	if (file.exists() && file.canRead()) {
    		return true;
    	}
    	return false;
    }
 
    public boolean removeWpaSupplicant() {
    	File file = new File(this.DATA_FILE_PATH+"/conf/wpa_supplicant.conf");
    	if (file.exists()) {
	    	return file.delete();
    	}
    	return false;
    }

    
    public Hashtable<String,ClientData> getLeases() throws Exception {
        Hashtable<String,ClientData> returnHash = new Hashtable<String,ClientData>();
    	File file = new File(this.DATA_FILE_PATH+"/var/dnsmasq.leases");
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        DataInputStream dis = null;
        try {
	        if (file.exists() && file.canRead() && file.length() > 0) {
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
			}
	    }
	    finally {
	    	try {
	    		if (fis != null)
	    			fis.close();
	    		if (bis != null)
	    			bis.close();
	    		if (dis != null)
	    			dis.close();
	    	} catch (Exception ex) {
	    		// nothinh
	    	}
	    }        
    	return returnHash;
    }
  
    public void chmodBin(List<String> filenames) throws Exception {
        Process process = null;
		process = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
    	for (String tmpFilename : filenames) {
    		os.writeBytes("chmod 4755 "+this.DATA_FILE_PATH+"/bin/"+tmpFilename+"\n");
    	}
    	os.writeBytes("exit\n");
        os.flush();
        os.close();
        process.waitFor();
    }   
    
    public void chownBin(List<String> filenames) throws Exception {
        Process process = null;
		process = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
    	for (String tmpFilename : filenames) {
    		os.writeBytes("chown 0.0 "+this.DATA_FILE_PATH+"/bin/"+tmpFilename+"\n");
    	}
    	os.writeBytes("exit\n");
        os.flush();
        os.close();
        process.waitFor();
    }

    public boolean isNatEnabled() {
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
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
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
    
    public boolean isProcessRunning(String processName) throws Exception {
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

    public boolean hasRootPermission() {
    	Process process = null;
    	DataOutputStream os = null;
    	boolean rooted = true;
		try {
			process = Runtime.getRuntime().exec("su");
	        os = new DataOutputStream(process.getOutputStream());
	        os.writeBytes("exit\n");
	        os.flush();	        
	        process.waitFor();
	        Log.d(MSG_TAG, "Exit-Value ==> "+process.exitValue());
	        if (process.exitValue() != 0) {
	        	rooted = false;
	        }
		} catch (Exception e) {
			Log.d(MSG_TAG, "Can't obtain root - Here is what I know: "+e.getMessage());
			rooted = false;
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
		return rooted;
    }
    
    public boolean runRootCommand(String command) {
        Process process = null;
        DataOutputStream os = null;
		try {
			process = Runtime.getRuntime().exec("su");
	        os = new DataOutputStream(process.getOutputStream());
	        Log.d(MSG_TAG, "Execute command: "+command);
	        os.writeBytes(command+"\n");
	        os.writeBytes("exit\n");
	        os.flush();
	        process.waitFor();
		} catch (Exception e) {
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
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

    public String getProp(String property) {
        String result = null;
    	Process process = null;
        BufferedReader br = null;
        try {
			process = Runtime.getRuntime().exec("getprop "+property);
        	br = new BufferedReader(new InputStreamReader(process.getInputStream()));
    		String s = br.readLine();
	    	while (s != null){
	    		result = s;
	    		s = br.readLine();
	    	}
	    	process.waitFor();
		} catch (Exception e) {
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
		}
		finally {
			try {
				if (br != null) {
					br.close();
				}
				process.destroy();
			} catch (Exception e) {
				// nothing
			}
		}
		return result;
    }
    
    public synchronized void updateDnsmasqFilepath() {
    	String dnsmasqConf = this.DATA_FILE_PATH+"/conf/dnsmasq.conf";
    	String newDnsmasq = new String();
    	String s = null;
    	BufferedReader br = null;
    	boolean writeconfig = false;
    	try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(dnsmasqConf))));
	    	while((s = br.readLine())!=null) {
	    		if (s.contains("dhcp-leasefile=") && !s.contains(CoreTask.this.DATA_FILE_PATH)){
	    			s = "dhcp-leasefile="+CoreTask.this.DATA_FILE_PATH+"/var/dnsmasq.leases";
	    			writeconfig = true;
	    		}
	    		else if (s.contains("pid-file=") && !s.contains(CoreTask.this.DATA_FILE_PATH)){
	    			s = "pid-file="+CoreTask.this.DATA_FILE_PATH+"/var/dnsmasq.pid";
	    			writeconfig = true;
	    		}
	    		newDnsmasq += s+"\n";
			}
		} catch (Exception e) {
			writeconfig = false;
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
		}
		finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					// nothing
				}
			}
		}
    	if (writeconfig == true) {

    		OutputStream out = null;
			try {
				out = new FileOutputStream(dnsmasqConf);
	        	out.write(newDnsmasq.getBytes());
			} catch (Exception e) {
				Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
			}
			finally {
	        	try {
	        		if (out != null)
	        			out.close();
				} catch (IOException e) {
					// nothing
				}
			}
    	}
    }
    
    public synchronized void updateDnsmasqConf() {
    	String dnsmasqConf = this.DATA_FILE_PATH+"/conf/dnsmasq.conf";
    	String newDnsmasq = new String();
    	// Getting dns-servers
    	String dns[] = new String[2];
    	dns[0] = getProp("net.dns1");
    	dns[1] = getProp("net.dns2");
    	if (dns[0] == null || dns[0].length() <= 0) {
    		dns[0] = defaultDNS1;
    	}
    	if (dns[1] == null || dns[1].length() <= 0) {
    		dns[1] = defaultDNS2;
    	}
    	String s = null;
    	BufferedReader br = null;
    	boolean writeconfig = false;
    	try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(dnsmasqConf))));

	    	int servercount = 0;
	    	while((s = br.readLine())!=null) {
	    		if (s.contains("server")) { 
	    			if (s.contains(dns[servercount]) == false){
	    				s = "server="+dns[servercount];
	    				writeconfig = true;
	    			}
	    			servercount++;
	    		}
	    		newDnsmasq += s+"\n";
			}
		} catch (Exception e) {
			writeconfig = false;
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
		}
		finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					// nothing
				}
			}
		}
    	if (writeconfig == true) {
			Log.d(MSG_TAG, "Writing new DNS-Servers: "+dns[0]+","+dns[1]);
    		OutputStream out = null;
			try {
				out = new FileOutputStream(dnsmasqConf);
	        	out.write(newDnsmasq.getBytes());
			} catch (Exception e) {
				Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
			}
			finally {
	        	try {
	        		if (out != null)
	        			out.close();
				} catch (IOException e) {
					// nothing
				}
			}
    	}
    	else {
			Log.d(MSG_TAG, "No need to update DNS-Servers: "+dns[0]+","+dns[1]);
    	}
    }
    
    public boolean filesetOutdated(){
    	boolean outdated = true;
    	InputStream is = null;
    	BufferedReader br = null;
    	File inFile = new File(this.DATA_FILE_PATH+"/bin/tether");
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
    		Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
    		outdated = true;
    	}
    	finally {
   			try {
   				if (is != null)
   					is.close();
   				if (br != null)
   					br.close();
   			} catch (Exception e) {
				// nothing
			}
    	}
    	return outdated;
    }
    

    public Hashtable<String,String> getWpaSupplicantConf() {
    	File inFile = new File(this.DATA_FILE_PATH+"/conf/wpa_supplicant.conf");
    	if (inFile.exists() == false) {
    		return null;
    	}
    	Hashtable<String,String> tiWlanConf = new Hashtable<String,String>();
    	InputStream is = null;
    	BufferedReader br = null;
    	try{
        	is = new FileInputStream(inFile);
        	br = new BufferedReader(new InputStreamReader(is));
    		String s = br.readLine();
	    	while (s != null){
	    		if (s.contains("=")) {
		    		String[] pair = s.split("=");
		    		if (pair[0] != null && pair[1] != null && pair[0].length() > 0 && pair[1].length() > 0) {
		    			tiWlanConf.put(pair[0].trim(), pair[1].trim());
		    		}
	    		}
	    		s = br.readLine();
	    	}
    	}
    	catch (Exception e){
    		Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
    	}
    	finally {
	    	try {
	    		if (is != null)
	    			is.close();
		    	if (br != null)
		    		br.close();
			} catch (Exception e) {
				// nothing
			}
    	}
    	return tiWlanConf;
    }   
    
    public synchronized boolean writeWpaSupplicantConf(Hashtable<String,String> values) {
    	String filename = this.DATA_FILE_PATH+"/conf/wpa_supplicant.conf";
    	String fileString = "";
    	String s;
    	BufferedReader br = null;
    	OutputStream out = null;
    	
        try {
        	File inFile = new File(filename);
        	br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
        	while((s = br.readLine())!=null) {
        		if (s.contains("=")) {
        			String key = s.split("=")[0];
        			if (values.containsKey(key)) {
        				s = key+"="+values.get(key);
        			}
        		}
        		s+="\n";
        		fileString += s;
        	}
        	File outFile = new File(filename);
        	out = new FileOutputStream(outFile);
        	out.write(fileString.getBytes());
        	out.close();
        	br.close();
		} catch (IOException e) {
			return false;
		}
		finally {
			try {
				if (br != null)
					br.close();
				if (out != null)
					out.close();
			} catch (Exception ex) {
				//nothing
			}
		}
		return true;   	
    }
    
    public Hashtable<String,String> getTiWlanConf() {
    	Hashtable<String,String> tiWlanConf = new Hashtable<String,String>();
    	File inFile = new File(this.DATA_FILE_PATH+"/conf/tiwlan.ini");
    	InputStream is = null;
    	BufferedReader br = null;
    	try{
        	is = new FileInputStream(inFile);
        	br = new BufferedReader(new InputStreamReader(is));
    		String s = br.readLine();
	    	while (s != null){
	    		String[] pair = s.split("=");
	    		if (pair[0] != null && pair[1] != null && pair[0].length() > 0 && pair[1].length() > 0) {
	    			tiWlanConf.put(pair[0].trim(), pair[1].trim());
	    		}
	    		s = br.readLine();
	    	}
    	}
    	catch (Exception e){
    		Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
    	}
    	finally {
	    	try {
	    		if (is != null)
	    			is.close();
		    	if (br != null)
		    		br.close();
			} catch (Exception e) {
				// nothing
			}
    	}
    	return tiWlanConf;
    }
 
    public synchronized boolean writeTiWlanConf(String name, String value) {
    	String filename = this.DATA_FILE_PATH+"/conf/tiwlan.ini";
    	String fileString = "";
    	String s;
    	BufferedReader br = null;
    	OutputStream out = null;
        try {
        	File inFile = new File(filename);
        	br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
        	while((s = br.readLine())!=null) {
        		if (s.contains(name)){
	    			s = name+" = "+value;
	    		}
        		s+="\n";
        		fileString += s;
			}
        	File outFile = new File(filename);
        	out = new FileOutputStream(outFile);
        	out.write(fileString.getBytes());
        	out.close();
        	br.close();
		} catch (IOException e) {
			return false;
		}
		finally {
			try {
				if (br != null)
					br.close();
				if (out != null)
					out.close();
			} catch (Exception ex) {
				//nothing
			}
		}
		return true;   	
    }
    
    public synchronized boolean writeTiWlanConf(Hashtable<String,String> values) {
    	String filename = this.DATA_FILE_PATH+"/conf/tiwlan.ini";
    	ArrayList<String> valueNames = Collections.list(values.keys());

    	String fileString = "";
    	String s;
    	BufferedReader br = null;
    	OutputStream out = null;
    	
        try {
        	File inFile = new File(filename);
        	br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
        	while((s = br.readLine())!=null) {
        		for (String name : valueNames) {
	        		if (s.contains(name)){
		    			s = name+" = "+values.get(name);
		    			break;
		    		}
        		}
        		s+="\n";
        		fileString += s;
        	}
        	File outFile = new File(filename);
        	out = new FileOutputStream(outFile);
        	out.write(fileString.getBytes());
        	out.close();
        	br.close();
		} catch (IOException e) {
			return false;
		}
		finally {
			try {
				if (br != null)
					br.close();
				if (out != null)
					out.close();
			} catch (Exception ex) {
				//nothing
			}
		}
		return true;   	
    }
    
    public long getModifiedDate(String filename) {
    	File file = new File(filename);
    	if (file.exists() == false) {
    		return -1;
    	}
    	return file.lastModified();
    }
}
