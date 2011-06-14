package org.servalproject.system;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

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

	public enum WifiMode {
		Ap, Client, Adhoc;
	}

	@SuppressWarnings("unused")
	private String wifichipset = null;
	private Set<WifiMode> supportedModes;
	private WifiMode currentMode;

	private static final String strMustExist = "exists";
	private static final String strMustNotExist = "missing";
	private static final String strandroid = "androidversion";
	private static final String strCapability = "capability";

	private String logFile;
	private String detectPath;
	private static WiFiRadio wifiRadio;

	public static WiFiRadio getWiFiRadio(String datapath) {
		if (wifiRadio == null)
			wifiRadio = new WiFiRadio(datapath);
		return wifiRadio;
	}

	private WiFiRadio(String datapath) {
		try {
			this.logFile = datapath + "/var/wifidetect.log";
			this.detectPath = datapath + "/conf/wifichipsets/";

			identifyChipset();
		} catch (UnknowndeviceException e) {
			this.writeFile(e.toString());
			Log.e("BatPhone", e.toString(), e);
		}

		// XXX Call identifyChipset() if we don't have a stored detection result
		// in
		// /data/data/org.servalproject/var/hardware.identity
		// If we do detect the hardware we should then write the result to that
		// file
		// so that we can skip the potentially time-consuming detection stage on
		// subsequent runs.
	}

	public void writeFile(String Message) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(logFile,
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
	public void identifyChipset() throws UnknowndeviceException {

		File detectScripts = new File(detectPath);
		if (detectScripts.isDirectory() == false) {
			throw new UnknowndeviceException(detectPath + " is not a directory");
		}
		int count = 0;

		for (File script : detectScripts.listFiles()) {
			if (!script.getName().endsWith(".detect"))
				continue;

			if (testForChipset(script))
				count++;
		}

		if (count != 1)
			throw new UnknowndeviceException("Unable to determine device type");
	}

	/* Check if the chipset matches with the available chipsets */
	private boolean testForChipset(File detectScript) {
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
		String filename = detectScript.getName();
		String chipset = filename.substring(0, filename.lastIndexOf('.'));
		writeFile("trying " + chipset);

		boolean reject = false;
		int matches = 0;
		Set<WifiMode> modes = null;

		try {
			FileInputStream fstream = new FileInputStream(detectScript);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			String strLine;
			// Read File Line By Line
			while ((strLine = in.readLine()) != null) {
				writeFile("# " + strLine);
				String arChipset[] = strLine.split(" ");

				if (arChipset[0].equals(strMustExist)
						|| arChipset[0].equals(strMustNotExist)) {
					boolean exist = fileExists(arChipset[1]);
					boolean wanted = arChipset[0].equals(strMustExist);
					writeFile((exist ? "exists" : "missing") + " "
							+ arChipset[1]);
					if (exist != wanted) { // wrong
						reject = true;
					} else
						matches++;
				} else if (arChipset[0].equals(strandroid)) {
					int sdkVersion = Build.VERSION.SDK_INT;
					writeFile(strandroid + " = " + Build.VERSION.SDK_INT);
					Boolean satisfies = false;
					float requestedVersion = Float.parseFloat(arChipset[2]);

					if (arChipset[1].equals(">="))
						satisfies = sdkVersion >= requestedVersion;
					if (arChipset[1].equals(">"))
						satisfies = sdkVersion > requestedVersion;
					if (arChipset[1].equals("<="))
						satisfies = sdkVersion <= requestedVersion;
					if (arChipset[1].equals("<"))
						satisfies = sdkVersion < requestedVersion;
					if (arChipset[1].equals("="))
						satisfies = sdkVersion == requestedVersion;
					if (arChipset[1].equals("!="))
						satisfies = sdkVersion != requestedVersion;

					if (satisfies)
						matches++;
					else
						reject = true;

				} else if (arChipset[0].equals(strCapability)) {
					modes = EnumSet.noneOf(WifiMode.class);

					for (String mode : arChipset[1].split(",")) {
						WifiMode m = WifiMode.valueOf(mode);
						if (m != null)
							modes.add(m);
					}
				}

			}

			in.close();

			// Return our final verdict
			if (matches > 0 && !reject) {
				Log.i("BatPhone", "identified chipset " + chipset);
				writeFile("is " + chipset);
				wifichipset = chipset;
				supportedModes = modes;
				return true;
			}

		} catch (IOException e) {
			Log.i("BatPhone", e.toString(), e);
			writeFile("Exception Caught in testForChipset" + e);
		}

		writeFile("isnot " + chipset);
		return false;
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
