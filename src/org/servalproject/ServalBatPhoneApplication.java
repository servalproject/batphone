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
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.servalproject.batphone.CallHandler;
import org.servalproject.meshms.IncomingMeshMS;
import org.servalproject.servald.Identities;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDMonitor;
import org.servalproject.system.BluetoothService;
import org.servalproject.system.ChipsetDetection;
import org.servalproject.system.CoreTask;
import org.servalproject.system.WiFiRadio;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class ServalBatPhoneApplication extends Application {

	// fake some peers for testing
	public boolean test = false;

	public static final String MSG_TAG = "ADHOC -> AdhocApplication";

	public static final String DEFAULT_LANNETWORK = "10.130.1.110/24";
	public static final String DEFAULT_SSID = "Mesh";
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
	public CoreTask coretask = null;

	public static String version="Unknown";
	public static long lastModified;

	// adhoc allocated ip address
    private String ipaddr="";
	public static ServalBatPhoneApplication context;

	public enum State {
		Installing(R.string.state_installing),
		Upgrading(R.string.state_upgrading),
		Off(R.string.state_power_off),
		Starting(R.string.state_starting),
		On(R.string.state_power_on),
		Stopping(R.string.state_stopping),
		Broken(R.string.state_broken);

		private int resourceId;

		State(int resourceId) {
			this.resourceId = resourceId;
		}

		public int getResourceId() {
			return resourceId;
		}
	}

	public static final String ACTION_STATE = "org.servalproject.ACTION_STATE";
	public static final String EXTRA_STATE = "state";
	private State state;

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

		String chipset = settings.getString("detectedChipset", "");
		wifiSetup = !"".equals(chipset);

		if (state != State.Installing && state != State.Upgrading && wifiSetup)
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

			if (chipset != null && !"".equals(chipset)
					&& !"UnKnown".equals(chipset)) {
				detection.testAndSetChipset(chipset, true);
			}
			if (detection.getChipset() == null) {
				detection.setChipset(null);
			}
		}

		if (this.wifiRadio == null)
			this.wifiRadio = WiFiRadio.getWiFiRadio(this);

		if (Identities.getCurrentDid() == null) {
			try {
				resetNumber();
			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}
		} else {
			Intent intent=new Intent("org.servalproject.SET_PRIMARY");
			intent.putExtra("did", Identities.getCurrentDid());
			intent.putExtra("sid", Identities.getCurrentIdentity().toString());
			this.sendStickyBroadcast(intent);
		}

		ipaddr = settings.getString("lannetworkpref", ipaddr);
		if (ipaddr.indexOf('/')>0) ipaddr = ipaddr.substring(0, ipaddr.indexOf('/'));

        // Bluetooth-Service
        this.bluetoothService = BluetoothService.getInstance();
        this.bluetoothService.setApplication(this);

		Instrumentation
				.setEnabled(settings.getBoolean("instrumentpref", false));
		setState(State.Off);

		if (running) {
			Intent serviceIntent = new Intent(this, Control.class);
			startService(serviceIntent);
		}

		// show notification for any unseen messages
		IncomingMeshMS.initialiseNotification(this);
		return true;
	}

	public void installFilesIfRequired() {
		if (state == State.Installing || state == State.Upgrading) {
			// Install files as required
			installFiles();

			// Replace old default SSID with new default SSID
			// (it changed between 0.06 and 0.07).
			String newSSID = settings.getString("ssidpref",
					ServalBatPhoneApplication.DEFAULT_SSID);
			if (newSSID.equals("ServalProject.org")) {
				Editor e = settings.edit();
				e.putString("ssidpref", ServalBatPhoneApplication.DEFAULT_SSID);
				e.commit();
			}
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

			if (installed.equals("")) {
				setState(State.Installing);
			} else if (!installed.equals(version + " " + lastModified)) {
				// We have a newer version, so schedule it for installation.
				// Actual installation will be triggered by the preparation
				// wizard so that the user knows what is going on.
				setState(State.Upgrading);
			}
		} catch (NameNotFoundException e) {
			Log.v("BatPhone", e.toString(), e);
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

	public static File getStorageFolder() {
    	String storageState = Environment.getExternalStorageState();
    	File folder;
    	if (Environment.MEDIA_MOUNTED.equals(storageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)){
    		folder = Environment.getExternalStorageDirectory();
    	}else
    		folder=new File(context.coretask.DATA_FILE_PATH, "var");
    	folder.mkdirs();
    	return folder;
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

	public boolean showNoAdhocDialog;

	public ServalDMonitor servaldMonitor = null;

	protected long lastVompCallTime = 0;

	public CallHandler callHandler;

	protected static boolean terminate_setup = false;
	protected static boolean terminate_main = false;

	public static boolean wifiSetup = false;
	public static boolean dontCompleteWifiSetup = false;

	public void resetNumber() throws IOException {
		Editor ed = ServalBatPhoneApplication.this.settings.edit();
		ed.remove("primaryNumber");
		ed.remove("primarySubscriber");
		ed.commit();
	}

	public void setPrimaryNumber(String newNumber, String newName,
			boolean collectData)
			throws IOException, ServalDFailureException,
			IllegalArgumentException, IllegalAccessException,
			InstantiationException {
		// Create default HLR entry
		if (newNumber == null || !newNumber.matches("[0-9+*#]{5,31}"))
			throw new IllegalArgumentException(
					"The phone number must contain only 0-9+*# and be at least 5 characters in length");

		if (PhoneNumberUtils.isEmergencyNumber(newNumber)
				|| newNumber.startsWith("11"))
			throw new IllegalArgumentException(
					"That number cannot be dialed as it will be redirected to a cellular emergency service.");

		Control.startServalD();

		int tries = 0;
		while (Identities.getCurrentIdentity() == null && tries < 20) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Log.e("BatPhone", e.toString(), e);
			}
			tries++;
		}

		if (Identities.getCurrentIdentity() != null) {
			Identities.setDid(Identities.getCurrentIdentity(), newNumber,
					newName);
		}

		Editor ed = ServalBatPhoneApplication.this.settings.edit();
		ed.putString("primaryNumber", Identities.getCurrentDid());
		if (Identities.getCurrentIdentity() != null)
			ed.putString("primarySubscriber", Identities.getCurrentIdentity()
					.toString());
		ed.putBoolean("dataCollection", collectData);
		ed.commit();

		Intent intent = new Intent("org.servalproject.SET_PRIMARY");
		intent.putExtra("did", Identities.getCurrentDid());
		// Catch null pointer exception on setting number
		if (Identities.getCurrentIdentity() != null)
			intent.putExtra("sid", Identities.getCurrentIdentity().toString());
		this.sendStickyBroadcast(intent);

		try {
			String storageState = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(storageState)
					|| Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)) {
				File f = new File(Environment.getExternalStorageDirectory(),
						"/serval");
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

	private void createEmptyFolders() {
		// make sure all this folders exist, even if empty
		String[] dirs = { "/tmp", "/htdocs", "/htdocs/packages", "/var/run",
				"/var/serval-node", "/var/serval-node/import"
		};

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

	private Map<String, String> readTree(DataInputStream in) throws IOException {
		Map<String, String> tree = new HashMap<String, String>();
		String line;
		try {
			while ((line = in.readLine()) != null) {
				String fields[] = line.split("\\s+");
				String path, hash;
				if (fields.length == 1) {
					path = line;
					hash = "";
				} else if (fields.length < 4) {
					continue;
				} else {
					path = fields[3];
					hash = fields[1];
				}
				if (path.startsWith("data/"))
					path = path.substring(path.indexOf('/') + 1);
				tree.put(path, hash);
			}
		} finally {
			in.close();
		}
		return tree;
	}

	private void buildTree(Map<String, String> map, File folder) {
		for (File file : folder.listFiles()) {
			if (file.isDirectory()) {
				// ignore native libraries and preferences
				// everything else should be replaced / deleted
				if (file.getName().equals("lib")
						|| file.getName().equals("shared_prefs")
						|| file.getName().equals(".")
						|| file.getName().equals(".."))
					continue;

				buildTree(map, file);
			} else {
				String name = file.getAbsolutePath();
				if (name.startsWith(this.coretask.DATA_FILE_PATH)) {
					name = name
							.substring(this.coretask.DATA_FILE_PATH.length());
					map.put(name, "legacy");
				}
			}
		}
	}

	public void installFiles() {
		try{
			// if we just reinstalled, dna might still be running, and may need
			// to be replaced
			Control.stopServalD();

			{
				AssetManager m = this.getAssets();
				Set<String> extractFiles = null;
				File folder = new File(this.coretask.DATA_FILE_PATH);
				File oldTree = new File(folder, "manifest");

				Map<String, String> existingTree = null;
				if (oldTree.exists()) {
					existingTree = readTree(new DataInputStream(new FileInputStream(oldTree)));
				}else{
					existingTree = new HashMap<String, String>();
					buildTree(existingTree, folder);
				}

				if (!existingTree.isEmpty()) {
					Map<String, String> newTree = readTree(new DataInputStream(
							m.open("manifest")));

					Iterator<Entry<String, String>> it = existingTree
							.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<String, String> existing = it.next();
						String key = existing.getKey();
						String value = newTree.get(key);

						if (value != null) {
							if ((!value.equals(""))
									&& value.equals(existing.getValue())) {
								newTree.remove(key);
							}
							it.remove();
						} else {
							File file = new File(folder, key);
							if (file.exists()) {
								Log.v("BatPhone", "Removing " + key);
								file.delete();
							} else {
								Log.v("BatPhone", "Should remove " + key
										+ " but it doesn't exist?");
							}
						}
					}

					extractFiles = newTree.keySet();
				}

				this.coretask.writeFile(oldTree, m.open("manifest"), 0);

				Log.v("BatPhone", "Extracting serval.zip");
				this.coretask.extractZip(m.open("serval.zip"),
						new File(this.coretask.DATA_FILE_PATH), extractFiles);
			}
			createEmptyFolders();

			// Generate some random data for auto allocating IP / Mac / Phone
			// number
			SecureRandom random = new SecureRandom();
			byte[] bytes = new byte[6];

			random.nextBytes(bytes);

			// Mark MAC as locally administered unicast
			bytes[0] |= 0x2;
			bytes[0] &= 0xfe;

			ipaddr = settings.getString("lannetworkpref", "");

			// TODO only test for 10/8 once when upgrading past 0.08???
			if (ipaddr.equals("") || ipaddr.startsWith("10/")) {
				// Set default IP address from the same random data
				ipaddr = String.format("%d.%d.%d.%d/7", 28 | (bytes[2] & 1),
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

			preferenceEditor.putString("lannetworkpref", ipaddr);
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

	public boolean isMainThread() {
		return this.getMainLooper().getThread().equals(Thread.currentThread());
	}

    // Display Toast-Message
	public void displayToastMessage(String message) {
		if (!isMainThread()) {
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

	public String getIpAddress() {
		return ipaddr;
	}

	public void shareViaBluetooth() {
		try {
			File apk = new File(getApplicationInfo().sourceDir);
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(apk));
			intent.setType("image/apk");
			intent.addCategory(Intent.CATEGORY_DEFAULT);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			// There are at least two different classes for handling this intent on
			// different platforms.  Find the bluetooth one.  Alternative strategy: let the
			// user choose.
			// for (ResolveInfo r :
			// getPackageManager().queryIntentActivities(intent, 0)) {
			// if (r.activityInfo.packageName.equals("com.android.bluetooth")) {
			// intent.setClassName(r.activityInfo.packageName,
			// r.activityInfo.name);
			// intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			// break;
			// }
			// }
			this.startActivity(intent);
		} catch (Exception e) {
			Log.e("MAIN", "failed to send app", e);
			displayToastMessage("Failed to send app: " + e.getMessage());
		}
	}

}
