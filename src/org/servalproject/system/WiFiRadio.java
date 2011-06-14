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
import android.util.Log;

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
	/*
	 * public final int WIFI_OFF = 0; public final int WIFI_ADHOC = 1; public
	 * final int WIFI_CLIENT = 2; public final int WIFI_MANAGED = 2; public
	 * final int WIFI_ADHOC_MANAGED = 3; public final int WIFI_AP = 4; public
	 * final int WIFI_MONITOR = 8;
	 */

	private String wifichipset = null;
	private String[] arChipset = new String[25];
	private String strMustExist = "exists";
	private String strMustNotExist = "missing";
	private String strandroid = "androidversion";
	public int intLinecount;
	private String fileName;
	public Logger logger;
	public String strLogMessage;
	private String strCapability = "capability";
	private String strAdhoc = "Adhoc";
	private String strAp = "AP";
	private String strClient = "Client";
	private String strAhAp = "AdhocAP";
	private String strAhCl = "AdhocClient";
	private String strApCl = "APClient";
	private String strAhApCl = "AdhocAPClient";
	private String strPath;
	@SuppressWarnings("unused")
	private String stridentified_chipset;
	private int sdkVersion;
	private static WiFiRadio wifiRadio;
	public String DATA_FILE_PATH;

	public static WiFiRadio getWiFiRadio(String datapath) {
		if (wifiRadio == null)
			wifiRadio = new WiFiRadio(datapath);
		return wifiRadio;
	}

	private WiFiRadio(String datapath) {
		try {
			this.fileName = datapath + "/var/wifidetect.log";
			this.strPath = datapath + "/conf/wifichipsets/";

			System.out.println("test for constructor");
			Boolean Idenitified = identifyChipset();
			Log.i("WiFiConstructor", "identified" + Idenitified);
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
			FileHandler fh = new FileHandler(fileName, append);
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
			Log.i("FileExists", "filename" + filename);
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

		File f = new File(strPath);
		if (f.isDirectory() == false) {
			Log.i("WiFiConstructor", "isnot directory" + f.isDirectory());
			return false;
		}
		String files[] = f.list();
		for (int i = 0; i < files.length; i++) {
			if (files[i].endsWith(".detect")) {
				String chipset = files[i].substring(0,
						files[i].lastIndexOf('.'));
				strLogMessage = "trying " + chipset;
				writeFile(strLogMessage);
				if (testForChipset(files[i])) {
					strLogMessage = "is " + chipset;
					stridentified_chipset = chipset;
					Log.i("Identified", "chipsetval" + chipset);
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
		Boolean reject = false;
		int matches = 0;
		Boolean exist = false;
		try {
			FileInputStream fstream = new FileInputStream(strPath + chipset);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			// Read File Line By Line
			intLinecount = 0;
			while ((strLine = br.readLine()) != null) {
				writeFile("# " + strLine);
				arChipset = strLine.split(" ");
				if (arChipset[0].equals(strMustExist)
						|| arChipset[0].equals(strMustNotExist)) {
					exist = fileExists(arChipset[1]);
					Boolean wanted;
					if (arChipset[0].equals(strMustExist))
						wanted = true;
					else
						wanted = false;
					writeFile((exist ? "exists" : "missing") + " "
							+ arChipset[1]);
					if (exist != wanted) { // wrong
						reject = true;
					} else
						matches++;
				} else if (arChipset[0].equals(strandroid)) {
					sdkVersion = Integer.parseInt(Build.VERSION.SDK);
					writeFile(strandroid + " = " + Build.VERSION.SDK);
					Boolean satisfies = false;
					float requestedVersion = Float.parseFloat(arChipset[2]);
					if (arChipset[1].equals(">="))
						satisfies = (sdkVersion >= requestedVersion) ? true
								: false;
					if (arChipset[1].equals(">"))
						satisfies = (sdkVersion > requestedVersion) ? true
								: false;
					if (arChipset[1].equals("<="))
						satisfies = (sdkVersion <= requestedVersion) ? true
								: false;
					if (arChipset[1].equals("<"))
						satisfies = (sdkVersion < requestedVersion) ? true
								: false;
					if (arChipset[1].equals("="))
						satisfies = (sdkVersion == requestedVersion) ? true
								: false;
					if (arChipset[1].equals("!="))
						satisfies = (sdkVersion != requestedVersion) ? true
								: false;
					if (satisfies)
						matches++;
					else
						reject = true;
				}

			}
			// Return our final verdict
			if (matches > 0 && (reject == false)) {
				supportedWiFiModeSets(strPath + chipset);
				return true;
			} else {
				return false;
			}
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
			FileInputStream fstream = new FileInputStream(strPath + chipset
					+ ".detect");
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
			FileInputStream fstream = new FileInputStream(strPath + chipset
					+ ".detect");
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
			FileInputStream fstream = new FileInputStream(chipset);
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
					writeFile(arChipset[0] + "List");
					if (arChipset[1].equals(strAdhoc)) {
						WiFiModeSets.put(arChipset[1], "0001");
						writeFile(arChipset[1] + " "
								+ (WiFiModeSets.get(arChipset[1])));
					} else if (arChipset[1].equals(strAp)) {
						WiFiModeSets.put(arChipset[1], "0010");
						writeFile(arChipset[1] + " "
								+ (WiFiModeSets.get(arChipset[1])));
					} else if (arChipset[1].equals(strClient)) {
						WiFiModeSets.put(arChipset[1], "0011");
						writeFile(arChipset[1] + " "
								+ (WiFiModeSets.get(arChipset[1])));
					} else if (arChipset[1].equals(strAhAp)) {
						WiFiModeSets.put(arChipset[1], "0110");
						writeFile(arChipset[1] + " "
								+ (WiFiModeSets.get(arChipset[1])));
					} else if (arChipset[1].equals(strAhCl)) {
						WiFiModeSets.put(arChipset[1], "0111");
						writeFile(arChipset[1] + " "
								+ (WiFiModeSets.get(arChipset[1])));
					} else if (arChipset[1].equals(strApCl)) {
						WiFiModeSets.put(arChipset[1], "1000");
						writeFile(arChipset[1] + " "
								+ (WiFiModeSets.get(arChipset[1])));
					} else if (arChipset[1].equals(strAhApCl)) {
						WiFiModeSets.put(arChipset[1], "1001");
						writeFile(arChipset[1] + " "
								+ (WiFiModeSets.get(arChipset[1])));
					}
					// strLogMessage = "supports " + arChipset[1];
					// writeFile(strLogMessage);
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
