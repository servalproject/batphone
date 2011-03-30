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

package org.servalproject.system;

import java.io.BufferedReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;

import org.servalproject.data.ClientData;

import android.util.Log;

public class CoreTask {

	public static final String MSG_TAG = "ADHOC -> CoreTask";
	
	public String DATA_FILE_PATH;
	
	private static final String FILESET_VERSION = "67";
	private static final String defaultDNS1 = "208.67.220.220";
	
	private Hashtable<String,String> runningProcesses = new Hashtable<String,String>();
	
	public void setPath(String path){
		this.DATA_FILE_PATH = path;
	}
	
	public class Whitelist {
		/*
		 * Maintains the whitelist of allowed MAC addresses.
		 */
		public ArrayList<String> whitelist;
		
		public Whitelist() {
			this.whitelist = new ArrayList<String>();
		}
		
		public boolean exists() {
			File file = new File(DATA_FILE_PATH+"/conf/whitelist_mac.conf");
			return (file.exists() && file.canRead());
		}
		
		public boolean remove() {
			File file = new File(DATA_FILE_PATH+"/conf/whitelist_mac.conf");
			if (file.exists())
				return file.delete();
			return false;
		}
		
		public void touch() throws IOException {
			File file = new File(DATA_FILE_PATH+"/conf/whitelist_mac.conf");
			file.createNewFile();
		}
		
	    public void save() throws Exception {
	    	FileOutputStream fos = null;
	    	File file = new File(DATA_FILE_PATH+"/conf/whitelist_mac.conf");
	    	try {
				fos = new FileOutputStream(file);
				for (String mac : this.whitelist) {
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
	    public ArrayList<String> get() {
	    	return readLinesFromFile(DATA_FILE_PATH+"/conf/whitelist_mac.conf");
	    }
	}
	
	/*
	 * A class to handle the wpa supplicant config file.
	 */
	public class WpaSupplicant {
		
		public boolean exists() {
			File file = new File(DATA_FILE_PATH+"/conf/wpa_supplicant.conf");
			return (file.exists() && file.canRead());
		}
		
	    public boolean remove() {
	    	File file = new File(DATA_FILE_PATH+"/conf/wpa_supplicant.conf");
	    	if (file.exists()) {
		    	return file.delete();
	    	}
	    	return false;
	    }

	    public Hashtable<String,String> get() {
	    	File inFile = new File(DATA_FILE_PATH+"/conf/wpa_supplicant.conf");
	    	if (inFile.exists() == false) {
	    		return null;
	    	}
	    	Hashtable<String,String> SuppConf = new Hashtable<String,String>();
	    	ArrayList<String> lines = readLinesFromFile(DATA_FILE_PATH+"/conf/wpa_supplicant.conf");

	    	for (String line : lines) {
	    		if (line.contains("=")) {
		    		String[] pair = line.split("=");
		    		if (pair[0] != null && pair[1] != null && pair[0].length() > 0 && pair[1].length() > 0) {
		    			SuppConf.put(pair[0].trim(), pair[1].trim());
		    		}
	    		}
	    	}
	    	return SuppConf;
	    }   
	    
	    public synchronized boolean write(Hashtable<String,String> values) {
	    	String filename = DATA_FILE_PATH+"/conf/wpa_supplicant.conf";
	    	String fileString = "";
	    	
	    	ArrayList<String>inputLines = readLinesFromFile(filename);
	    	for (String line : inputLines) {
	    		if (line.contains("=")) {
	    			String key = line.split("=")[0];
	    			if (values.containsKey(key)) {
	    				line = key+"="+values.get(key);
	    			}
	    		}
	    		line+="\n";
	    		fileString += line;
	    	}
	    	if (writeLinesToFile(filename, fileString)) {
	    		CoreTask.this.chmod(filename, "0644");
	    		return true;
	    	}
	    	return false;
	    }
	}
	
	public class TiWlanConf {
	    /*
	     * Handle operations on the TiWlan.conf file.
	     */
	    public Hashtable<String,String> get() {
	    	Hashtable<String,String> tiWlanConf = new Hashtable<String,String>();
	    	ArrayList<String> lines = readLinesFromFile(DATA_FILE_PATH+"/conf/tiwlan.ini");

	    	for (String line : lines) {
	    		String[] pair = line.split("=");
	    		if (pair[0] != null && pair[1] != null && pair[0].length() > 0 && pair[1].length() > 0) {
	    			tiWlanConf.put(pair[0].trim(), pair[1].trim());
	    		}
	    	}
	    	return tiWlanConf;
	    }
	 
	    public synchronized boolean write(String name, String value) {
	    	Hashtable<String, String> table = new Hashtable<String, String>();
	    	table.put(name, value);
	    	return write(table);
	    }
	    
	    public synchronized boolean write(Hashtable<String,String> values) {
	    	String filename = DATA_FILE_PATH+"/conf/tiwlan.ini";
	    	ArrayList<String> valueNames = Collections.list(values.keys());

	    	String fileString = "";
	    	
	    	ArrayList<String> inputLines = readLinesFromFile(filename);
	    	for (String line : inputLines) {
	    		for (String name : valueNames) {
	        		if (line.contains(name)){
		    			line = name+" = "+values.get(name);
		    			break;
		    		}
	    		}
	    		line+="\n";
	    		fileString += line;
	    	}
	    	return writeLinesToFile(filename, fileString); 	
	    }
	}
	
	public class AdhocConfig extends HashMap<String, String> {

		private static final long serialVersionUID = 1L;
		
		public HashMap<String, String> read() {
			String filename = DATA_FILE_PATH + "/conf/adhoc.conf";
			this.clear();
			for (String line : readLinesFromFile(filename)) {
				if (line.startsWith("#"))
					continue;
				if (!line.contains("="))
					continue;
				String[] data = line.split("=");
				if (data.length > 1) {
					this.put(data[0], data[1]);
				} 
				else {
					this.put(data[0], "");
				}
			}
			return this;
		}
		
		public boolean write() {
			String lines = new String();
			for (String key : this.keySet()) {
				lines += key + "=" + this.get(key) + "\n";
			}
			return writeLinesToFile(DATA_FILE_PATH + "/conf/adhoc.conf", lines);
		}
	}
    
	public class DnsmasqConfig {
		
		private static final long serialVersionUID = 1L;
		private String lanconfig;
		
		/**
		 * @param lanconfig - Uses the "number of bits in the routing prefix" to specify the subnet. Example: 192.168.1.0/24
		 */
		public void set(String lanconfig) {
			this.lanconfig = lanconfig;
		}
		
		public boolean write() {
			String[] lanparts = lanconfig.split("\\.");
			String iprange = lanparts[0]+"."+lanparts[1]+"."+lanparts[2]+".100,"+lanparts[0]+"."+lanparts[1]+"."+lanparts[2]+".105,12h";
	    	StringBuffer buffer = new StringBuffer();;
	    	ArrayList<String> inputLines = readLinesFromFile(DATA_FILE_PATH+"/conf/dnsmasq.conf");   
	    	for (String line : inputLines) {
	    		if (line.contains("dhcp-range")) {
	    			line = "dhcp-range="+iprange;
	    		}    		
	    		buffer.append(line+"\n");
	    	}
	    	if (writeLinesToFile(DATA_FILE_PATH+"/conf/dnsmasq.conf", buffer.toString()) == false) {
	    		Log.e(MSG_TAG, "Unable to update conf/dnsmasq.conf with new lan-configuration.");
	    		return false;
	    	}    	
	    	return true;
		}
	}
	
	public class BluetoothConfig {
		
		private static final long serialVersionUID = 1L;
		private String lanconfig;
		
		/**
		 * @param lanconfig - Uses the "number of bits in the routing prefix" to specify the subnet. Example: 192.168.1.0/24
		 */
		public void set(String lanconfig) {
			this.lanconfig = lanconfig;
		}		
		
		public boolean write() {
			String[] lanparts = lanconfig.split("\\.");
			String gateway = lanparts[0]+"."+lanparts[1]+"."+lanparts[2]+"."+lanparts[3];
			StringBuffer buffer = new StringBuffer();;
	    	ArrayList<String> inputLines = readLinesFromFile(DATA_FILE_PATH+"/bin/blue-up.sh");   
	    	for (String line : inputLines) {
	    		if (line.contains("ifconfig bnep0") && line.endsWith("netmask 255.255.255.0 up >> $adhoclog 2>> $adhoclog")) {
	    			line = reassembleLine(line, " ", "bnep0", gateway);
	    		}    		
	    		buffer.append(line+"\n");
	    	}
	    	if (writeLinesToFile(DATA_FILE_PATH+"/bin/blue-up.sh", buffer.toString()) == false) {
	    		Log.e(MSG_TAG, "Unable to update bin/adhoc with new lan-configuration.");
	    		return false;
	    	}
	    	return true;
		}
	}
	
    public Hashtable<String,ClientData> getLeases() throws Exception {
        Hashtable<String,ClientData> returnHash = new Hashtable<String,ClientData>();
        
        ClientData clientData;
        
        ArrayList<String> lines = readLinesFromFile(this.DATA_FILE_PATH+"/var/dnsmasq.leases");
        
        for (String line : lines) {
			clientData = new ClientData();
			String[] data = line.split(" ");
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
    	return returnHash;
    }
    
    public boolean chmod(String file, String mode) {
    	try {
			if (runCommand("chmod "+ mode + " " + file) == 0) {
				return true;
			}
		} catch (Exception e) {}
    	return false;
    }
    
    public ArrayList<String> readLinesFromFile(String filename) {
    	String line = null;
    	BufferedReader br = null;
    	InputStream ins = null;
    	ArrayList<String> lines = new ArrayList<String>();
    	File file = new File(filename);
    	if (file.canRead() == false)
    		return lines;
    	try {
    		ins = new FileInputStream(file);
    		br = new BufferedReader(new InputStreamReader(ins), 8192);
    		while((line = br.readLine())!=null) {
    			lines.add(line.trim());
    		}
    	} catch (Exception e) {
    		Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
    	}
    	finally {
    		try {
    			ins.close();
    			br.close();
    		} catch (Exception e) {
    			// Nothing.
    		}
    	}
    	return lines;
    }
    
    public boolean writeLinesToFile(String filename, String lines) {
		OutputStream out = null;
		boolean returnStatus = false;
		Log.d(MSG_TAG, "Writing " + lines.length() + " bytes to file: " + filename);
		try {
			out = new FileOutputStream(filename);
        	out.write(lines.getBytes());
        	out.flush();
		} catch (Exception e) {
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
		}
		finally {
        	try {
        		if (out != null)
        			out.close();
        		returnStatus = true;
			} catch (IOException e) {
				returnStatus = false;
			}
		}
		return returnStatus;
    }
    
    public boolean isNatEnabled() {
    	ArrayList<String> lines = readLinesFromFile("/proc/sys/net/ipv4/ip_forward");
    	return lines.contains("1");
    }
    
    public String getKernelVersion() {
        ArrayList<String> lines = readLinesFromFile("/proc/version");
        String version = lines.get(0).split(" ")[2];
        Log.d(MSG_TAG, "Kernel version: " + version);
        return version;
    }
    
	/*
	 * This method checks if netfilter/iptables is supported by kernel
	 */
    public boolean isNetfilterSupported() {
    	if ((new File("/proc/config.gz")).exists() == false) {
	    	if ((new File("/proc/net/netfilter")).exists() == false)
	    		return false;
	    	if ((new File("/proc/net/ip_tables_targets")).exists() == false) 
	    		return false;
    	}
    	else {
            if (!Configuration.hasKernelFeature("CONFIG_NETFILTER=") || 
                !Configuration.hasKernelFeature("CONFIG_IP_NF_IPTABLES="))
            return false;
    	}
    	return true;
    }
    
    public boolean isAccessControlSupported() {
    	if ((new File("/proc/config.gz")).exists() == false) {
	    	if ((new File("/proc/net/ip_tables_matches")).exists() == false)
	    		return false;    		
    	}
    	else {
    		if (!Configuration.hasKernelFeature("CONFIG_NETFILTER_XT_MATCH_MAC="))
    		return false;
    	}
    	return true;
    }
    
    public boolean isProcessRunning(String processName) throws Exception {
    	boolean processIsRunning = false;
    	Hashtable<String,String> tmpRunningProcesses = new Hashtable<String,String>();
    	File procDir = new File("/proc");
    	FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                try {
                    Integer.parseInt(name);
                } catch (NumberFormatException ex) {
                    return false;
                }
                return true;
            }
        };
    	File[] processes = procDir.listFiles(filter);
    	for (File process : processes) {
    		String cmdLine = "";
    		// Checking if this is a already known process
    		if (this.runningProcesses.containsKey(process.getAbsoluteFile().toString())) {
    			cmdLine = this.runningProcesses.get(process.getAbsoluteFile().toString());
    		}
    		else {
    			ArrayList<String> cmdlineContent = this.readLinesFromFile(process.getAbsoluteFile()+"/cmdline");
    			if (cmdlineContent != null && cmdlineContent.size() > 0) {
    				cmdLine = cmdlineContent.get(0);
    			}
    		}
    		// Adding to tmp-Hashtable
    		tmpRunningProcesses.put(process.getAbsoluteFile().toString(), cmdLine);
    		
    		// Checking if processName matches
    		if (cmdLine.contains(processName)) {
    			processIsRunning = true;
    		}
    	}
    	// Overwriting runningProcesses
    	this.runningProcesses = tmpRunningProcesses;
    	return processIsRunning;
    }
    
    private static int rooted=0;
    
    public boolean hasRootPermission() {
		try {
	    	if (rooted==0){
				File su = new File("/system/bin/su");
				if (su.exists()) rooted = 1;
	    	}
			if (rooted==0){
				File su = new File("/system/xbin/su");
				if (su.exists()) rooted = 1;
			}
			if (rooted==0) rooted = 2;
		} catch (Exception e) {
			Log.d(MSG_TAG, "Can't obtain root - Here is what I know: "+e.getMessage());
			rooted = 2;
		}
		return rooted==1;
    }
    //TODO: better exception type?
    public void runRootCommand(String command) throws IOException{
    	if (!hasRootPermission()) throw new IOException("su not found");
		while(true){
			int returncode = runCommand("su -c \""+command+"\"");
	    	if (returncode == 0) return;
	    	if (returncode!=65280){
	    		throw new IOException("Command error, \""+command+"\" returned code: "+returncode);
	    	}
		}
    }
    
    public int runCommand(String command) {
		Log.d(MSG_TAG, "Command ==> "+command);
		int returncode = NativeTask.runCommand(command);
    	if (returncode != 0)
    		Log.d(MSG_TAG, "Command error, return code: " + returncode);
		return returncode;
    }
    
    public String getProp(String property) {
    	return NativeTask.getProp(property);
    }
    
    public long countBatmanPeers() {

    	/* Make socket */
    	return BatmanService.getPeerCount(); 
    	}
    
    public long[] getDataTraffic(String device) {
    	// Returns traffic usage for all interfaces starting with 'device'.
    	long [] dataCount = new long[] {0, 0, 0};
    	if (device == "")
    		return dataCount;
    	for (String line : readLinesFromFile("/proc/net/dev")) {
    		if (line.startsWith(device) == false)
    			continue;
    		line = line.replace(':', ' ');
    		String[] values = line.split(" +");
    		dataCount[0] += Long.parseLong(values[1]);
    		dataCount[1] += Long.parseLong(values[9]);
    	}
    	dataCount[2] = countBatmanPeers();
    	//Log.d(MSG_TAG, "Data rx: " + dataCount[0] + ", tx: " + dataCount[1]);
    	return dataCount;
    }

    
    public synchronized void updateDnsmasqFilepath() {
    	String dnsmasqConf = this.DATA_FILE_PATH+"/conf/dnsmasq.conf";
    	String newDnsmasq = new String();
    	boolean writeconfig = false;
    	
    	ArrayList<String> lines = readLinesFromFile(dnsmasqConf);
    	
    	for (String line : lines) {
    		if (line.contains("dhcp-leasefile=") && !line.contains(CoreTask.this.DATA_FILE_PATH)){
    			line = "dhcp-leasefile="+CoreTask.this.DATA_FILE_PATH+"/var/dnsmasq.leases";
    			writeconfig = true;
    		}
    		else if (line.contains("pid-file=") && !line.contains(CoreTask.this.DATA_FILE_PATH)){
    			line = "pid-file="+CoreTask.this.DATA_FILE_PATH+"/var/dnsmasq.pid";
    			writeconfig = true;
    		}
    		newDnsmasq += line+"\n";
    	}

    	if (writeconfig == true)
    		writeLinesToFile(dnsmasqConf, newDnsmasq);
    }
    
    public synchronized String[] getCurrentDns() {
    	// Getting dns-servers
    	String dns[] = new String[2];
    	dns[0] = getProp("net.dns1");
    	dns[1] = getProp("net.dns2");
    	if (dns[0] == null || dns[0].length() <= 0 || dns[0].equals("undefined")) {
    		dns[0] = defaultDNS1;
    	}
    	if (dns[1] == null || dns[1].length() <= 0 || dns[1].equals("undefined")) {
    		dns[1] = "";
    	}
    	return dns;
    }
    
    public synchronized String[] updateResolvConf() {
    	String resolvConf = this.DATA_FILE_PATH+"/conf/resolv.conf";
    	// Getting dns-servers
    	String dns[] = this.getCurrentDns();
    	String linesToWrite = new String();
    	linesToWrite = "nameserver "+dns[0]+"\n";
    	if (dns[1].length() > 0) {
    		linesToWrite += "nameserver "+dns[1]+"\n";
    	}
    	this.writeLinesToFile(resolvConf, linesToWrite);
    	return dns;
    }    
    
    public boolean filesetOutdated(){
    	boolean outdated = true;
    	
    	File inFile = new File(this.DATA_FILE_PATH+"/conf/adhoc.edify");
    	if (inFile.exists() == false) {
    		return false;
    	}
    	ArrayList<String> lines = readLinesFromFile(this.DATA_FILE_PATH+"/conf/adhoc.edify");

    	int linecount = 0;
    	for (String line : lines) {
    		if (line.contains("@Version")){
    			String instVersion = line.split("=")[1];
    			if (instVersion != null && FILESET_VERSION.equals(instVersion.trim()) == true) {
    				outdated = false;
    			}
    			break;
    		}
    		if (linecount++ > 2)
    			break;
    	}
    	return outdated;
    }

    
    public long getModifiedDate(String filename) {
    	File file = new File(filename);
    	if (file.exists() == false) {
    		return -1;
    	}
    	return file.lastModified();
    }

    private String reassembleLine(String source, String splitPattern, String prefix, String target) {
    	String returnString = new String();
    	String[] sourceparts = source.split(splitPattern);
    	boolean prefixmatch = false;
    	boolean prefixfound = false;
    	for (String part : sourceparts) {
    		if (prefixmatch) {
    			returnString += target+" ";
    			prefixmatch = false;
    		}
    		else {
    			returnString += part+" ";
    		}
    		if (prefixfound == false && part.trim().equals(prefix)) {
    			prefixmatch = true;
    			prefixfound = true;
    		}

    	}
    	return returnString;
    }
    
}
