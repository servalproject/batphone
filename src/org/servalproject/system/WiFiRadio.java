package org.servalproject.system;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.WifiApControl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
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

	private String wifichipset = null;
	private Set<WifiMode> supportedModes;
	private WifiMode currentMode;

	private int wifiState = WifiManager.WIFI_STATE_UNKNOWN;
	private int wifiApState = WifiApControl.WIFI_AP_STATE_FAILED;

	// WifiManager
	private WifiManager wifiManager;
	private WifiApControl wifiApManager;
	private ServalBatPhoneApplication app;

	private static final String strMustExist = "exists";
	private static final String strMustNotExist = "missing";
	private static final String strandroid = "androidversion";
	private static final String strCapability = "capability";

	private String logFile;
	private String detectPath;

	private static WiFiRadio wifiRadio;

	public static WiFiRadio getWiFiRadio(ServalBatPhoneApplication context) {
		if (wifiRadio == null)
			wifiRadio = new WiFiRadio(context);
		return wifiRadio;
	}

	// translate wifi state int values to WifiMode enum.
	private void checkWifiMode() {
		switch (wifiState) {
		case WifiManager.WIFI_STATE_ENABLED:
		case WifiManager.WIFI_STATE_DISABLING:
		case WifiManager.WIFI_STATE_ENABLING:
			currentMode = WifiMode.Client;
			Log.v("BatPhone", "Client mode detected");
			return;
		}

		if (wifiApManager != null) {
			switch (wifiApState) {
			case WifiApControl.WIFI_AP_STATE_ENABLED:
			case WifiApControl.WIFI_AP_STATE_ENABLING:
			case WifiApControl.WIFI_AP_STATE_DISABLING:
				currentMode = WifiMode.Ap;
				Log.v("BatPhone", "Access point mode detected");
				return;
			}
		}

		if (currentMode != WifiMode.Adhoc) {
			currentMode = null;
			Log.v("BatPhone", "Wifi turned off");
		}
	}

	private WiFiRadio(ServalBatPhoneApplication context) {
		this.app = context;
		this.logFile = context.coretask.DATA_FILE_PATH + "/var/wifidetect.log";
		this.detectPath = context.coretask.DATA_FILE_PATH
				+ "/conf/wifichipsets/";

		// init wifiManager
		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		wifiApManager = WifiApControl.getApControl(wifiManager);

		wifiState = wifiManager.getWifiState();
		if (wifiApManager != null)
			wifiApState = wifiApManager.getWifiApState();

		checkWifiMode();

		try {
			String hardwareFile = app.coretask.DATA_FILE_PATH
					+ "var/hardware.identity";
			DataInputStream in = new DataInputStream(new FileInputStream(
					hardwareFile));
			String chipset = in.readLine();
			in.close();
			if (chipset != null) {
				// read the detect script again to make sure we have the
				// right
				// supported modes etc.
				testForChipset(new File(detectPath + chipset + ".detect"));
			}
		} catch (Exception e) {
			// ignore all errors (Yeah I know it's nasty)
		}

		if (wifichipset != null) {
			String adhocStatus = app.coretask.getProp("adhoc.status");

			if (currentMode == null && app.coretask.isNatEnabled()
					&& adhocStatus.equals("running")) {
				// looks like the application force closed and
				// restarted, check that everything we require is still
				// running.
				currentMode = WifiMode.Adhoc;
			}

			if (currentMode != WifiMode.Adhoc
					&& app.settings.getBoolean("meshRunning", false)) {
				try {
					switchWiFiMode(WifiMode.Adhoc);
				} catch (IOException e) {
					Log.e("BatPhone", e.toString(), e);
				}
			}
		}

		// receive wifi state broadcasts.
		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION);

		app.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();

				if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {

					wifiState = intent.getIntExtra(
							WifiManager.EXTRA_WIFI_STATE,
							WifiManager.WIFI_STATE_UNKNOWN);
					Log.v("BatPhone", "new client state: " + wifiState);
					checkWifiMode();

				} else if (action
						.equals(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION)) {

					wifiApState = intent.getIntExtra(
							WifiApControl.EXTRA_WIFI_AP_STATE,
							WifiApControl.WIFI_AP_STATE_FAILED);
					Log.v("BatPhone", "new AP state: " + wifiApState);
					checkWifiMode();

				}
			}
		}, filter);

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

		// TODO, setup "unknown" chipset with only android client / ap support

		if (count != 1)
			throw new UnknowndeviceException("Unable to determine device type");

		// write out the detected chipset
		try {
			String hardwareFile = app.coretask.DATA_FILE_PATH
					+ "/var/hardware.identity";
			FileOutputStream out = new FileOutputStream(hardwareFile);
			out.write(this.wifichipset.getBytes());
			out.close();
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
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
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(logFile,
					true), 256);

			String filename = detectScript.getName();
			String chipset = filename.substring(0, filename.lastIndexOf('.'));
			writer.write("trying " + chipset + "\n");

			boolean reject = false;
			int matches = 0;
			Set<WifiMode> modes = EnumSet.noneOf(WifiMode.class);

			try {
				FileInputStream fstream = new FileInputStream(detectScript);
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
									modes.add(m);
							} catch (IllegalArgumentException e) {
							}
						}
					}

				}

				in.close();

				if (matches < 1)
					reject = true;

				// Return our final verdict
				if (!reject) {
					Log.i("BatPhone", "identified chipset " + chipset);
					writer.write("is " + chipset + "\n");

					if (!modes.contains(WifiMode.Ap) && wifiApManager != null)
						modes.add(WifiMode.Ap);
					if (!modes.contains(WifiMode.Client))
						modes.add(WifiMode.Client);

					wifichipset = chipset;
					supportedModes = modes;
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

	public void setWiFiMode(WifiMode newMode) throws IOException {
		// XXX Set WiFi Radio to specified mode (or combination) if supported
		// XXX Should cancel any schedule from setWiFiModeSet

		if (newMode != null && !this.supportedModes.contains(newMode))
			throw new IOException("Wifi mode " + newMode + " is not supported");

		switchWiFiMode(newMode);
	}

	public boolean setWiFiModeSet(int modeset) {
		// XXX Create a schedule of modes that covers all in the modeset, else
		// return false.
		// XXX Will eventually call switchWiFiMode()
		return false;
	}

	public WifiMode getCurrentMode() {
		return currentMode;
	}

	private void waitForApState(int newState) throws IOException {
		while (true) {
			int state = wifiApManager.getWifiApState();
			if (state == newState)
				return;
			if (state == WifiManager.WIFI_STATE_UNKNOWN)
				throw new IOException(
						"Failed to control access point mode");
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}

	private void startAp() throws IOException {
		WifiConfiguration netConfig = new WifiConfiguration();
		netConfig.SSID = "BatPhone Installation";
		netConfig.allowedAuthAlgorithms
				.set(WifiConfiguration.AuthAlgorithm.OPEN);
		this.wifiApManager.setWifiApEnabled(netConfig, true);
		waitForApState(WifiManager.WIFI_STATE_ENABLED);
	}

	private void stopAp() throws IOException {
		this.wifiApManager.setWifiApEnabled(null, false);
		waitForApState(WifiManager.WIFI_STATE_DISABLED);
	}

	private void waitForClientState(int newState) throws IOException {
		while (true) {
			int state = this.wifiManager.getWifiState();
			if (state == newState)
				return;
			if (state == WifiManager.WIFI_STATE_UNKNOWN)
				throw new IOException(
						"Failed to control wifi client mode");

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}

	private void startClient() throws IOException {
		this.wifiManager.setWifiEnabled(true);
		waitForClientState(WifiManager.WIFI_STATE_ENABLED);
	}

	private void stopClient() throws IOException {
		this.wifiManager.setWifiEnabled(false);
		waitForClientState(WifiManager.WIFI_STATE_DISABLED);
	}

	private void startAdhoc() throws IOException {
		// Get WiFi in adhoc mode and batmand running
		if (app.coretask.runRootCommand(app.coretask.DATA_FILE_PATH
				+ "/bin/adhoc start 1") != 0)
			throw new IOException("Failed to start adhoc mode");
	}

	private void stopAdhoc() throws IOException {
		if (app.coretask.runRootCommand(app.coretask.DATA_FILE_PATH
				+ "/bin/adhoc stop 1") != 0)
			throw new IOException("Failed to stop adhoc mode");
	}

	private void switchWiFiMode(WifiMode newMode) throws IOException {
		// XXX Private method to switch modes without disturbing modeset cycle
		// schedule

		if (newMode == currentMode)
			return;

		if (currentMode != null) {
			Log.v("BatPhone", "Stopping " + currentMode);
			switch (currentMode) {
			case Ap:
				stopAp();
				break;
			case Adhoc:
				stopAdhoc();
				break;
			case Client:
				stopClient();
				break;
			}
		}

		if (newMode != null) {
			Log.v("BatPhone", "Starting " + newMode);
			switch (newMode) {
			case Ap:
				startAp();
				break;
			case Adhoc:
				startAdhoc();
				break;
			case Client:
				startClient();
				break;
			}
		}
		currentMode = newMode;
	}
}
