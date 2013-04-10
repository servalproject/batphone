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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.servalproject.batphone.CallHandler;
import org.servalproject.meshms.IncomingMeshMS;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.servald.BundleId;
import org.servalproject.servald.Identity;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalD.RhizomeManifestResult;
import org.servalproject.servald.ServalDMonitor;
import org.servalproject.system.BluetoothService;
import org.servalproject.system.ChipsetDetection;
import org.servalproject.system.CoreTask;
import org.servalproject.system.NetworkManager;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.net.Uri;
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

	// fake some peers for testing
	public boolean test = false;

	public static final String MSG_TAG = "ADHOC -> AdhocApplication";

	// Bluetooth
	BluetoothService bluetoothService = null;

	// Preferences
	public SharedPreferences settings = null;
	private File ourApk;

	// Various instantiations of classes that we need.
	public NetworkManager nm = null;
	public CoreTask coretask = null;
	public Control controlService = null;

	public static String version="Unknown";
	public static long lastModified;

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
	public static final String ACTION_STATUS = "org.servalproject.ACTION_STATUS";
	public static final String EXTRA_STATUS = "status";
	private State state = State.Broken;
	private String status = "Off";

	private boolean wasRunningLastTime;
	@Override
	public void onCreate() {
		Log.d(MSG_TAG, "Calling onCreate()");
		context=this;

		//create CoreTask
		this.coretask = new CoreTask();
		this.coretask.setPath(this.getApplicationContext().getFilesDir().getParent());

        // Preferences
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);

		wasRunningLastTime = settings.getBoolean("meshRunning", false);

		// this appears to be a load bearing log statement, without it services
		// may not restart!
		// perhaps the compiler is migrating the code around?
		Log.v("BatPhone", "Was running? " + wasRunningLastTime);

		checkForUpgrade();

		ChipsetDetection detection = ChipsetDetection.getDetection();

		String chipset = settings.getString("chipset", "Automatic");
		if (chipset.equals("Automatic"))
			chipset = settings.getString("detectedChipset", "");

		if (chipset != null && !"".equals(chipset)
				&& !"UnKnown".equals(chipset)) {
			detection.testAndSetChipset(chipset);
		}
		if (detection.getChipset() == null) {
			detection.setChipset(null);
		}
		this.nm = NetworkManager.getNetworkManager(this);

		if (state != State.Installing && state != State.Upgrading)
			getReady();
	}

	public boolean getReady() {
		List<Identity> identities = Identity.getIdentities();
		if (identities.size() >= 1) {
			Identity main = identities.get(0);
			Intent intent = new Intent("org.servalproject.SET_PRIMARY");
			intent.putExtra("did", main.getDid());
			intent.putExtra("sid", main.subscriberId.toString());
			this.sendStickyBroadcast(intent);
		}

        // Bluetooth-Service
        this.bluetoothService = BluetoothService.getInstance();
        this.bluetoothService.setApplication(this);

		Instrumentation
				.setEnabled(settings.getBoolean("instrumentpref", false));

		Rhizome.setRhizomeEnabled();

		if (wasRunningLastTime) {
			Log.v("BatPhone", "Restarting serval services");
			Intent serviceIntent = new Intent(this, Control.class);
			startService(serviceIntent);
		}

		// show notification for any unseen messages
		IncomingMeshMS.initialiseNotification(this);
		return true;
	}

	public void checkForUpgrade() {
		String installed = settings.getString("lastInstalled", "");

		version = this.getString(R.string.version);
		try {
			ourApk = new File(this.getPackageCodePath());
			lastModified = ourApk.lastModified();
		} catch (Exception e) {
			Log.v("BatPhone", e.getMessage(), e);
			this.displayToastMessage("Unable to determine if this application needs to be updated");
		}

		if (installed.equals("")) {
			setState(State.Installing);
		} else if (!installed.equals(version + " " + lastModified)) {
			// We have a newer version, so schedule it for installation.
			// Actual installation will be triggered by the preparation
			// wizard so that the user knows what is going on.
			setState(State.Upgrading);
		} else {
			// TODO check rhizome for manifest version of
			// "installed_manifest_id"
			// which may have already arrived (and been ignored?)
			setState(State.Off);
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

	public static File getStorageFolder() {
		String storageState = Environment.getExternalStorageState();
		File folder = null;

		if (Environment.MEDIA_MOUNTED.equals(storageState)) {
			folder = ServalBatPhoneApplication.context
					.getExternalFilesDir(null);
			if (folder != null)
				folder.mkdirs();
		} else
			Log.v("BatPhone", "External storage is " + storageState);
		return folder;
	}

	public State getState() {
		return state;
	}

	void setState(State state) {
		if (this.state == state)
			return;

		this.state = state;

		Intent intent = new Intent(ServalBatPhoneApplication.ACTION_STATE);
		intent.putExtra(ServalBatPhoneApplication.EXTRA_STATE, state.ordinal());
		this.sendBroadcast(intent);

		if (this.nm != null)
			nm.control.onAppStateChange(state);
	}

	public void updateStatus(String status) {
		this.status = status;
		Intent intent = new Intent(ServalBatPhoneApplication.ACTION_STATUS);
		intent.putExtra(ServalBatPhoneApplication.EXTRA_STATUS, status);
		this.sendBroadcast(intent);
	}

	public String getStatus() {
		return status;
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

	public ServalDMonitor servaldMonitor = null;

	protected long lastVompCallTime = 0;

	public CallHandler callHandler;

	public static boolean terminate_setup = false;
	public static boolean terminate_main = false;

	private void createEmptyFolders() {
		// make sure all this folders exist, even if empty
		String[] dirs = {
				"/htdocs", "/htdocs/packages"
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

	public void notifySoftwareUpdate(BundleId manifestId) {
		try {
			Log.v("Batphone", "Prompting to install new version");
			File newVersion = new File(Rhizome.getTempDirectoryCreated(),
					manifestId.toHex() + ".apk");

			// use the same path to create a combined payload and manifest
			ServalD.rhizomeExtractBundle(manifestId, newVersion,
					newVersion);

			// Construct an intent to start the install
			Intent i = new Intent("android.intent.action.VIEW")
					.setType("application/vnd.android.package-archive")
					.setClassName("com.android.packageinstaller",
							"com.android.packageinstaller.PackageInstallerActivity")
					.setData(Uri.fromFile(newVersion))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

			Notification n = new Notification(R.drawable.ic_serval_logo,
					"A new version of Serval Mesh is available",
					System.currentTimeMillis());

			n.setLatestEventInfo(this, "Software Update",
					"A new version of Serval Mesh is available",
					PendingIntent.getActivity(this, 0, i,
							PendingIntent.FLAG_ONE_SHOT));

			n.flags = Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE
					| Notification.FLAG_AUTO_CANCEL;

			NotificationManager nm = (NotificationManager) this
					.getSystemService(Context.NOTIFICATION_SERVICE);
			nm.notify("Upgrade", 0, n);

		} catch (Exception e) {
			Log.e("BatPhone", e.getMessage(), e);
		}

	}

	public void installFiles() {
		try{
			// if we just reinstalled, the old dna process, or asterisk, might
			// still be running, and may need to be replaced
			ServalD.serverStop();
			this.coretask.killProcess("bin/dna", false);
			this.coretask.killProcess("bin/asterisk", false);

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

			// write a new nvram.txt with the mac address in it (for ideos
			// phones)
			replaceInFile("/system/wifi/nvram.txt",
					this.coretask.DATA_FILE_PATH + "/conf/nvram.txt",
					new String[] { "macaddr" }, new String[] { String.format(
							"%02x:%02x:%02x:%02x:%02x:%02x", bytes[0],
							bytes[1], bytes[2], bytes[3], bytes[4], bytes[5]) });

			Rhizome.setRhizomeEnabled();
			Editor ed = settings.edit();

			// attempt to import our own bundle into rhizome.
			if (ServalD.isRhizomeEnabled() && ourApk != null) {
				try {
					RhizomeManifestResult result = ServalD.rhizomeImportBundle(
							ourApk, ourApk);
					ed.putString("installed_manifest_id",
							result.manifestId.toHex());
					ed.putLong("installed_manifest_version",
							result.version);
				} catch (Exception e) {
					ed.remove("installed_manifest_id");
					ed.remove("installed_manifest_version");
					Log.v("BatPhone", e.getMessage(), e);
				}
			}

			// remove legacy ssid preference values
			// (and hope that doesn't annoy anyone)
			String ssid_pref = settings.getString("ssidpref", null);
			if (ssid_pref != null
					&& ("Mesh".equals(ssid_pref) ||
						"ServalProject.org".equals(ssid_pref)))
				ed.remove("ssidpref");

			ed.putString("lastInstalled", version + " "
					+ lastModified);

			ed.commit();

			setState(State.Off);
			getReady();

		}catch(Exception e){
			Log.v("BatPhone","File instalation failed",e);
			// Sending message
			ServalBatPhoneApplication.this.displayToastMessage(e.toString());
		}
    }

	public boolean isMainThread() {
		return this.getMainLooper().getThread().equals(Thread.currentThread());
	}

	private Toast toast = null;

    // Display Toast-Message
	public void displayToastMessage(String message) {
		if (!isMainThread()) {
			Message msg = new Message();
			msg.obj = message;
			ServalBatPhoneApplication.this.displayMessageHandler.sendMessage(msg);
			return;
		}

		if (toast == null) {
			LayoutInflater li = LayoutInflater.from(this);
			View layout = li.inflate(R.layout.toastview, null);
			toast = new Toast(getApplicationContext());
			toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
			toast.setDuration(Toast.LENGTH_LONG);
			toast.setView(layout);
		}
		toast.cancel();
		TextView text = (TextView) toast.getView().findViewById(R.id.toastText);
		text.setText(message);
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
