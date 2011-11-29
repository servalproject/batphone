/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

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

package org.servalproject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import org.servalproject.dna.DataFile;
import org.servalproject.dna.Dna;
import org.servalproject.dna.SubscriberId;
import org.servalproject.system.BluetoothService;
import org.servalproject.system.ChipsetDetection;
import org.servalproject.system.CoreTask;
import org.servalproject.system.MeshManager;
import org.servalproject.system.WiFiRadio;
import org.servalproject.system.WifiMode;
import org.sipdroid.sipua.ui.Receiver;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class ServalBatPhoneApplication extends Application {

	public static final String MSG_TAG = "ADHOC -> AdhocApplication";

	public static final String DEFAULT_LANNETWORK = "10.130.1.110/24";
	public static final String DEFAULT_SSID = "ServalProject.org";
	public static final String DEFAULT_CHANNEL = "1";

	// Devices-Information
	// public String deviceType = "unknown";
	// public String interfaceDriver = "wext";

	// Bluetooth
	BluetoothService bluetoothService = null;

	// Preferences
	public SharedPreferences settings = null;
	public SharedPreferences.Editor preferenceEditor = null;

	// Various instantiations of classes that we need.
	public WiFiRadio wifiRadio;
	public MeshManager meshManager;
	public CoreTask coretask = null;

	public static String version="Unknown";
	public static long lastModified;

	// adhoc allocated ip address
    private String ipaddr="";
	private SubscriberId primarySubscriberId=null;
    private String primaryNumber="";
    public static ServalBatPhoneApplication context;

	public enum State {
		Installing, Off, Starting, On, Stopping, Broken
	}

	public static final String ACTION_STATE = "org.servalproject.ACTION_STATE";
	public static final String EXTRA_STATE = "state";
	private State state;

    Receiver m_receiver;

	@Override
	public void onCreate() {
		Log.d(MSG_TAG, "Calling onCreate()");
		context=this;

		//create CoreTask
		this.coretask = new CoreTask();
		this.coretask.setPath(this.getApplicationContext().getFilesDir().getParent());

        // Set device-information
		// this.deviceType = Configuration.getDeviceType();
		// this.interfaceDriver =
		// Configuration.getWifiInterfaceDriver(this.deviceType);

        // Preferences
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);

        // preferenceEditor
        this.preferenceEditor = settings.edit();

		checkForUpgrade();

		if (state != State.Installing && wifiSetup == true)
			getReady();
	}

	public boolean getReady() {
		boolean running = settings.getBoolean("meshRunning", false);
		ChipsetDetection detection = ChipsetDetection.getDetection();

		if (detection.getChipset() == null) {
			// re-init chipset
			String chipset = settings.getString("chipset", "Automatic");
			if (chipset.equals("Automatic"))
				chipset = settings.getString("detectedChipset", "");

			if (chipset != null && !"".equals(chipset)) {
				detection.testAndSetChipset(chipset, true);
			}
			if (detection.getChipset() == null) {
				detection.setChipset(null);
			}
		}

		if (this.wifiRadio == null)
			this.wifiRadio = WiFiRadio.getWiFiRadio(this);

		this.primarySubscriberId = DataFile.getSid(0);
		this.primaryNumber = DataFile.getDid(0);

		if (primaryNumber!=null && !primaryNumber.equals("")){
			Intent intent=new Intent("org.servalproject.SET_PRIMARY");
			intent.putExtra("did", primaryNumber);
			if (primarySubscriberId!=null)
				intent.putExtra("sid", primarySubscriberId.toString());
			this.sendStickyBroadcast(intent);
		}

		ipaddr=settings.getString("lannetworkpref",ipaddr+"/8");
		if (ipaddr.indexOf('/')>0) ipaddr = ipaddr.substring(0, ipaddr.indexOf('/'));

        // Bluetooth-Service
        this.bluetoothService = BluetoothService.getInstance();
        this.bluetoothService.setApplication(this);

        m_receiver=new Receiver();
        m_receiver.register(this);

		meshManager = new MeshManager(this);

   		Instrumentation.setEnabled(settings.getBoolean("instrumentpref", false));

		meshManager.setEnabled(running);
		if (running) {
			setState(State.Starting);
			Thread t = new Thread() {
				@Override
				public void run() {
					try {
						wifiRadio.turnOn();
						setState(ServalBatPhoneApplication.State.On);
					} catch (IOException e) {
						setState(ServalBatPhoneApplication.State.Broken);
						Log.e("BatPhone", e.toString(), e);
					}
				}
			};
			t.start();
		} else
			setState(State.Off);
		return true;
	}

	public void installFilesIfRequired() {
		if (state == State.Installing) {
			String installed = settings.getString("lastInstalled", "");
			String dataHash = settings.getString("installedDataHash", "");
			installFiles(!installed.equals(""), dataHash);
		}
	}

	public void checkForUpgrade() {
		try {
			String installed = settings.getString("lastInstalled", "");

			PackageInfo info = getPackageManager().getPackageInfo(
					getPackageName(), 0);

			version = info.versionName;

			// force install mode if apk has changed
			// TODO, in API 9 you can get the installed time from packegeinfo
			File apk = new File(info.applicationInfo.sourceDir);
			lastModified = apk.lastModified();

			if (!installed.equals(version + " " + lastModified)) {
				// We have a newer version, so schedule it for installation.
				// Actual installation will be triggered by the preparation
				// wizard so that the user knows what is going on.
				setState(State.Installing);
				return;
			}
		} catch (NameNotFoundException e) {
			Log.v("BatPhone", e.toString(), e);
			return;
		}
	}

	@Override
	public void onTerminate() {
		Log.d(MSG_TAG, "Calling onTerminate()");
    	// Stopping Adhoc
		try {
			this.stopAdhoc();
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}

	public String netSizeToMask(int netbits)
	{
		int donebits=0;
		String netmask="";
		while (netbits>7) {
			if (netmask.length()>0) netmask=netmask+".";
			netmask=netmask+"255";
			netbits-=8;
			donebits+=8;
		}
		if (donebits<32) {
			if (netmask.length()>0) netmask=netmask+".";
			switch(netbits) {
			case 0: netmask=netmask+"0"; break;
			case 1: netmask=netmask+"128"; break;
			case 2: netmask=netmask+"192"; break;
			case 3: netmask=netmask+"224"; break;
			case 4: netmask=netmask+"240"; break;
			case 5: netmask=netmask+"248"; break;
			case 6: netmask=netmask+"252"; break;
			case 7: netmask=netmask+"254"; break;
			}
			donebits+=8;
		}
		while(donebits<32) {
			if (netmask.length()>0) netmask=netmask+".";
			netmask=netmask+"0";
			donebits+=8;
		}
		return netmask;
	}

	public String getSsid() {
		return this.settings.getString("ssidpref", DEFAULT_SSID);
	}


	public File getStorageFolder() {
    	String storageState = Environment.getExternalStorageState();
    	File folder;
    	if (Environment.MEDIA_MOUNTED.equals(storageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)){
    		folder=new File(Environment.getExternalStorageDirectory(), "/BatPhone");
    	}else
    		folder=new File(this.coretask.DATA_FILE_PATH+"/var");
    	folder.mkdirs();
    	return folder;
    }


	public boolean setApEnabled(boolean enabled){
		try {
			wifiRadio.setHardLock(enabled);

			if (enabled) {
				wifiRadio.setWiFiMode(WifiMode.Ap);
				this.meshManager.setEnabled(true);
			} else if (getState() != State.On) {
				wifiRadio.setWiFiMode(WifiMode.Off);
				this.meshManager.setEnabled(false);
			}
			return true;
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
			return false;
		}
	}

	private void startWifi() throws IOException {
		meshManager.setEnabled(true);
		wifiRadio.turnOn();
	}

	// Start/Stop Adhoc
	public synchronized void startAdhoc() throws IOException {
		if (getState() != State.Off)
			return;

		setState(State.Starting);
		try {
			startWifi();

			setState(State.On);
		} catch (IOException e) {
			setState(State.Off);
			throw e;
		}
    }

	public void stopWifi() throws IOException {
		meshManager.setEnabled(false);
		WifiMode mode = wifiRadio.getCurrentMode();

		// If the current mode is Ap or Adhoc, the user will probably want us to
		// turn off the radio.
		// If client mode, we'll ask them
		switch (mode) {
		case Adhoc:
		case Ap:
			this.wifiRadio.setWiFiMode(WifiMode.Off);
			break;
		}
		wifiRadio.checkAlarm();
	}

	public synchronized void stopAdhoc() throws IOException {
		if (getState() != State.On)
			return;

		setState(State.Stopping);
		try {
			stopWifi();
		} finally {
			setState(State.Off);
		}
    }

	public synchronized boolean restartAdhoc() {
		if (getState() != State.On)
			return false;

		setState(State.Starting);
    	try{
    		this.stopWifi();
    		this.startWifi();
			setState(State.On);
    		return true;
    	}catch(Exception e){
    		this.displayToastMessage(e.toString());
			setState(State.Off);
    		return false;
    	}
    }

	public State getState() {
		return state;
	}

	void setState(State state) {
		Editor ed = ServalBatPhoneApplication.this.settings.edit();
		ed.putBoolean("meshRunning", state == State.On);
		ed.commit();

		this.state = state;

		Intent intent = new Intent(ServalBatPhoneApplication.ACTION_STATE);
		intent.putExtra(ServalBatPhoneApplication.EXTRA_STATE, state.ordinal());
		this.sendBroadcast(intent);
	}

    public String getAdhocNetworkDevice() {
    	boolean bluetoothPref = this.settings.getBoolean("bluetoothon", false);
        if (bluetoothPref)
			return "bnep";
		else {
			/**
			 * TODO: Quick and ugly workaround for nexus
			 */
			// if
			// (Configuration.getWifiInterfaceDriver(this.deviceType).equals(Configuration.DRIVER_SOFTAP_GOG))
			// {
			// return "wl0.1";
			// }
			// else {
				return this.coretask.getProp("wifi.interface");
			// }
		}
    }

    // gets user preference on whether sync should be disabled during adhoc
    public boolean isSyncDisabled(){
		return this.settings.getBoolean("syncpref", false);
	}

    // gets user preference on whether sync should be disabled during adhoc
    public boolean isUpdatecDisabled(){
		return this.settings.getBoolean("updatepref", false);
	}

    // get preferences on whether donate-dialog should be displayed
    public boolean showDonationDialog() {
    	return this.settings.getBoolean("donatepref", true);
    }

    Handler displayMessageHandler = new Handler(){
        @Override
		public void handleMessage(Message msg) {
       		if (msg.obj != null) {
				displayToastMessage((String) msg.obj);
       		}
        	super.handleMessage(msg);
        }
    };

	protected static boolean terminate_setup = false;
	protected static boolean terminate_main = false;

	public static boolean wifiSetup = false;
	public static boolean dontCompleteWifiSetup = false;

	public void resetNumber() throws IOException {
		this.primaryNumber = null;
		this.primarySubscriberId = null;
		Editor ed = ServalBatPhoneApplication.this.settings.edit();
		ed.remove("primaryNumber");
		ed.remove("primarySubscriber");
		ed.commit();

		if (this.getState() == State.On) {
			this.stopAdhoc();
		}

		this.meshManager.stopDna();

		File file = new File(this.coretask.DATA_FILE_PATH + "/tmp/myNumber.tmp");
		file.delete();
		file = new java.io.File(this.coretask.DATA_FILE_PATH + "/var/hlr.dat");
		file.delete();
	}

	public String getPrimaryNumber() {
		return primaryNumber;
	}

	public void setPrimaryNumber(String newNumber, boolean collectData)
			throws IOException,
			IllegalArgumentException, IllegalAccessException,
			InstantiationException {
		// Create default HLR entry
		if (newNumber == null || !newNumber.matches("[0-9+*#]{5,31}"))
			throw new IllegalArgumentException(
					"The phone number must contain only 0-9+*# and be at least 5 characters in length");

		if (newNumber.startsWith("11"))
			throw new IllegalArgumentException(
					"That number cannot be dialed. The prefix 11 is reserved for emergency use.");

		this.meshManager.startDna();

		Dna dna = new Dna();
		dna.addLocalHost();

		primarySubscriberId = DataFile.getSid(0);
		int tries = 0;
		while (primarySubscriberId == null && tries < 20) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Log.e("BatPhone", e.toString(), e);
			}
			primarySubscriberId = DataFile.getSid(0);
			tries++;
		}

		if (primarySubscriberId != null) {
			try {
				dna.writeDid(primarySubscriberId, (byte) 0, true, newNumber);
			} catch (IOException e) {
				// create a new subscriber if dna has forgotten about the old
				// one.
				// XXX - This is really a hard error, and we need to catch it
				// and probably quit
				primarySubscriberId = null;

			}
		}

		if (getState() != State.On)
			this.meshManager.stopDna();

		// TODO rework how asterisk determines the caller id.
		this.coretask.writeLinesToFile(this.coretask.DATA_FILE_PATH
				+ "/tmp/myNumber.tmp", newNumber);

		primaryNumber = newNumber;

		Editor ed = ServalBatPhoneApplication.this.settings.edit();
		ed.putString("primaryNumber", primaryNumber);
		if (primarySubscriberId != null)
			ed.putString("primarySubscriber", primarySubscriberId.toString());
		ed.putBoolean("dataCollection", collectData);
		ed.commit();

		Intent intent = new Intent("org.servalproject.SET_PRIMARY");
		intent.putExtra("did", primaryNumber);
		intent.putExtra("sid", primarySubscriberId.toString());
		this.sendStickyBroadcast(intent);

		try {
			String storageState = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(storageState)
					|| Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)) {
				File f = new File(Environment.getExternalStorageDirectory(),
						"/BatPhone");
				f.mkdirs();
				f = new File(f, "primaryNumber");
				FileOutputStream fs = new FileOutputStream(f);
				fs.write(newNumber.getBytes());
				fs.close();
			}
		} catch (IOException e) {

		}

		if (collectData)
			ChipsetDetection.getDetection().uploadLog();
    }

	public SubscriberId getSubscriberId() {
		return primarySubscriberId;
	}

	private void createEmptyFolders() {
		// make sure all this folders exist, even if empty
		String[] dirs = { "/tmp", "/htdocs", "/htdocs/packages", "/var/run",
				"/asterisk/var/run",
				"/asterisk/var/log/asterisk",
				"/asterisk/var/log/asterisk/cdr-csv",
				"/asterisk/var/log/asterisk/cdr-custom",
				"/asterisk/var/spool/asterisk/dictate",
				"/asterisk/var/spool/asterisk/meetme",
				"/asterisk/var/spool/asterisk/monitor",
				"/asterisk/var/spool/asterisk/system",
				"/asterisk/var/spool/asterisk/tmp",
				"/asterisk/var/spool/asterisk/voicemail",
				"/voiceSignature" };

		for (String dirname : dirs) {
			new File(this.coretask.DATA_FILE_PATH + dirname).mkdirs();
		}
	}

	public void replaceInFile(String inFile, String outFile,
			String variables[], String values[]) {
		try {
			File fileIn = new File(inFile);
			if (!fileIn.exists())
				return;

			boolean found[] = new boolean[variables.length];

			DataInputStream in = new DataInputStream(
					new FileInputStream(fileIn));
			FileOutputStream out = new FileOutputStream(outFile);

			String line;
			while ((line = in.readLine()) != null) {
				for (int i = 0; i < variables.length; i++) {
					if (line.startsWith(variables[i])) {
						line = variables[i] + '=' + values[i];
						found[i] = true;
						break;
					}
				}
				out.write(line.getBytes());
				out.write("\n".getBytes());
			}

			for (int i = 0; i < variables.length; i++) {
				if (!found[i]) {
					line = variables[i] + '=' + values[i];
					out.write(line.getBytes());
					out.write("\n".getBytes());
				}
			}

			in.close();
			out.close();

		} catch (FileNotFoundException e) {
		} catch (Exception e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}

	public void installFiles(boolean upgrade, String previousHash) {
		try{
			{
				AssetManager m = this.getAssets();

				Map<String, Character> instructions = null;
				if (upgrade) {
					instructions = new HashMap<String, Character>();
					DataInputStream in = new DataInputStream(m.open("log.txt"));
					String line;
					String newHash = null;
					File folder = new File(this.coretask.DATA_FILE_PATH);

					while ((line = in.readLine()) != null) {
						if (line.equals(""))
							continue;

						if (line.charAt(1) == '\t') {
							String filename = line.substring(7);
							char op = line.charAt(0);
							Character inst = instructions.get(filename);

							if (inst != null && (inst == 'D' || op == 'D'))
								continue;

							if (op == 'D') {
								File file = new File(folder, filename);
								Log.v("BatPhone", "Removing " + filename);
								file.delete();
							} else
								Log.v("BatPhone", "Should update " + filename);

							instructions.put(filename, op);

						} else {
							if (newHash == null)
								newHash = line;
							if (newHash == previousHash)
								break;
						}
					}
					in.close();

					preferenceEditor.putString("installedDataHash", newHash);
				}

				Log.v("BatPhone", "Extracting serval.zip");
				this.coretask.extractZip(m.open("serval.zip"),
						this.coretask.DATA_FILE_PATH, instructions);
			}
			createEmptyFolders();

			// if we just reinstalled, dna might still be running, which could
			// be very confusing...
			this.coretask.killProcess("bin/dna", false);
			this.coretask.killProcess("bin/asterisk", false);

			// Generate some random data for auto allocating IP / Mac / Phone
			// number
			SecureRandom random = new SecureRandom();
			byte[] bytes = new byte[6];

			random.nextBytes(bytes);

			// Mark MAC as locally administered unicast
			bytes[0] |= 0x2;
			bytes[0] &= 0xfe;

			ipaddr = settings.getString("lannetworkpref", "");
			if (ipaddr.equals("")) {
				// Set default IP address from the same random data
				ipaddr = String.format("10.%d.%d.%d",
						bytes[3] < 0 ? 256 + bytes[3] : bytes[3],
						bytes[4] < 0 ? 256 + bytes[4] : bytes[4],
						bytes[5] < 0 ? 256 + bytes[5] : bytes[5]);
			}

			// write a new nvram.txt with the mac address in it (for ideos
			// phones)
			replaceInFile("/system/wifi/nvram.txt",
					this.coretask.DATA_FILE_PATH + "/conf/nvram.txt",
					new String[] { "macaddr" }, new String[] { String.format(
							"%02x:%02x:%02x:%02x:%02x:%02x", bytes[0],
							bytes[1], bytes[2], bytes[3], bytes[4], bytes[5]) });

			preferenceEditor.putString("lannetworkpref", ipaddr + "/8");
			preferenceEditor.putString("lastInstalled", version + " "
					+ lastModified);
			preferenceEditor.commit();

			setState(State.Off);

		}catch(Exception e){
			Log.v("BatPhone","File instalation failed",e);
			// Sending message
			ServalBatPhoneApplication.this.displayToastMessage(e.toString());
		}
    }

    // Display Toast-Message
	public void displayToastMessage(String message) {
		if (!this.getMainLooper().getThread().equals(Thread.currentThread())){
			Message msg = new Message();
			msg.obj = message;
			ServalBatPhoneApplication.this.displayMessageHandler.sendMessage(msg);
			return;
		}

		LayoutInflater li = LayoutInflater.from(this);
		View layout = li.inflate(R.layout.toastview, null);
		TextView text = (TextView) layout.findViewById(R.id.toastText);
		text.setText(message);
		Toast toast = new Toast(getApplicationContext());
		toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
		toast.setDuration(Toast.LENGTH_LONG);
		toast.setView(layout);
		toast.show();
	}

    public int getVersionNumber() {
    	int version = -1;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionCode;
        } catch (Exception e) {
            Log.e(MSG_TAG, "Package name not found", e);
        }
        return version;
    }

    public String getVersionName() {
    	String version = "?";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionName;
        } catch (Exception e) {
            Log.e(MSG_TAG, "Package name not found", e);
        }
        return version;
    }

    /*
     * This method checks if changing the transmit-power is supported
     */
    public boolean isTransmitPowerSupported() {
		// XXX - This should be read from the capabilities list in the .detect
		// file
		// for the handset, or tested by running iwconfig.
		return false;
    }
}
