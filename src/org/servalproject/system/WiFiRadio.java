package org.servalproject.system;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.WifiApControl;
import org.servalproject.batman.Batman;
import org.servalproject.batman.Olsr;
import org.servalproject.batman.PeerFinder;
import org.servalproject.batman.PeerParser;
import org.servalproject.batman.PeerRecord;
import org.servalproject.batman.Routing;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

public class WiFiRadio {

	// TODO, investigate better timing values.
	static int defaultSleep = 30;

	public enum WifiMode {
		Adhoc(defaultSleep), Client(defaultSleep), Ap(defaultSleep), Sleep(
				defaultSleep);

		int sleepTime;

		WifiMode(int sleepTime) {
			this.sleepTime = sleepTime;
		}
	}

	private String wifichipset = null;
	private Set<WifiMode> supportedModes;
	private WifiMode currentMode, lastActiveMode;
	private PendingIntent alarmIntent;
	private boolean changing = false;
	private boolean autoCycling = false;
	private boolean modeLocked = false;

	private int wifiState = WifiManager.WIFI_STATE_UNKNOWN;
	private int wifiApState = WifiApControl.WIFI_AP_STATE_FAILED;
	private SupplicantState supplicantState = null;
	private Routing routingImp;

	// WifiManager
	private WifiManager wifiManager;
	private WifiApControl wifiApManager;
	private AlarmManager alarmManager;
	private ServalBatPhoneApplication app;

	private static final String strMustExist = "exists";
	private static final String strMustNotExist = "missing";
	private static final String strandroid = "androidversion";
	private static final String strCapability = "capability";
	private static final String strAh_on_tag = "#Insert_Adhoc_on";
	private static final String strAh_off_tag = "#Insert_Adhoc_off";
	private static final String ALARM = "org.servalproject.WIFI_ALARM";
	public static final String WIFI_MODE_ACTION = "org.servalproject.WIFI_MODE";
	public static final String EXTRA_NEW_MODE = "new_mode";

	private String logFile;
	private String detectPath;
	private String edifyPath;
	private String edifysrcPath;

	private static WiFiRadio wifiRadio;

	public static WiFiRadio getWiFiRadio(ServalBatPhoneApplication context) {
		if (wifiRadio == null)
			wifiRadio = new WiFiRadio(context);
		return wifiRadio;
	}

	private void modeChanged(WifiMode newMode, boolean force) {
		if (!force && changing)
			return;

		if (currentMode == newMode)
			return;

		if (newMode == WifiMode.Client || newMode == WifiMode.Ap) {
			if (peerFinder == null) {
				peerFinder = new PeerFinder(app);
				peerFinder.start();
			}
		} else if (peerFinder != null) {
			peerFinder.interrupt();
			peerFinder = null;
		}

		Intent modeChanged = new Intent(WIFI_MODE_ACTION);
		modeChanged.putExtra("new_mode",
				(newMode == null ? null : newMode.toString()));
		app.sendStickyBroadcast(modeChanged);
		currentMode = newMode;
		if (newMode != null)
			lastActiveMode = currentMode;
		changing = false;
	}

	// translate wifi state int values to WifiMode enum.
	private void checkWifiMode() {

		if (wifiManager.isWifiEnabled()) {
			modeChanged(WifiMode.Client, false);
			return;
		}
		if (wifiApManager != null && wifiApManager.isWifiApEnabled()) {
			modeChanged(WifiMode.Ap, false);
			return;
		}

		if (currentMode != WifiMode.Adhoc) {
			modeChanged(null, false);
		}
	}

	private WiFiRadio(ServalBatPhoneApplication context) {
		this.app = context;
		this.logFile = context.coretask.DATA_FILE_PATH + "/var/wifidetect.log";
		this.detectPath = context.coretask.DATA_FILE_PATH
				+ "/conf/wifichipsets/";
		this.edifyPath = context.coretask.DATA_FILE_PATH + "/conf/adhoc.edify";
		this.edifysrcPath = context.coretask.DATA_FILE_PATH
				+ "/conf/adhoc.edify.src";
		this.alarmManager = (AlarmManager) app
				.getSystemService(Context.ALARM_SERVICE);

		createRoutingImp();

		// init wifiManager
		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		wifiApManager = WifiApControl.getApControl(wifiManager);

		wifiState = wifiManager.getWifiState();
		if (wifiApManager != null)
			wifiApState = wifiApManager.getWifiApState();

		checkWifiMode();

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

		String adhocStatus = app.coretask.getProp("adhoc.status");

		if (currentMode == null && app.coretask.isNatEnabled()
				&& adhocStatus.equals("running")) {
			// looks like the application force closed and
			// restarted, check that everything we require is still
			// running.
			currentMode = WifiMode.Adhoc;
			Log.v("BatPhone", "Detected adhoc mode already running");
		}

		if (app.settings.getBoolean("meshRunning", false)) {
			try {
				this.startCycling();
			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}
		}

		// receive wifi state broadcasts.
		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		filter.addAction(ALARM);

		app.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();

				if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {

					wifiState = intent.getIntExtra(
							WifiManager.EXTRA_WIFI_STATE,
							WifiManager.WIFI_STATE_UNKNOWN);
					Log.v("BatPhone", "new client state: " + wifiState);

					// if the user tries to enable wifi, and we're running adhoc
					// their attempt will fail, but we can finish it for them
					if (!changing
							&& wifiState == WifiManager.WIFI_STATE_ENABLING
							&& currentMode == WifiMode.Adhoc)
						setWiFiModeAsync(WifiMode.Client);

					checkWifiMode();

				} else if (action
						.equals(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION)) {

					wifiApState = intent.getIntExtra(
							WifiApControl.EXTRA_WIFI_AP_STATE,
							WifiApControl.WIFI_AP_STATE_FAILED);
					Log.v("BatPhone", "new AP state: " + wifiApState);

					// if the user tries to enable AP, and we're running adhoc
					// their attempt will fail, but we can finish it for them
					if (!changing
							&& wifiApState == WifiApControl.WIFI_AP_STATE_ENABLING
							&& currentMode == WifiMode.Adhoc)
						setWiFiModeAsync(WifiMode.Ap);

					checkWifiMode();

				} else if (action
						.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
					supplicantState = intent
							.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
					Log.v("BatPhone", "Supplicant State: " + supplicantState);

					testClientState();

				} else if (action.equals(ALARM)) {
					Log.v("BatPhone", "Alarm firing...");

					nextMode();
				}
			}
		}, filter);

	}

	private void createRoutingImp() {
		String routing = app.settings.getString("routingImpl", "batman");
		if (routing.equals("batman")) {
			Log.v("BatPhone", "Using batman routing");
			this.routingImp = new Batman(app.coretask);
		} else if (routing.equals("olsr")) {
			Log.v("BatPhone", "Using olsr routing");
			this.routingImp = new Olsr(app.coretask);
		} else
			Log.e("BatPhone", "Unknown routing implementation " + routing);
	}

	public void setRouting() throws IOException {
		boolean running = (routingImp == null ? false : routingImp.isRunning());

		if (running)
			routingImp.stop();

		createRoutingImp();

		if (running)
			routingImp.start();
	}

	private PeerFinder peerFinder;

	private PeerParser getPeerParser() {
		if (currentMode == WifiMode.Adhoc)
			return routingImp;
		return peerFinder;
	}

	public ArrayList<PeerRecord> getPeers() throws IOException {
		PeerParser parser = getPeerParser();
		if (parser == null)
			return null;
		return parser.getPeerList();
	}

	public int getPeerCount() throws IOException {
		PeerParser parser = getPeerParser();
		if (parser == null)
			return 1;
		return parser.getPeerCount();
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

	public class Chipset {
		File detectScript;
		public String chipset;

		Chipset(File detectScript) {
			this.detectScript = detectScript;
			String filename = detectScript.getName();
			this.chipset = filename.substring(0, filename.lastIndexOf('.'));
		}

		@Override
		public String toString() {
			return chipset;
		}
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
			setChipset("unknown", null, null, null);
		} else {
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
		return wifichipset;
	}

	public String getChipset() {
		return wifichipset;
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
			Set<WifiMode> modes = EnumSet.noneOf(WifiMode.class);
			String stAdhoc_on = null;
			String stAdhoc_off = null;

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
									modes.add(m);
							} catch (IllegalArgumentException e) {
							}
						}
						if (arChipset.length >= 3)
							stAdhoc_on = arChipset[2];
						if (arChipset.length >= 4)
							stAdhoc_off = arChipset[3];
					}

				}

				in.close();

				if (matches < 1)
					reject = true;

				// Return our final verdict
				if (!reject) {
					Log.i("BatPhone", "identified chipset " + chipset);
					writer.write("is " + chipset + "\n");

					setChipset(chipset.chipset, modes, stAdhoc_on, stAdhoc_off);
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
	public void setChipset(String chipset, Set<WifiMode> modes,
			String stAdhoc_on, String stAdhoc_off) {

		if (modes == null)
			modes = EnumSet.noneOf(WifiMode.class);

		// add support for modes via SDK if available
		if (!modes.contains(WifiMode.Ap) && wifiApManager != null)
			modes.add(WifiMode.Ap);
		if (!modes.contains(WifiMode.Client))
			modes.add(WifiMode.Client);
		if (!modes.contains(WifiMode.Sleep))
			modes.add(WifiMode.Sleep);

		// make sure we have root permission for adhoc support
		if (modes.contains(WifiMode.Adhoc)) {
			if (!app.coretask.hasRootPermission()) {
				modes.remove(WifiMode.Adhoc);
				Log.v("BatPhone",
						"Unable to support adhoc mode without root permission");
			}
		}

		wifichipset = chipset;
		supportedModes = modes;

		try {
			FileOutputStream out = new FileOutputStream(edifyPath);
			FileInputStream fstream = new FileInputStream(edifysrcPath);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			String strLine;
			// Read File Line By Line
			while ((strLine = in.readLine()) != null) {
				if (strLine.startsWith(strAh_on_tag)) {
					if (stAdhoc_on != null)
						appendFile(out, detectPath + stAdhoc_on);
				} else if (strLine.startsWith(strAh_off_tag)) {
					if (stAdhoc_off != null)
						appendFile(out, detectPath + stAdhoc_off);
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
		return mode == null || this.supportedModes.contains(mode);
	}

	public void setWiFiMode(WifiMode newMode) throws IOException {
		if (!isModeSupported(newMode))
			throw new IOException("Wifi mode " + newMode + " is not supported");

		releaseControl();

		switchWiFiMode(newMode);
	}

	private void setWiFiModeAsync(final WifiMode newMode) {
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					setWiFiMode(newMode);
				} catch (Exception e) {
					Log.v("BatPhone", e.toString(), e);
				}
			}
		};
		t.start();
	}

	private void releaseAlarm() {
		// kill the current alarm if there is one
		if (alarmIntent != null) {
			alarmManager.cancel(alarmIntent);
			alarmIntent = null;
			Log.v("BatPhone", "Cancelled alarm");
		}
	}

	private void setAlarm() {
		// create a new alarm to wake up the phone and switch modes.
		// TODO add percentage of randomness to timer

		releaseAlarm();
		alarmIntent = PendingIntent.getBroadcast(app, 0, new Intent(ALARM),
				PendingIntent.FLAG_UPDATE_CURRENT);

		int timer = currentMode.sleepTime * 1000;
		Log.v("BatPhone", "Set alarm for " + timer + "ms");
		alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
				+ timer, alarmIntent);
	}

	private void testClientState() {
		if (autoCycling && supplicantState != null) {
			// lock the mode if we start associating with any known
			// AP.
			switch (supplicantState) {
			case ASSOCIATED:
			case ASSOCIATING:
			case COMPLETED:
			case FOUR_WAY_HANDSHAKE:
			case GROUP_HANDSHAKE:
				if (!modeLocked)
					lockMode();
				break;
			default:
				if (modeLocked) {
					try {
						startCycling();
					} catch (IOException e) {
						Log.e("BatPhone", e.toString(), e);
					}
				}
				break;
			}
		}
	}

	private WifiMode findNextMode(WifiMode current) {
		// Cycle to the next supported wifi mode
		WifiMode values[] = WifiMode.values();

		int index = 0;
		if (currentMode != null)
			index = this.currentMode.ordinal() + 1;

		while (true) {
			if (index >= values.length)
				index = 0;
			WifiMode newMode = values[index];
			if (this.supportedModes.contains(newMode))
				return newMode;
			index++;
		}
	}

	private void nextMode() {
		if (modeLocked || !autoCycling)
			return;

		try {
			this.switchWiFiMode(findNextMode(lastActiveMode));
			setAlarm();
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}

	public void releaseControl() {
		releaseAlarm();
		modeLocked = false;
		autoCycling = false;
	}

	public void lockMode() {
		// ignore over enthusiastic callers
		if (modeLocked || !autoCycling)
			return;

		releaseAlarm();
		modeLocked = true;
		Log.v("BatPhone", "Locked mode to " + currentMode);
	}

	public void startCycling() throws IOException {
		// ignore over enthusiastic callers
		if (autoCycling && !modeLocked)
			return;

		Log.v("BatPhone", "Cycling modes");

		autoCycling = true;
		modeLocked = false;

		if (this.currentMode == null)
			nextMode();
		else {
			setAlarm();

			if (currentMode == WifiMode.Client) {
				testNetwork();
				testClientState();
			}
		}
	}

	public WifiMode getCurrentMode() {
		return currentMode;
	}

	public boolean isCycling() {
		return autoCycling;
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
		if (this.wifiManager.isWifiEnabled())
			this.wifiManager.setWifiEnabled(false);
		if (!this.wifiApManager.setWifiApEnabled(netConfig, true))
			throw new IOException("Failed to control access point mode");
		waitForApState(WifiManager.WIFI_STATE_ENABLED);
	}

	private void stopAp() throws IOException {
		if (!this.wifiApManager.setWifiApEnabled(null, false))
			throw new IOException("Failed to control access point mode");
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

	private boolean hasNetwork(String ssid) {
		for (WifiConfiguration network : wifiManager.getConfiguredNetworks()) {
			if (network.SSID.equals(ssid))
				return true;
		}
		return false;
	}

	private void testNetwork() {
		if (hasNetwork("BatPhone Installation"))
			return;
		WifiConfiguration netConfig = new WifiConfiguration();
		netConfig.SSID = "BatPhone Installation";
		netConfig.allowedAuthAlgorithms
				.set(WifiConfiguration.AuthAlgorithm.OPEN);
		wifiManager.addNetwork(netConfig);
	}

	private void startClient() throws IOException {
		testNetwork();
		if (this.wifiApManager != null && this.wifiApManager.isWifiApEnabled())
			this.wifiApManager.setWifiApEnabled(null, false);
		if (!this.wifiManager.setWifiEnabled(true))
			throw new IOException("Failed to control wifi client mode");
		waitForClientState(WifiManager.WIFI_STATE_ENABLED);
	}

	private void stopClient() throws IOException {
		if (!this.wifiManager.setWifiEnabled(false))
			throw new IOException("Failed to control wifi client mode");
		waitForClientState(WifiManager.WIFI_STATE_DISABLED);
	}

	private void startAdhoc() throws IOException {
		if (routingImp == null)
			throw new IllegalStateException("No routing protocol configured");

		// Get WiFi in adhoc mode and batmand running
		if (app.coretask.runRootCommand(app.coretask.DATA_FILE_PATH
				+ "/bin/adhoc start 1") != 0)
			throw new IOException("Failed to start adhoc mode");

		if (!routingImp.isRunning()) {
			Log.v("BatPhone", "Starting routing engine");
			routingImp.start();
		}
	}

	private void stopAdhoc() throws IOException {
		if (routingImp != null) {
			Log.v("BatPhone", "Stopping routing engine");
			this.routingImp.stop();
		}

		if (app.coretask.runRootCommand(app.coretask.DATA_FILE_PATH
				+ "/bin/adhoc stop 1") != 0)
			throw new IOException("Failed to stop adhoc mode");
	}

	private synchronized void switchWiFiMode(WifiMode newMode)
			throws IOException {
		if (newMode == currentMode)
			return;

		try {
			// stop paying attention to broadcast receivers while forcing a mode
			// change
			changing = true;

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

			modeChanged(newMode, true);
		} catch (IOException e) {
			// if something went wrong, try to work out what the mode currently
			// is.
			changing = false;
			checkWifiMode();
			throw e;
		}
	}
}
