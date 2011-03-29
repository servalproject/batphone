package org.servalproject.system;

import java.io.File;

public class WiFiRadio {

	public final int WIFI_OFF = 0;
	public final int WIFI_ADHOC = 1;
	public final int WIFI_CLIENT = 2;
	public final int WIFI_MANAGED = 2;
	public final int WIFI_ADHOC_MANAGED = 3;
	public final int WIFI_AP = 4;
	// XXX Complete the rest
	public final int WIFI_MONITOR = 8;
	// XXX Complete the rest
	
	private String wifichipset = null;
	private String interfacename = null;
	
	public void WiFiRadio() {
		// XXX Call identifyChipset() if we don't have a stored detection result in
		// /data/data/org.servalproject/var/hardware.identity
		// If we do detect the hardware we should then write the result to that file
		// so that we can skip the potentially time-consuming detection stage on
		// subsequent runs.
	}
	
	private boolean fileExists(String filename) {
		// Check if the specified file exists during wifi chipset detection.
		// Record the result in a dictionary or similar structure so that if
		// we fail to detect a phone, we can create a bundle of information 
		// that can be sent back to the Serval Project developers to help them
		// add support for the phone.
		
		boolean result = false;
		
		// XXX Check that file exists
		
		// XXX Record result in dictionary or otherwise
		
		return result;
	}
	
	public boolean identifyChipset() {
		// XXX needs better error checking and throwing of appropriate exceptions
		File f = new File("/data/data/org.servalproject/conf/wifichipsets");
		if (f.isDirectory() == false) return false;
		
		String files[] = f.list();
		for (int i=0;i<files.length;i++) {
			if (files[i].endsWith(".detect")) {
				String chipset = files[i].substring(0,files[i].lastIndexOf('.')-1);
				if (testForChipset(chipset)) {
					if (wifichipset != null) {
						// XXX This phone identifies as multiple chipsets
						// This should be reported.  Also which one shall we use.
					}
					wifichipset = chipset;
				}
			}
		}
		return (wifichipset!=null);
	}
	
	private boolean testForChipset(String chipset) {
		// Read /data/data/org.servalproject/conf/wifichipsets/"+chipset+".detect"
		// and see if we can meet the criteria.
		// This method needs to interpret the lines of that file as test instructions
		// that can do everything that the old big hairy if()else() chain did.
		// This largely consists of testing for the existence of files.
		
		// use fileExists() to test for the existence of files so that we can generate
		// a report for this phone in case it is not supported.
		
		// XXX Stub 
		return false;
	}
	
	public int supportedWiFiModes() {
		// XXX Return a 4 bit value that indicates the set of modes we can use on this chipset
		return 0;
	}
	
	public int[] supportedWiFiModeSets() {
		// XXX Return a list of 4 bit values of each combination of modes we can do with this chipset.
		int[] m = new int[0];
		return m;
	}

	public boolean setWiFiMode(int modeset) {
		// XXX Set WiFi Radio to specified mode (or combination) if supported
		// XXX Should cancel any schedule from setWiFiModeSet
		// XXX Will eventually call switchWiFiMode()
		return false;
	}

	public boolean setWiFiModeSet(int modeset) {
		// XXX Create a schedule of modes that covers all in the modeset, else return false.
		// XXX Will eventually call switchWiFiMode()
		return false;
	}
	
	private boolean switchWiFiMode(int modeset) {
		// XXX Private method to switch modes without disturbing modeset cycle schedule
		return false;
	}
}
