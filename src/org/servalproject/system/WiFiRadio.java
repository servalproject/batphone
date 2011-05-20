package org.servalproject.system;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import android.os.Build;

/**
 *
 * @author swapna
 */
class UnknowndeviceException extends Exception {
	private static final long serialVersionUID = 4850408394461910703L;

	public UnknowndeviceException(String msg){
		super(msg);
	}
}

public class WiFiRadio {

	/**
	 * @param args
	 *            the command line arguments
	 */
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
	@SuppressWarnings("unused")
	private String interfacename = null;
	private String[] arChipset = new String[25];
	private String strMustExist = "must exist";
	private String strandroid = "android";
	public int intLinecount;
	private String fileName = "WiFiProp.log";
	public Logger logger;
	public String strLogMessage;
	private String strCapability = "capability";
	private String strAdhoc = "adhoc";
	private String strAp = "access point";
	private String strClient = "client";
	// private String strCapabilityModeSets = "capabilityModeSets";
	private String strAhAp = "AdhocAP";
	private String strAhCl = "AdhocClient";
	private String strApCl = "APClient";
	private String strAhApCl = "AdhocAPClient";
	private int sdkVersion;
	private static WiFiRadio wifiRadio;

	public static WiFiRadio getWiFiRadio() {
		if (wifiRadio == null)
			wifiRadio = new WiFiRadio();
		return wifiRadio;
	}

	@SuppressWarnings("unused")
	private void WiFiRadio() {
		try {
			@SuppressWarnings("unused")
			Boolean Idenitified = identifyChipset();
		} catch (UnknowndeviceException e) {
			System.out.println(e.getMessage());
		}

		// XXX Call identifyChipset() if we don't have a stored detection result
		// in
		// /data/data/org.servalproject/var/hardware.identity
		// If we do detect the hardware we should then write the result to that
		// file
		// so that we can skip the potentially time-consuming detection stage on
		// subsequent runs.
	}

	// Log the result with the date and time
	public void LogFile(String Message) {
		try {
			boolean append = true;
			FileHandler fh = new FileHandler("WiFiProp.log", append);
			fh.setFormatter(new SimpleFormatter());
			logger = Logger.getLogger("TestLog");
			logger.addHandler(fh);
			logger.info(Message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Write the results in the file
	public String filename() {
		return fileName;
	}

	public void writeFile(String Message) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(fileName,
					true));
			writer.write(Message);
			writer.newLine();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Check if the corresponding file exists
	private boolean fileExists(String filename) {
		boolean result = false;
		if ((new File(filename)).exists() == true) {
			result = true;
		}
		return result;
		// Check if the specified file exists during wifi chipset detection.
		// Record the result in a dictionary or similar structure so that if
		// we fail to detect a phone, we can create a bundle of information
		// that can be sent back to the Serval Project developers to help them
		// add support for the phone.
		// XXX Check that file exists
		// XXX Record result in dictionary or otherwise
	}

	/* Function to identify the chipset and log the result */
	public boolean identifyChipset() throws UnknowndeviceException {
		// XXX needs better error checking and throwing of appropriate
		// exceptions
		File f = new File("/data/data/org.servalproject/conf/wifichipsets");
		if (f.isDirectory() == false) {
			return false;
		}
		String files[] = f.list();
		for (int i = 0; i < files.length; i++) {
			if (files[i].endsWith(".detect")) {
				String chipset = files[i].substring(0,
						files[i].lastIndexOf('.') - 1);
				strLogMessage = "trying" + chipset;
				writeFile(strLogMessage);
				if (testForChipset(chipset)) {
					strLogMessage = "is " + chipset;
					writeFile(strLogMessage);
					if (wifichipset != null) {
						strLogMessage = "supports Multiple Chipsets";
						writeFile(strLogMessage);
						throw new UnknowndeviceException(strLogMessage);
						// XXX This phone identifies as multiple chipsets
						// This should be reported. Also which one shall we use.
					}
					wifichipset = chipset;
				} else {
					strLogMessage = "isnot " + chipset;
					writeFile(strLogMessage);
				}
			}
		}
		if (wifichipset == null) {
			strLogMessage = "Unknown device";
			writeFile(strLogMessage);
			throw new UnknowndeviceException(strLogMessage);
		}
		return (wifichipset != null);
	}

	/* Check if the chipset matches with the available chipsets */
	private boolean testForChipset(String chipset) {
		// Read
		// /data/data/org.servalproject/conf/wifichipsets/"+chipset+".detect"
		// and see if we can meet the criteria.
		// This method needs to interpret the lines of that file as test
		// instructions
		// that can do everything that the old big hairy if()else() chain did.
		// This largely consists of testing for the existence of files.

		// use fileExists() to test for the existence of files so that we can
		// generate
		// a report for this phone in case it is not supported.

		// XXX Stub}
		Boolean exist = false;
		try {
			FileInputStream fstream = new FileInputStream(chipset + ".detect");
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			// Read File Line By Line
			intLinecount = 0;
			while ((strLine = br.readLine()) != null) {
				System.out.println(strLine);
				arChipset = strLine.split(" ");
				if (arChipset[0].equals(strMustExist)) {
					if ((exist = fileExists(arChipset[1])) == true) {
						strLogMessage = "file " + arChipset[1] + " exists";
						if (arChipset[0].equals(strandroid)) {
							sdkVersion = Integer.parseInt(Build.VERSION.SDK);
							if (arChipset[1].equals(">=")) {
								if (sdkVersion >= Integer
										.parseInt(arChipset[2])) {
									strLogMessage = arChipset[0]
											+ "is appropriate for galaxy2x";
								}
							}
						}
					} else {
						strLogMessage = "file " + arChipset[1] + " not exists";
					}
					writeFile(strLogMessage);
				}
			}
			return exist;
		} catch (IOException e) {
			strLogMessage = "Exception Caught in testForChipset" + e;
			writeFile(strLogMessage);
		}
		return false;
	}

	/*
	 * Check if a particular mode is supported by the chipset and returns true
	 * or false
	 */
	public Boolean supportedWiFiModesretBoolean(String chipset,
			String strWiFiMode) {
		String strLine;
		Boolean result = false;
		try {
			FileInputStream fstream = new FileInputStream(chipset + ".detect");
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			// Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				System.out.println(strLine);
				arChipset = strLine.split(" ");
				if (arChipset[0].equals(strCapability)) {
					if (arChipset[1].equalsIgnoreCase(strWiFiMode)) {
						result = true;
					}
				}
			}

		} catch (IOException e) {
			strLogMessage = "Exception Caught in supportedWiFiModesretBoolean"
					+ e;
			writeFile(strLogMessage);
			result = false;
		}
		return result;
		// XXX Return a 4 bit value that indicates the set of modes
		// Read the "capability" statements from the relevant .detect file we
		// can use on this chipset
	}

	/*
	 * Check if a particular mode is supported by the chipset and returns a
	 * integer of 4 bit value
	 */
	public int supportedWiFiModesretInt(String chipset, String strWiFiMode) {
		String strLine;
		int intWiFiModeVal = 0000;
		try {
			FileInputStream fstream = new FileInputStream(chipset + ".detect");
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			// Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				System.out.println(strLine);
				arChipset = strLine.split(" ");
				if (arChipset[0].equals(strCapability)) {
					if (arChipset[1].equalsIgnoreCase(strWiFiMode)) {
						if (arChipset[1].equals(strAdhoc)) {
							intWiFiModeVal = 0001;
						} else if (arChipset[1].equals(strAp)) {
							intWiFiModeVal = 0010;
						} else if (arChipset[1].equals(strClient)) {
							intWiFiModeVal = 0011;
						} else if (arChipset[1].equals(strAhAp)) {
							intWiFiModeVal = 0110;
						} else if (arChipset[1].equals(strAhCl)) {
							intWiFiModeVal = 0111;
						} else if (arChipset[1].equals(strApCl)) {
							intWiFiModeVal = 1000;
						} else if (arChipset[1].equals(strAhApCl)) {
							intWiFiModeVal = 1001;
						}
					}
				}
			}

		} catch (IOException e) {
			strLogMessage = "Exception Caught in supportedWiFiModesretInt" + e;
			writeFile(strLogMessage);
		}
		return intWiFiModeVal;
		// XXX Return a 4 bit value that indicates the set of modes
		// Read the "capability" statements from the relevant .detect file we
		// can use on this chipset
	}

	/*
	 * Check what are the wifi modes supported here by comparing with the
	 * .detect files
	 */
	public Hashtable<String, String> supportedWiFiModeSets(String chipset) {
		Hashtable<String, String> WiFiModeSets = new Hashtable<String, String>();
		try {
			FileInputStream fstream = new FileInputStream(chipset + ".detect");
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			Boolean blWiFiModeSets = false;
			// Read File Line By Line
			intLinecount = 0;
			while ((strLine = br.readLine()) != null) {
				System.out.println(strLine);
				arChipset = strLine.split(" ");
				if (arChipset[0].equals(strCapability)) {
					blWiFiModeSets = true;
					if (arChipset[1].equals(strAdhoc)) {
						WiFiModeSets.put("0001", arChipset[1]);
					} else if (arChipset[1].equals(strAp)) {
						WiFiModeSets.put("0010", arChipset[1]);
					} else if (arChipset[1].equals(strClient)) {
						WiFiModeSets.put("0011", arChipset[1]);
					} else if (arChipset[1].equals(strAhAp)) {
						WiFiModeSets.put("0110", arChipset[1]);
					} else if (arChipset[1].equals(strAhCl)) {
						WiFiModeSets.put("0111", arChipset[1]);
					} else if (arChipset[1].equals(strApCl)) {
						WiFiModeSets.put("1000", arChipset[1]);
					} else if (arChipset[1].equals(strAhApCl)) {
						WiFiModeSets.put("1001", arChipset[1]);
					}
					strLogMessage = "supports " + arChipset[1];
					writeFile(strLogMessage);
				}
			}
			if (blWiFiModeSets.equals(false)) {
				strLogMessage = "supports No WiFi Modesets";
				writeFile(strLogMessage);
			}
		} catch (IOException e) {
			strLogMessage = "Exception Caught in supportedWiFiModeSets" + e;
			writeFile(strLogMessage);
		}
		// XXX Return a 4 bit value that indicates the set of modes
		// Read the "capability" statements from the relevant .detect file we
		// can use on this chipset
		return WiFiModeSets;
		// int[] m = new int[0];
		// return m;
	}

	public boolean setWiFiMode(int modeset) {
		// XXX Set WiFi Radio to specified mode (or combination) if supported
		// XXX Should cancel any schedule from setWiFiModeSet
		// XXX Will eventually call switchWiFiMode()
		return false;
	}

	public boolean setWiFiModeSet(int modeset) {
		// XXX Create a schedule of modes that covers all in the modeset, else
		// return false.
		// XXX Will eventually call switchWiFiMode()
		return false;
	}

	@SuppressWarnings("unused")
	private boolean switchWiFiMode(int modeset) {
		// XXX Private method to switch modes without disturbing modeset cycle
		// schedule
		return false;
	}
}
