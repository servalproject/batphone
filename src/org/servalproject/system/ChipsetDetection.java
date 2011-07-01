package org.servalproject.system;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.WifiApControl;

import android.os.Build;
import android.util.Log;

public class ChipsetDetection {
	private static final String strMustExist = "exists";
	private static final String strMustNotExist = "missing";
	private static final String strandroid = "androidversion";
	private static final String strCapability = "capability";
	private static final String strAh_on_tag = "#Insert_Adhoc_on";
	private static final String strAh_off_tag = "#Insert_Adhoc_off";

	private String logFile;
	private String detectPath;
	private String edifyPath;
	private String edifysrcPath;

	private ServalBatPhoneApplication app;
	private Chipset wifichipset;
	private Chipset unknownChipset = new Chipset();

	private ChipsetDetection() {
		this.app = ServalBatPhoneApplication.context;
		this.logFile = app.coretask.DATA_FILE_PATH + "/var/wifidetect.log";
		this.detectPath = app.coretask.DATA_FILE_PATH + "/conf/wifichipsets/";
		this.edifyPath = app.coretask.DATA_FILE_PATH + "/conf/adhoc.edify";
		this.edifysrcPath = app.coretask.DATA_FILE_PATH
				+ "/conf/adhoc.edify.src";

		if (!app.firstRun) {
			try {
				String hardwareFile = app.coretask.DATA_FILE_PATH
						+ "/var/hardware.identity";
				DataInputStream in = new DataInputStream(new FileInputStream(
						hardwareFile));
				String chipset = in.readLine();
				in.close();
				if (chipset != null) {
					// read the detect script again to make sure we have the
					// right supported modes etc.
					testForChipset(new Chipset(new File(detectPath + chipset
							+ ".detect")));
				}
			} catch (Exception e) {
				Log.v("BatPhone", edifyPath.toString(), e);
			}
		}
	}

	private static ChipsetDetection detection;

	public static ChipsetDetection getDetection() {
		if (detection == null)
			detection = new ChipsetDetection();
		return detection;
	}

	private HashMap<String, Boolean> existsTests = new HashMap<String, Boolean>();

	// Check if the corresponding file exists
	private boolean fileExists(String filename) {
		// Check if the specified file exists during wifi chipset detection.
		// Record the result in a dictionary or similar structure so that if
		// we fail to detect a phone, we can create a bundle of information
		// that can be sent back to the Serval Project developers to help them
		// add support for the phone.
		Boolean result = existsTests.get(filename);
		if (result == null) {
			result = (new File(filename)).exists();
			existsTests.put(filename, result);
		}
		return result;
	}

	public List<Chipset> getChipsets() {
		List<Chipset> chipsets = new ArrayList<Chipset>();

		File detectScripts = new File(detectPath);
		if (!detectScripts.isDirectory())
			return null;

		for (File script : detectScripts.listFiles()) {
			if (!script.getName().endsWith(".detect"))
				continue;
			chipsets.add(new Chipset(script));
		}
		return chipsets;
	}

	/* Function to identify the chipset and log the result */
	public String identifyChipset() throws UnknowndeviceException {

		int count = 0;

		for (Chipset chipset : getChipsets()) {
			if (testForChipset(chipset))
				count++;
		}

		if (count != 1) {
			setChipset(unknownChipset);
		} else {
			// write out the detected chipset
			try {
				String hardwareFile = app.coretask.DATA_FILE_PATH
						+ "/var/hardware.identity";
				FileOutputStream out = new FileOutputStream(hardwareFile);
				out.write(wifichipset.chipset.getBytes());
				out.close();
			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}
		}
		return wifichipset.chipset;
	}

	public Chipset getWifiChipset() {
		return wifichipset;
	}

	public String getChipset() {
		return wifichipset.chipset;
	}

	/* Check if the chipset matches with the available chipsets */
	public boolean testForChipset(Chipset chipset) {
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
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(logFile,
					true), 256);

			writer.write("trying " + chipset + "\n");

			boolean reject = false;
			int matches = 0;
			chipset.supportedModes.clear();
			chipset.detected = false;

			try {
				FileInputStream fstream = new FileInputStream(
						chipset.detectScript);
				// Get the object of DataInputStream
				DataInputStream in = new DataInputStream(fstream);
				String strLine;
				// Read File Line By Line
				while ((strLine = in.readLine()) != null) {
					writer.write("# " + strLine + "\n");
					String arChipset[] = strLine.split(" ");

					if (arChipset[0].equals(strMustExist)
							|| arChipset[0].equals(strMustNotExist)) {
						boolean exist = fileExists(arChipset[1]);
						boolean wanted = arChipset[0].equals(strMustExist);
						writer.write((exist ? "exists" : "missing") + " "
								+ arChipset[1] + "\n");
						if (exist != wanted) { // wrong
							reject = true;
						} else
							matches++;
					} else if (arChipset[0].equals(strandroid)) {
						int sdkVersion = Build.VERSION.SDK_INT;
						writer.write(strandroid + " = " + Build.VERSION.SDK_INT
								+ "\n");
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
						for (String mode : arChipset[1].split(",")) {
							try {
								WifiMode m = WifiMode.valueOf(mode);
								if (m != null)
									chipset.supportedModes.add(m);
							} catch (IllegalArgumentException e) {
							}
						}
						if (arChipset.length >= 3)
							chipset.adhocOn = arChipset[2];
						if (arChipset.length >= 4)
							chipset.adhocOff = arChipset[3];
					}

				}

				in.close();

				if (matches < 1)
					reject = true;

				// Return our final verdict
				if (!reject) {
					Log.i("BatPhone", "identified chipset " + chipset);
					writer.write("is " + chipset + "\n");
					chipset.detected = true;
					setChipset(chipset);
				}

			} catch (IOException e) {
				Log.i("BatPhone", e.toString(), e);
				writer.write("Exception Caught in testForChipset" + e + "\n");
				reject = true;
			}

			writer.write("isnot " + chipset + "\n");

			writer.close();
			return !reject;
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
			return false;
		}
	}

	private void appendFile(FileOutputStream out, String path)
			throws IOException {
		DataInputStream input = new DataInputStream(new FileInputStream(path));
		String strLineinput;
		while ((strLineinput = input.readLine()) != null) {
			out.write((strLineinput + "\n").getBytes());
		}
		input.close();
	}

	// set chipset configuration
	public void setChipset(Chipset chipset) {

		// add support for modes via SDK if available
		if (!chipset.supportedModes.contains(WifiMode.Ap)
				&& WifiApControl.isApSupported())
			chipset.supportedModes.add(WifiMode.Ap);
		if (!chipset.supportedModes.contains(WifiMode.Client))
			chipset.supportedModes.add(WifiMode.Client);
		if (!chipset.supportedModes.contains(WifiMode.Off))
			chipset.supportedModes.add(WifiMode.Off);

		// make sure we have root permission for adhoc support
		if (chipset.supportedModes.contains(WifiMode.Adhoc)) {
			if (!app.coretask.hasRootPermission()) {
				chipset.supportedModes.remove(WifiMode.Adhoc);
				Log.v("BatPhone",
						"Unable to support adhoc mode without root permission");
			}
		}

		wifichipset = chipset;

		try {
			FileOutputStream out = new FileOutputStream(edifyPath);
			FileInputStream fstream = new FileInputStream(edifysrcPath);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			String strLine;
			// Read File Line By Line
			while ((strLine = in.readLine()) != null) {
				if (strLine.startsWith(strAh_on_tag)) {
					if (chipset.adhocOn != null)
						appendFile(out, detectPath + chipset.adhocOn);
				} else if (strLine.startsWith(strAh_off_tag)) {
					if (chipset.adhocOff != null)
						appendFile(out, detectPath + chipset.adhocOff);
				} else
					out.write((strLine + "\n").getBytes());
			}
			in.close();
			out.close();
		} catch (IOException exc) {
			Log.e("Exception caught at set_Adhoc_mode", exc.toString(), exc);
		}
	}

	public boolean isModeSupported(WifiMode mode) {
		if (mode == null)
			return true;
		if (wifichipset == null)
			return false;
		return wifichipset.supportedModes.contains(mode);
	}

}
