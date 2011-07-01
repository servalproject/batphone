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

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.servalproject.dna.Dna;
import org.servalproject.dna.SubscriberId;
import org.servalproject.system.BluetoothService;
import org.servalproject.system.ChipsetDetection;
import org.servalproject.system.Configuration;
import org.servalproject.system.CoreTask;
import org.servalproject.system.MeshManager;
import org.servalproject.system.WebserviceTask;
import org.servalproject.system.WiFiRadio;
import org.servalproject.system.WifiMode;
import org.sipdroid.sipua.ui.Receiver;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class ServalBatPhoneApplication extends Application {

	public static final String MSG_TAG = "ADHOC -> AdhocApplication";

	public final String DEFAULT_PASSPHRASE = "abcdefghijklm";
	// PGS 20100613 - VillageTelco MeshPotato compatible setting
	public final String DEFAULT_LANNETWORK = "10.130.1.110/24";
	public final String DEFAULT_ENCSETUP   = "wpa_supplicant";
	public final String DEFAULT_SSID = "potato";
	public final String DEFAULT_CHANNEL = "1";

	// Devices-Information
	public String deviceType = "unknown";
	public String interfaceDriver = "wext";

	// StartUp-Check performed
	public boolean firstRun = false;

	// Bluetooth
	BluetoothService bluetoothService = null;

	// Preferences
	public SharedPreferences settings = null;
	public SharedPreferences.Editor preferenceEditor = null;

	// Supplicant
	public CoreTask.WpaSupplicant wpasupplicant = null;
	// TiWlan.conf
	public CoreTask.TiWlanConf tiwlan = null;
	// adhoc.conf
	public CoreTask.AdhocConfig adhoccfg = null;
	// blue-up.sh
	public CoreTask.BluetoothConfig btcfg = null;

	public WiFiRadio wifiRadio;
	public MeshManager meshManager;

	// CoreTask
	public CoreTask coretask = null;

	// WebserviceTask
	public WebserviceTask webserviceTask = null;

	// Update Url, Diverted to Serval BatPhone versions
	private static final String APPLICATION_PROPERTIES_URL = "http://servalproject.org/batphone/android/application.properties";
	private static final String APPLICATION_DOWNLOAD_URL = "http://servalproject/batphone/files/";

	public static String version="Unknown";
	public static long lastModified;

	// adhoc allocated ip address
    private String ipaddr="";
	private SubscriberId primarySubscriberId=null;
    private String primaryNumber="";
    public static ServalBatPhoneApplication context;
	private boolean isRunning = false;

    Receiver m_receiver;

	@Override
	public void onCreate() {
		Log.d(MSG_TAG, "Calling onCreate()");
		context=this;

		//create CoreTask
		this.coretask = new CoreTask();
		this.coretask.setPath(this.getApplicationContext().getFilesDir().getParent());

		//create WebserviceTask
		this.webserviceTask = new WebserviceTask();

        // Set device-information
        this.deviceType = Configuration.getDeviceType();
        this.interfaceDriver = Configuration.getWifiInterfaceDriver(this.deviceType);

        // Preferences
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);

        // preferenceEditor
        this.preferenceEditor = settings.edit();

		try {
			String installed = settings.getString("lastInstalled", "");

			PackageInfo info=getPackageManager()
			   .getPackageInfo(getPackageName(), 0);

			version=info.versionName;

			// force install mode if apk has changed
			// TODO, in API 9 you can get the installed time from packegeinfo
			File apk = new File(info.applicationInfo.sourceDir);
			lastModified=apk.lastModified();

			if (!installed.equals(version+" "+lastModified)){
				this.firstRun=true;
			}
		} catch (NameNotFoundException e) {
			Log.v("BatPhone",e.toString(),e);
		}

		String subScrbr = settings.getString("primarySubscriber", "");
		if (subScrbr.length()==64){
			primarySubscriberId=new SubscriberId(subScrbr);
		}

		this.primaryNumber = settings.getString("primaryNumber", "");
		if (primaryNumber!=null && !primaryNumber.equals("")){
			Intent intent=new Intent("org.servalproject.SET_PRIMARY");
			intent.putExtra("did", primaryNumber);
			if (primarySubscriberId!=null)
				intent.putExtra("sid", primarySubscriberId.toString());
			this.sendStickyBroadcast(intent);
		}

		ipaddr=settings.getString("lannetworkpref",ipaddr+"/8");
		if (ipaddr.indexOf('/')>0) ipaddr = ipaddr.substring(0, ipaddr.indexOf('/'));

		this.wifiRadio = WiFiRadio.getWiFiRadio(this);

        // Supplicant config
        this.wpasupplicant = this.coretask.new WpaSupplicant();

        // tiwlan.conf
        this.tiwlan = this.coretask.new TiWlanConf();

        // adhoc.cfg
        this.adhoccfg = this.coretask.new AdhocConfig();
        this.adhoccfg.read();

    	// blue-up.sh
    	this.btcfg = this.coretask.new BluetoothConfig();

        // Bluetooth-Service
        this.bluetoothService = BluetoothService.getInstance();
        this.bluetoothService.setApplication(this);

        m_receiver=new Receiver();
        m_receiver.register(this);

		meshManager = new MeshManager(this);

   		Instrumentation.setEnabled(settings.getBoolean("instrumentpref", false));

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

	private String netSizeToMask(int netbits)
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

	public void updateConfiguration() {

		long startStamp = System.currentTimeMillis();

        boolean bluetoothPref = this.settings.getBoolean("bluetoothon", false);
		boolean encEnabled = this.settings.getBoolean("encpref", false);
		String ssid = this.settings.getString("ssidpref", DEFAULT_SSID);
        String txpower = this.settings.getString("txpowerpref", "disabled");
        String lannetwork = this.settings.getString("lannetworkpref", DEFAULT_LANNETWORK);
        String wepkey = this.settings.getString("passphrasepref", DEFAULT_PASSPHRASE);
        String wepsetupMethod = this.settings.getString("encsetuppref", DEFAULT_ENCSETUP);

		// adhoc.conf
        // PGS 20100613 - For Serval BatPhone, we want the user to specify the exact IP to use.
        // XXX - Eventually should pick a random one and use arp etc to avoid clashes.
		String[] pieces = lannetwork.split("/");
		String ipaddr = pieces[0];
        this.adhoccfg.read();
		this.adhoccfg.put("device.type", deviceType);
        this.adhoccfg.put("adhoc.mode", bluetoothPref ? "bt" : "wifi");
        this.adhoccfg.put("wifi.essid", ssid);
		this.adhoccfg.put("ip.network", ipaddr);
		int netbits=8;
		if (pieces.length>1) netbits=Integer.parseInt(pieces[1]);
		this.adhoccfg.put("ip.netmask", netSizeToMask(netbits));
		this.adhoccfg.put("ip.gateway", ipaddr);
		this.adhoccfg.put("wifi.interface", this.coretask.getProp("wifi.interface"));
		this.adhoccfg.put("wifi.txpower", txpower);

		// wepEncryption
		if (encEnabled) {
			if (this.interfaceDriver.startsWith("softap")) {
				this.adhoccfg.put("wifi.encryption", "wpa2-psk");
			}
			else {
				this.adhoccfg.put("wifi.encryption", "wep");
			}
			// Storing wep-key
			this.adhoccfg.put("wifi.encryption.key", wepkey);

			// Getting encryption-method if setup-method on auto
			if (wepsetupMethod.equals("auto")) {
				wepsetupMethod = Configuration.getEncryptionAutoMethod(deviceType);
			}
			// Setting setup-mode
			this.adhoccfg.put("wifi.setup", wepsetupMethod);
			// Prepare wpa_supplicant-config if wpa_supplicant selected
			if (wepsetupMethod.equals("wpa_supplicant")) {
				if (this.wpasupplicant.exists() == false) {
					this.installWpaSupplicantConfig();
				}
				Hashtable<String,String> values = new Hashtable<String,String>();
				values.put("ssid", "\""+this.settings.getString("ssidpref", DEFAULT_SSID)+"\"");
				values.put("wep_key0", "\""+this.settings.getString("passphrasepref", DEFAULT_PASSPHRASE)+"\"");
				this.wpasupplicant.write(values);
			}
        }
		else {
			this.adhoccfg.put("wifi.encryption", "open");
			this.adhoccfg.put("wifi.encryption.key", "none");

			// Make sure to remove wpa_supplicant.conf
			if (this.wpasupplicant.exists()) {
				this.wpasupplicant.remove();
			}
		}

		// determine driver wpa_supplicant
		this.adhoccfg.put("wifi.driver", Configuration.getWifiInterfaceDriver(deviceType));

		// writing config-file
		if (!this.adhoccfg.write())
			Log.e(MSG_TAG, "Unable to update adhoc.conf!");

		// blue-up.sh
		this.btcfg.set(lannetwork);
		if (!this.btcfg.write())
			Log.e(MSG_TAG, "Unable to update blue-up.sh!");

		/*
		 * TODO
		 * Need to find a better method to identify if the used device is a
		 * HTC Dream aka T-Mobile G1
		 */
		if (deviceType.equals(Configuration.DEVICE_DREAM)) {
			Hashtable<String,String> values = new Hashtable<String,String>();
			values.put("dot11DesiredSSID", this.settings.getString("ssidpref", DEFAULT_SSID));
			values.put("dot11DesiredChannel", this.settings.getString("channelpref", DEFAULT_CHANNEL));
			this.tiwlan.write(values);
		}

		Log.d(MSG_TAG, "Creation of configuration-files took ==> "+(System.currentTimeMillis()-startStamp)+" milliseconds.");
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
			} else if (!isRunning) {
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
        // Updating all configs
        this.updateConfiguration();

		this.isRunning = true;
		meshManager.setEnabled(true);
		wifiRadio.turnOn();
	}

	// Start/Stop Adhoc
	public void startAdhoc() throws IOException {
		startWifi();

		Editor ed= ServalBatPhoneApplication.this.settings.edit();
		ed.putBoolean("meshRunning",true);
		ed.commit();
    }

	private void stopWifi() throws IOException {
		meshManager.setEnabled(false);
		WifiMode mode = wifiRadio.getCurrentMode();
		switch (mode) {
		case Adhoc:
		case Ap:
			this.wifiRadio.setWiFiMode(WifiMode.Off);
			break;
		}
		this.isRunning = false;
		wifiRadio.checkAlarm();
    }

	public void stopAdhoc() throws IOException {
		stopWifi();

		Editor ed= ServalBatPhoneApplication.this.settings.edit();
		ed.putBoolean("meshRunning",false);
		ed.commit();
    }

    public boolean restartAdhoc() {
    	try{
    		this.stopWifi();
    		this.startWifi();
    		return true;
    	}catch(Exception e){
    		this.displayToastMessage(e.toString());
    		return false;
    	}
    }

	public boolean isRunning() {
		return isRunning;
	}

    public String getAdhocNetworkDevice() {
    	boolean bluetoothPref = this.settings.getBoolean("bluetoothon", false);
        if (bluetoothPref)
			return "bnep";
		else {
			/**
			 * TODO: Quick and ugly workaround for nexus
			 */
			if (Configuration.getWifiInterfaceDriver(this.deviceType).equals(Configuration.DRIVER_SOFTAP_GOG)) {
				return "wl0.1";
			}
			else {
				return this.coretask.getProp("wifi.interface");
			}
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

    public boolean binariesExists() {
		File file = new File(this.coretask.DATA_FILE_PATH
				+ "/asterisk/sbin/asterisk");
    	return file.exists();
    }

    public void installWpaSupplicantConfig() {
    	try {
			this.copyFile(this.coretask.DATA_FILE_PATH+"/conf/wpa_supplicant.conf", "0644", R.raw.wpa_supplicant_conf);
		} catch (IOException e) {
			Log.v("BatPhone",e.toString(),e);
		}
    }

    Handler displayMessageHandler = new Handler(){
        @Override
		public void handleMessage(Message msg) {
       		if (msg.obj != null) {
       			ServalBatPhoneApplication.this.displayToastMessage((String)msg.obj);
       		}
        	super.handleMessage(msg);
        }
    };

	public String getPrimaryNumber() {
		return primaryNumber;
	}

    public void setPrimaryNumber(String newNumber){
		// Create default HLR entry
		try{
			this.meshManager.startDna();

			Dna dna=new Dna();
			dna.addStaticPeer(Inet4Address.getLocalHost());

			if (primarySubscriberId!=null){
				try{
					dna.writeDid(primarySubscriberId, (byte)0, true, newNumber);
				}catch(IOException e){
					// create a new subscriber if dna has forgotten about the old one
					primarySubscriberId=null;
				}
			}

			if (primarySubscriberId==null){
				Log.v("BatPhone","Creating new hlr record for "+newNumber);
				primarySubscriberId=dna.requestNewHLR(newNumber);
				Log.v("BatPhone","Created subscriber "+primarySubscriberId.toString());
				dna.writeLocation(primarySubscriberId, (byte)0, false, "4000@");
			}

			if (!isRunning)
				this.meshManager.stopDna();

			// TODO rework how asterisk determines the caller id.
			this.coretask.writeLinesToFile(this.coretask.DATA_FILE_PATH
					+ "/tmp/myNumber.tmp", newNumber);

			primaryNumber=newNumber;

			Editor ed= ServalBatPhoneApplication.this.settings.edit();
			ed.putString("primaryNumber",primaryNumber);
			ed.putString("primarySubscriber", primarySubscriberId.toString());
			ed.commit();

			Intent intent=new Intent("org.servalproject.SET_PRIMARY");
			intent.putExtra("did", primaryNumber);
			intent.putExtra("sid", primarySubscriberId.toString());
			this.sendStickyBroadcast(intent);

			try{
				String storageState = Environment.getExternalStorageState();
				if (Environment.MEDIA_MOUNTED.equals(storageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)){
					File f=new File(Environment.getExternalStorageDirectory(), "/BatPhone");
					f.mkdirs();
					f=new File(f,"primaryNumber");
					FileOutputStream fs=new FileOutputStream(f);
					fs.write(newNumber.getBytes());
					fs.close();
				}
			}catch(IOException e){

			}
		}catch(Exception e){
			Log.v("BatPhone",e.toString(),e);
			this.displayToastMessage(e.toString());
		}
    }

	public SubscriberId getSubscriberId() {
		return primarySubscriberId;
	}

	private void writeFile(String path, ZipInputStream str) throws IOException {
		File outFile = new File(path);
		outFile.getParentFile().mkdirs();
		BufferedOutputStream out = new BufferedOutputStream(
				new FileOutputStream(outFile), 8 * 1024);
		int len;
		byte buff[] = new byte[1024];
		while ((len = str.read(buff)) > 0) {
			out.write(buff, 0, len);
		}
		out.close();
	}

	private void extractZip(String asset, String folder) throws IOException {
		AssetManager m = this.getAssets();
		ZipInputStream str = new ZipInputStream(m.open(asset));
		{
			while (true) {
				ZipEntry ent = str.getNextEntry();
				if (ent == null)
					break;
				try {
					String filename = ent.getName();
					if (ent.isDirectory()) {
						File dir = new File(folder + "/" + filename + "/");
						if (!dir.mkdirs())
							Log.v("BatPhone", "Failed to create path "
									+ filename);
					} else {
						// try to write the file directly
						writeFile(
								this.coretask.DATA_FILE_PATH + "/" + filename,
								str);

						if (filename.indexOf("bin/") >= 0
								|| filename.indexOf("lib/") >= 0
								|| filename.indexOf("libs/") >= 0
								|| filename.indexOf("conf/") >= 0)
							this.coretask.runCommand("chmod 755 "
									+ this.coretask.DATA_FILE_PATH + "/"
									+ filename);

					}
				} catch (Exception e) {
					Log.v("BatPhone", e.toString(), e);
				}
				str.closeEntry();
			}
			str.close();
		}
	}

	private void createEmptyFolders() {
		// make sure all this folders exist, even if empty
		String[] dirs = { "/tmp", "/htdocs", "/var/run", "/asterisk/var/run",
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

	private String readExistingNumber() {
		if (primaryNumber != null && !primaryNumber.equals(""))
			return primaryNumber;

		// try to get number from phone, probably wont work though...
		TelephonyManager mTelephonyMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String number = mTelephonyMgr.getLine1Number();
		if (number != null && !number.equals(""))
			return number;

		// try to read the last configured number from the sd card
		try {
			String storageState = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(storageState)
					|| Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)) {
				char[] buf = new char[128];
				File f = new File(Environment.getExternalStorageDirectory(),
						"/BatPhone/primaryNumber");

				java.io.FileReader fr = new java.io.FileReader(f);
				fr.read(buf, 0, 128);
				return new String(buf).trim();
			}
		} catch (IOException e) {
		}

		return null;
	}

	private void linkPackages() throws IOException {
		// link installed apk's into the web server's root folder
		PackageManager packageManager = this.getPackageManager();
		List<PackageInfo> packages = packageManager.getInstalledPackages(0);

		for (PackageInfo info : packages) {
			ApplicationInfo appInfo = info.applicationInfo;
			if (appInfo == null
					|| (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
				continue;

			String name = appInfo.name;
			if (name == null) {
				name = appInfo.loadLabel(packageManager).toString();
			}

			this.coretask.runCommand("ln -s \"" + appInfo.sourceDir
					+ "\" \"/data/data/org.servalproject/htdocs/" + name + " "
					+ info.versionName + ".apk\"\n");
		}
	}

    public void installFiles() {
		try{
			extractZip("serval.zip", this.coretask.DATA_FILE_PATH);
			createEmptyFolders();

			ChipsetDetection.getDetection().identifyChipset();

			// stop adhoc if it seems to be running from a previous installation
			if (coretask.isNatEnabled()
					&& coretask.getProp("adhoc.status").equals("running"))
				stopAdhoc();

			// if we just reinstalled, dna might still be running, which could
			// be very confusing...
			this.coretask.killProcess("bin/dna", false);
			this.coretask.killProcess("bin/asterisk", false);

			String number = readExistingNumber();

			// Generate some random data for auto allocating IP / Mac / Phone
			// number
			SecureRandom random = new SecureRandom();
			byte[] bytes = new byte[6];

			random.nextBytes(bytes);

			// Mark MAC as locally administered unicast
			bytes[0] |= 0x2;
			bytes[0] &= 0xfe;

			// Set default IP address from the same random data
			ipaddr = String.format("10.%d.%d.%d", bytes[3] < 0 ? 256 + bytes[3]
					: bytes[3], bytes[4] < 0 ? 256 + bytes[4] : bytes[4],
					bytes[5] < 0 ? 256 + bytes[5] : bytes[5]);

			if (number == null || "".equals(number)) {
				// Pick a random telephone number
				number = String.format("%d%09d", 2 + (bytes[5] & 3),
						Math.abs(random.nextInt()) % 1000000000);
			}

			this.setPrimaryNumber(number);

			// write a new nvram.txt with the mac address in it (for ideos
			// phones)
			try {
				DataInputStream in = new DataInputStream(new FileInputStream(
						"/system/wifi/nvram.txt"));
				FileOutputStream out = new FileOutputStream(
						this.coretask.DATA_FILE_PATH + "/conf/nvram.txt");
				String line;
				while ((line = in.readLine()) != null) {
					if (line.equals("macaddr=00:90:4c:14:43:19"))
						line = "macaddr="
								+ String.format(
										"%02x:%02x:%02x:%02x:%02x:%02x",
										bytes[0], bytes[1], bytes[2], bytes[3],
										bytes[4], bytes[5]);
					;
					out.write(line.getBytes());
					out.write("\n".getBytes());
				}
			} catch (FileNotFoundException e) {
			} catch (Exception e) {
				Log.e("BatPhone", e.toString(), e);
			}

			preferenceEditor.putString("lannetworkpref", ipaddr + "/8");
			preferenceEditor.putString("lastInstalled", version + " "
					+ lastModified);
			preferenceEditor.commit();

			linkPackages();

			this.firstRun=false;
		}catch(Exception e){
			Log.v("BatPhone","File instalation failed",e);
			// Sending message
			ServalBatPhoneApplication.this.displayToastMessage(e.toString());
		}
    }

    /*
     * Update checking. We go to a predefined URL and fetch a properties style file containing
     * information on the update. These properties are:
     *
     * versionCode: An integer, version of the new update, as defined in the manifest. Nothing will
     *              happen unless the update properties version is higher than currently installed.
     * fileName: A string, URL of new update apk. If not supplied then download buttons
     *           will not be shown, but instead just a message and an OK button.
     * message: A string. A yellow-highlighted message to show to the user. Eg for important
     *          info on the update. Optional.
     * title: A string, title of the update dialog. Defaults to "Update available".
     *
     * Only "versionCode" is mandatory.
     */
    public void checkForUpdate() {
    	if (this.isUpdatecDisabled()) {
    		Log.d(MSG_TAG, "Update-checks are disabled!");
    		return;
    	}
    	new Thread(new Runnable(){
			@Override
			public void run(){
				Looper.prepare();
				// Getting Properties
				Properties updateProperties = ServalBatPhoneApplication.this.webserviceTask.queryForProperty(APPLICATION_PROPERTIES_URL);
				if (updateProperties != null && updateProperties.containsKey("versionCode")) {

					int availableVersion = Integer.parseInt(updateProperties.getProperty("versionCode"));
					int installedVersion = ServalBatPhoneApplication.this.getVersionNumber();
					String fileName = updateProperties.getProperty("fileName", "");
					String updateMessage = updateProperties.getProperty("message", "");
					String updateTitle = updateProperties.getProperty("title", "Update available");
					if (availableVersion != installedVersion) {
						Log.d(MSG_TAG, "Installed version '"+installedVersion+"' and available version '"+availableVersion+"' do not match!");
						MainActivity.currentInstance.openUpdateDialog(APPLICATION_DOWNLOAD_URL+fileName,
						    fileName, updateMessage, updateTitle);
					}
				}
				Looper.loop();
			}
    	}).start();
    }

    public void downloadUpdate(final String downloadFileUrl, final String fileName) {
    	new Thread(new Runnable(){
			@Override
			public void run(){
				Message msg = Message.obtain();
            	msg.what = MainActivity.MESSAGE_DOWNLOAD_STARTING;
            	msg.obj = "Downloading update...";
            	MainActivity.currentInstance.viewUpdateHandler.sendMessage(msg);
				ServalBatPhoneApplication.this.webserviceTask.downloadUpdateFile(downloadFileUrl, fileName);
				Intent intent = new Intent(Intent.ACTION_VIEW);
			    intent.setDataAndType(android.net.Uri.fromFile(new File(WebserviceTask.DOWNLOAD_FILEPATH+"/"+fileName)),"application/vnd.android.package-archive");
			    MainActivity.currentInstance.startActivity(intent);
			}
    	}).start();
    }

    private void copyFile(String filename, String permission, int ressource) throws IOException {
    	this.copyFile(filename, ressource);
    	if (this.coretask.chmod(filename, permission) != true) {
    		throw new IOException("Can't change file-permission for '"+filename+"'!");
    	}
    }

    private void copyFile(String filename, int ressource) throws IOException {
    	File outFile = new File(filename);
    	Log.d(MSG_TAG, "Copying file '"+filename+"' ...");
    	InputStream is = this.getResources().openRawResource(ressource);
    	byte buf[] = new byte[1024];
        int len;
    	OutputStream out = new FileOutputStream(outFile);
    	while((len = is.read(buf))>0) {
			out.write(buf,0,len);
		}
    	out.close();
    	is.close();
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
    	// Only supported for the nexusone
    	if (this.deviceType.equals(Configuration.DEVICE_NEXUSONE)
    			&& this.interfaceDriver.startsWith("softap") == false) {
    		return true;
    	}
    	return false;
    }
}
