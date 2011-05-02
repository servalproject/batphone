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
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.servalproject.dna.SubscriberId;
import org.servalproject.system.BluetoothService;
import org.servalproject.system.Configuration;
import org.servalproject.system.CoreTask;
import org.servalproject.system.WebserviceTask;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import org.servalproject.R;
import org.sipdroid.sipua.ui.Receiver;
import org.zoolu.net.IpAddress;

import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;

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
	public boolean meshRunning=false;
	
	StatusNotification statusNotification;

	// WifiManager
	private WifiManager wifiManager;
	
	// PowerManagement
	private PowerManager powerManager = null;
	private PowerManager.WakeLock wakeLock = null;

	// Bluetooth
	BluetoothService bluetoothService = null;
	
	// Preferences
	public SharedPreferences settings = null;
	public SharedPreferences.Editor preferenceEditor = null;
	
	// Original States
	private static boolean origWifiState = false;
	private static boolean origBluetoothState = false;
	
	// Supplicant
	public CoreTask.WpaSupplicant wpasupplicant = null;
	// TiWlan.conf
	public CoreTask.TiWlanConf tiwlan = null;
	// adhoc.conf
	public CoreTask.AdhocConfig adhoccfg = null;
	// blue-up.sh
	public CoreTask.BluetoothConfig btcfg = null;
	
	// CoreTask
	public CoreTask coretask = null;
	
	// WebserviceTask
	public WebserviceTask webserviceTask = null;
	
	// Update Url, Diverted to Serval BatPhone versions
	private static final String APPLICATION_PROPERTIES_URL = "http://servalproject.org/batphone/android/application.properties";
	private static final String APPLICATION_DOWNLOAD_URL = "http://servalproject/batphone/files/";
	
	public static String version="Unknown";
	
	// adhoc allocated ip address
    private String ipaddr="";
    @SuppressWarnings("unused")
	private SubscriberId primarySubscriberId=null;
    private String primaryNumber="";
    
	@Override
	public void onCreate() {
		Log.d(MSG_TAG, "Calling onCreate()");
		
		try {
			version=getPackageManager()
						   .getPackageInfo(getPackageName(), 0)
						   .versionName;
		} catch (NameNotFoundException e) {
			Log.v("BatPhone",e.toString(),e);
		}
		
		//create CoreTask
		this.coretask = new CoreTask();
		this.coretask.setPath(this.getApplicationContext().getFilesDir().getParent());
		Log.d(MSG_TAG, "Current directory is "+this.getApplicationContext().getFilesDir().getParent());

		//create WebserviceTask
		this.webserviceTask = new WebserviceTask();
		
        // Set device-information
        this.deviceType = Configuration.getDeviceType();
        this.interfaceDriver = Configuration.getWifiInterfaceDriver(this.deviceType);
        
        this.statusNotification=new StatusNotification(this);
        
        // Preferences
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		this.firstRun = settings.getBoolean("first_run", true);
		
		String subScrbr = settings.getString("primarySubscriber", "");
		if (subScrbr.length()==64){
			primarySubscriberId=new SubscriberId(subScrbr);
		}
		
		try {
			char [] buf = new char[128];

			java.io.FileReader f = new java.io.FileReader("/data/data/org.servalproject/tmp/myNumber.tmp");
			f.read(buf,0,128);
			this.primaryNumber=new String(buf).trim();
		} catch (Exception e) {
			this.primaryNumber = settings.getString("primaryNumber", ""); 
		}
		ipaddr=settings.getString("lannetworkpref",ipaddr+"/8");
		if (ipaddr.indexOf('/')>0) ipaddr = ipaddr.substring(0, ipaddr.indexOf('/'));
		
        // preferenceEditor
        this.preferenceEditor = settings.edit();
		
        // init wifiManager
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE); 
        
        // Supplicant config
        this.wpasupplicant = this.coretask.new WpaSupplicant();
        
        // tiwlan.conf
        this.tiwlan = this.coretask.new TiWlanConf();
        
        // adhoc.cfg
        this.adhoccfg = this.coretask.new AdhocConfig();
        this.adhoccfg.read();

    	// blue-up.sh
    	this.btcfg = this.coretask.new BluetoothConfig();        
        
        // Powermanagement
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ADHOC_WAKE_LOCK");

        // Bluetooth-Service
        this.bluetoothService = BluetoothService.getInstance();
        this.bluetoothService.setApplication(this);
        
		try {
			if (coretask.isNatEnabled()){
				// Checking, if "wired adhoc" is currently running
				String adhocMode = coretask.getProp("adhoc.mode");
				String adhocStatus = coretask.getProp("adhoc.status");
				if (adhocStatus.equals("running")) {
					if (!(adhocMode.equals("wifi") || adhocMode.equals("bt"))) {
						throw new IllegalStateException("Wired-tethering seems to be running at the moment. Please disable it first!");
					}
				}
				
				// Checking, if cyanogens usb-tether is currently running
				String tetherStatus = coretask.getProp("tethering.enabled");
				if  (tetherStatus.equals("1")) {
					throw new IllegalStateException("USB-tethering seems to be running at the moment. Please disable it first: Settings -> Wireless & network setting -> Internet tethering.");
				}
				
				if (!coretask.isProcessRunning("bin/batmand"))
					throw new IllegalStateException("batman is not running");
				
				if (!coretask.isProcessRunning("bin/dna"))
					throw new IllegalStateException("dna is not running");
				
				if (!coretask.isProcessRunning("lib/ld-linux.so.3"))
					throw new IllegalStateException("asterisk is not running");
				
				Receiver.engine(this).StartEngine();
				
				statusNotification.showStatusNotification();
				meshRunning=true;
			}
		} catch (Exception e) {
			Log.v("Batphone",e.toString(),e);
			displayToastMessage(e.toString());
		}
        
	}

	@Override
	public void onTerminate() {
		Log.d(MSG_TAG, "Calling onTerminate()");
    	// Stopping Adhoc
		this.stopAdhoc();
		this.statusNotification.hideStatusNotification();
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
	
	public boolean setBluetoothState(boolean enabled) {
		boolean connected = false;
		if (enabled == false) {
			this.bluetoothService.stopBluetooth();
			return false;
		}
		origBluetoothState = this.bluetoothService.isBluetoothEnabled();
		if (origBluetoothState == false) {
			connected = this.bluetoothService.startBluetooth();
			if (connected == false) {
				Log.d(MSG_TAG, "Enable bluetooth failed");
			}
		} else {
			connected = true;
		}
		return connected;
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
        String ipaddr = lannetwork.split("/")[0];
        this.adhoccfg.read();
		this.adhoccfg.put("device.type", deviceType);
        this.adhoccfg.put("adhoc.mode", bluetoothPref ? "bt" : "wifi");
        this.adhoccfg.put("wifi.essid", ssid);
		this.adhoccfg.put("ip.network", lannetwork.split("/")[0]);
        String[] pieces = lannetwork.split("/");
		this.adhoccfg.put("ip.network", pieces[0]);
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
		if (this.adhoccfg.write() == false) {
			Log.e(MSG_TAG, "Unable to update adhoc.conf!");
		}
		
		// blue-up.sh
		this.btcfg.set(lannetwork);
		if (this.btcfg.write() == false) {
			Log.e(MSG_TAG, "Unable to update blue-up.sh!");
		}
		
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
	
	private void waitForIp() throws SocketException{
		// wait for the configured IP address to come up before continuing
		int tries=0;
		String lannetwork = this.settings.getString("lannetworkpref", DEFAULT_LANNETWORK);
		
		while(tries<=100){
			
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();

				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();

					if (!inetAddress.isLoopbackAddress()) { 
						String addr = inetAddress.getHostAddress().toString();
						if (addr.equals(lannetwork)) {
							
							IpAddress.localIpAddress=lannetwork;
							return;
						}
					}
				}
			}
			// Take a small nap before trying again
			tries++;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}

	}
	
	private void waitForProcess(String processName){
		int tries=0;
		while(tries<=100){
			try {
				if (coretask.isProcessRunning(processName)) 
					return;
			} catch (Exception e) {
			}
			
			tries++;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}
	
	// Start/Stop Adhoc
    public boolean startAdhoc(Handler trafficHandler) {

        boolean bluetoothPref = this.settings.getBoolean("bluetoothon", false);
        boolean bluetoothWifi = this.settings.getBoolean("bluetoothkeepwifi", false);
        
        // Updating all configs
        this.updateConfiguration();

        if (bluetoothPref) {
    		if (setBluetoothState(true) == false){
    			return false;
    		}
			if (bluetoothWifi == false) {
	        	this.disableWifi();
			}
        } 
        else {
        	this.disableWifi();
        }

    	// Starting service
        try {
			this.coretask.runRootCommand(this.coretask.DATA_FILE_PATH+"/bin/adhoc start 1");
			
			this.waitForIp();
			this.waitForProcess("bin/batmand");
			this.waitForProcess("bin/dna");
			this.waitForProcess("lib/ld-linux.so.3");
			Receiver.engine(this).StartEngine();
			
			this.statusNotification.showStatusNotification();
	    	
			// Acquire Wakelock
			this.acquireWakeLock();
			meshRunning=true;
			
			return true;
		} catch (Exception e) {
			Log.v("BatPhone",e.toString(),e);
			this.displayMessage(e.toString());
	    	return false;
		}
    }
    
    public boolean stopAdhoc() {
    	this.releaseWakeLock();

		this.statusNotification.hideStatusNotification();
		Receiver.engine(this).halt();
		
        boolean bluetoothPref = this.settings.getBoolean("bluetoothon", false);
        boolean bluetoothWifi = this.settings.getBoolean("bluetoothkeepwifi", false);
        boolean stopped=false;
    	try {
			this.coretask.runRootCommand(this.coretask.DATA_FILE_PATH+"/bin/adhoc stop 1");
			stopped=true;
		} catch (Exception e) {
    		this.displayToastMessage(e.toString());
		}
		
		// Put WiFi and Bluetooth back, if applicable.
		if (bluetoothPref && origBluetoothState == false) {
			setBluetoothState(false);
		}
		if (bluetoothPref == false || bluetoothWifi == false) {
			this.enableWifi();
		}
		meshRunning=false;
		
		return stopped;
    }
	
    public boolean restartAdhoc() {
    	try{
    		this.coretask.runRootCommand(this.coretask.DATA_FILE_PATH+"/bin/adhoc stop 1");
    		this.statusNotification.hideStatusNotification();

    		boolean bluetoothPref = this.settings.getBoolean("bluetoothon", false);
    		boolean bluetoothWifi = this.settings.getBoolean("bluetoothkeepwifi", false);

    		// Updating all configs
    		this.updateConfiguration();       

    		if (bluetoothPref) {
    			if (setBluetoothState(true) == false){
    				return false;
    			}
    			if (bluetoothWifi == false) {
    				this.disableWifi();
    			}
    		} 
    		else {
    			if (origBluetoothState == false) {
    				setBluetoothState(false);
    			}
    			this.disableWifi();
    		}

    		// Starting service
    		this.coretask.runRootCommand(this.coretask.DATA_FILE_PATH+"/bin/adhoc start 1");

			this.waitForIp();
    		this.statusNotification.showStatusNotification();
    		return true;
    	}catch(Exception e){
    		this.displayToastMessage(e.toString());
    		return false;
    	}
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
    
    // gets user preference on whether wakelock should be disabled during adhoc
    public boolean isWakeLockEnabled(){
		return this.settings.getBoolean("wakelockpref", true);
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

    // Wifi
    public void disableWifi() {
    	if (this.wifiManager.isWifiEnabled()) {
    		origWifiState = true;
    		this.wifiManager.setWifiEnabled(false);
    		Log.d(MSG_TAG, "Wifi disabled!");
        	// Waiting for interface-shutdown
    		try {
    			Thread.sleep(5000);
    		} catch (InterruptedException e) {
    			// nothing
    		}
    	}
    }
    
    public void enableWifi() {
    	if (origWifiState) {
        	// Waiting for interface-restart
    		this.wifiManager.setWifiEnabled(true);
    		try {
    			Thread.sleep(5000);
    		} catch (InterruptedException e) {
    			// nothing
    		}
    		Log.d(MSG_TAG, "Wifi started!");
    	}
    }
    
    // WakeLock
	public void releaseWakeLock() {
		try {
			if(this.wakeLock != null && this.wakeLock.isHeld()) {
				Log.d(MSG_TAG, "Trying to release WakeLock NOW!");
				this.wakeLock.release();
			}
		} catch (Exception ex) {
			Log.d(MSG_TAG, "Ups ... an exception happend while trying to release WakeLock - Here is what I know: "+ex.getMessage());
		}
	}
    
	public void acquireWakeLock() {
		try {
			if (this.isWakeLockEnabled() == true) {
				Log.d(MSG_TAG, "Trying to acquire WakeLock NOW!");
				this.wakeLock.acquire();
			}
		} catch (Exception ex) {
			Log.d(MSG_TAG, "Ups ... an exception happend while trying to acquire WakeLock - Here is what I know: "+ex.getMessage());
		}
	}
    
    public int getNotificationType() {
		return Integer.parseInt(this.settings.getString("notificationpref", "2"));
    }
    
    public boolean binariesExists() {
    	File file = new File(this.coretask.DATA_FILE_PATH+"/sbin/asterisk");
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
			this.coretask.runRootCommand(ServalBatPhoneApplication.this.coretask.DATA_FILE_PATH+"/bin/set_number "+newNumber+" "+ipaddr);
			
			/* use java dna, after dna has been fixed
			 * also need better handling of dna startup.
			Dna dna=new Dna();
			dna.addPeer(new InetSocketAddress(Inet4Address.getLocalHost(), 4110));
			if (primarySubscriberId==null){
				primarySubscriberId=dna.requestNewHLR(newNumber);
				dna.writeLocation(primarySubscriberId, (byte)0, "4000@");
			}else
				dna.writeDid(primarySubscriberId, (byte)0, newNumber);
			 */
			
			primaryNumber=newNumber;
			Editor ed= ServalBatPhoneApplication.this.settings.edit();
			ed.putString("primaryNumber",primaryNumber);
			ed.commit();
		}catch(Exception e){
			Log.v("BatPhone","Failed to set phone number",e);
			this.displayMessage("Failed to set number");
		}
    }
    
    public void installFiles() {
		try{
			// make sure all this folders exist
			String[] dirs = { "/bin", "/var", "/conf", "/tmp", "/var/run",
					"/var/log", 
					"/var/log/asterisk", "/var/log/asterisk/cdr-csv",
					"/var/log/asterisk/cdr-custom",
					"/var/spool", "/var/spool/asterisk",
					"/var/spool/asterisk/dictate", 
					"/var/spool/asterisk/meetme", "/var/spool/asterisk/monitor", 
					"/var/spool/asterisk/system", "/var/spool/asterisk/tmp", 
					"/var/spool/asterisk/voicemail", 
					"/voiceSignature"};
			
    		for (String dirname : dirs) {
    			File dir = new File(this.coretask.DATA_FILE_PATH + dirname);
    			dir.mkdirs();
    		}
    		
			OutputStreamWriter installScript = new OutputStreamWriter(new BufferedOutputStream(this.openFileOutput("installScript",0),8*1024));
			installScript.write("#!/system/bin/sh\n");
			installScript.write("busybox chown -R `busybox ls -ld "+this.coretask.DATA_FILE_PATH+" | busybox awk '{ printf(\"%s:%s\",$3,$4);}'` "+this.coretask.DATA_FILE_PATH+"\n");
			installScript.write("mkdir "+this.coretask.DATA_FILE_PATH+"/lib/asterisk\n");
			installScript.write("mkdir "+this.coretask.DATA_FILE_PATH+"/lib/asterisk/modules\n");
			AssetManager m=this.getAssets();
			ZipInputStream str=new ZipInputStream(m.open("serval.zip"));
			int i=0;
			while(true){
				ZipEntry ent=str.getNextEntry();
				if (ent==null) break;
				try{
					i++;
					if (ent.isDirectory()){
						File dir=new File(this.coretask.DATA_FILE_PATH+"/"+ent.getName()+"/x");
						if (!dir.mkdirs())
							Log.v("BatPhone","Failed to create path "+ent.getName());
					}else{
						File outFile=new File(this.coretask.DATA_FILE_PATH+"/files/", Integer.toString(i));
						installScript.write("mv "+outFile.getAbsolutePath()+" "+
								this.coretask.DATA_FILE_PATH+"/"+ent.getName()+"\n");
						BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile),8*1024);
						int len;
						byte buff[]=new byte[1024];
				    	while((len = str.read(buff))>0) {
							out.write(buff,0,len);
						}
				    	out.close();
					}
				}catch(Exception e){
					Log.v("BatPhone",e.toString(),e);
				}
				str.closeEntry();
			}
			str.close();
			
			installScript.write("busybox chmod 755 "+this.coretask.DATA_FILE_PATH+"/*bin/* "+this.coretask.DATA_FILE_PATH+"/lib/* "+this.coretask.DATA_FILE_PATH+"/lib/asterisk/modules "+this.coretask.DATA_FILE_PATH+"/conf\n");
			
			// Create nvram.txt with random MAC address for those platforms that need it.
			String mac = new String();
			SecureRandom random = new SecureRandom();
			byte[] bytes = new byte[6];
			
			random.nextBytes(bytes);
			
			/* Mark MAC as locally administered unicast */
			bytes[0]|=0x2; bytes[0]&=0xfe;
			
			// Render MAC address 
			mac=String.format("%02x:%02x:%02x:%02x:%02x:%02x", bytes[0],bytes[1],bytes[2],bytes[3],bytes[4],bytes[5]);
			
			// Set default IP address from the same random data
			ipaddr=String.format("10.%d.%d.%d", 
							bytes[3]<0?256+bytes[3]:bytes[3],
							bytes[4]<0?256+bytes[4]:bytes[4],
							bytes[5]<0?256+bytes[5]:bytes[5]
							);
			
			String number=primaryNumber;
			if (number==null||"".equals(number)){
				// get number from phone
				TelephonyManager mTelephonyMgr=(TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE); 
				number=mTelephonyMgr.getLine1Number();
			}

			if (number==null||"".equals(number)){
				// Pick initial telephone number
				number = String.format("%d%09d",
						2+(bytes[5]&3),Math.abs(random.nextInt())%1000000000);
			}
			
			installScript.write(this.coretask.DATA_FILE_PATH+"/bin/set_number "+number+" "+ipaddr+"\n");
			installScript.close();
			
			this.coretask.chmod(this.coretask.DATA_FILE_PATH+"/files/installScript", "755");
			this.coretask.runRootCommand(this.coretask.DATA_FILE_PATH+"/files/installScript");
			
			primaryNumber=number;
			Editor ed= ServalBatPhoneApplication.this.settings.edit();
			ed.putString("primaryNumber",primaryNumber);
			ed.putString("lannetworkpref",ipaddr+"/8");
			ed.commit();
			primaryNumber=number;
			
			BufferedReader a=new BufferedReader(new FileReader(this.coretask.DATA_FILE_PATH+"/conf/nvram.top"),256);
		    FileWriter out =new FileWriter("/data/data/org.servalproject/conf/nvram.txt");
			String line=null;
			String ls = System.getProperty("line.separator");
			while( ( line = a.readLine() ) != null ) {
		    	out.append( ls ).append( line );				        
		    }
			a.close();
		    out.append(mac).append( ls);
			BufferedReader b=new BufferedReader(new FileReader(this.coretask.DATA_FILE_PATH+"/conf/nvram.end"),256);
		    while( ( line = b.readLine() ) != null ) {
		    	out.append( line ).append( ls );
		    }
		    b.close();
		    out.flush();
		    out.close();
		    
		}catch(Exception e){
			Log.v("BatPhone","File instalation failed",e);
			// Sending message
			ServalBatPhoneApplication.this.displayMessage(e.toString());
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
    
    public void restartSecuredWifi() {
    	try {
			if (this.coretask.isNatEnabled() && this.coretask.isProcessRunning("bin/dnsmasq")) {
		    	Log.d(MSG_TAG, "Restarting iptables for access-control-changes!");
				this.coretask.runRootCommand(this.coretask.DATA_FILE_PATH+"/bin/adhoc restartsecwifi 1");
			}
		} catch (Exception e) {
			this.displayToastMessage(e.toString());
		}
    }
    
    public void displayMessage(String message){
		Message msg = new Message();
		msg.obj = message;
		ServalBatPhoneApplication.this.displayMessageHandler.sendMessage(msg);
    }
    
    // Display Toast-Message
	public void displayToastMessage(String message) {
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
