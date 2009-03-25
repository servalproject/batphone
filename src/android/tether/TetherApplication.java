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

package android.tether;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.tether.data.ClientData;
import android.tether.system.CoreTask;
import android.util.Log;
import android.widget.Toast;

public class TetherApplication extends Application {

	public static final String MSG_TAG = "TETHER -> TetherApplication";
	
	// Client-Connect-Thread
	private Thread clientConnectThread = null;
	
	// WifiManager
	private WifiManager wifiManager;
	
	// PowerManagement
	private PowerManager powerManager = null;
	private PowerManager.WakeLock wakeLock = null;
	
	// ConnectivityManager
	private ConnectivityManager connectivityManager;
	
	// Preferences
	public SharedPreferences settings = null;
	public SharedPreferences.Editor preferenceEditor = null;
	
	// Sync
	public static final Uri CONTENT_URI = Uri.parse("content://sync/settings");
	public static final String KEY = "name";
	public static final String VALUE = "value";
	private static final String[] PROJECTION = { KEY, VALUE };
	public static final String SETTING_LISTEN_FOR_TICKLES = "listen_for_tickles";
    public static final String SETTING_BACKGROUND_DATA = "background_data";		
	
    // Notification
	public NotificationManager notificationManager;
	private Notification notification;
	private int clientNotificationCount = 0;
	
	// Intents
	private PendingIntent mainIntent;
	private PendingIntent accessControlIntent;
    
	// Original States
	private static boolean origWifiState = false;
	public static boolean origTickleState = false;
	public static boolean origBackState = false;	
	
	// Client
	ArrayList<ClientData> clientDataAddList = new ArrayList<ClientData>();
	ArrayList<String> clientMacRemoveList = new ArrayList<String>();
	
	// CoreTask
	public CoreTask coretask = null;
	
	@Override
	public void onCreate() {
		Log.d(MSG_TAG, "Calling onCreate()");
		
		//create CoreTask
		coretask = new CoreTask();
		coretask.setPath(this.getApplicationContext().getFilesDir().getParent());
		Log.d(MSG_TAG, "Current directory is "+this.getApplicationContext().getFilesDir().getParent());
		
        // Check Homedir, or create it
        this.checkDirs(); 
		// Preferences
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		
        // preferenceEditor
        this.preferenceEditor = settings.edit();
		
        // init wifiManager
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE); 
		
        // Powermanagement
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "TETHER_WAKE_LOCK");
        
        // Connectivitymanager
        connectivityManager = (ConnectivityManager) this.getSystemService(CONNECTIVITY_SERVICE);
        
        // Original sync states
		origTickleState = getBoolean(getContentResolver(), SETTING_LISTEN_FOR_TICKLES, true);
		origBackState = getBoolean(getContentResolver(), SETTING_BACKGROUND_DATA, true);
		
        // init notificationManager
        this.notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
    	this.notification = new Notification(R.drawable.start_notification, "Wifi Tether", System.currentTimeMillis());
    	this.mainIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
    	this.accessControlIntent = PendingIntent.getActivity(this, 1, new Intent(this, AccessControlActivity.class), 0);
	}

	@Override
	public void onTerminate() {
		Log.d(MSG_TAG, "Calling onTerminate()");
    	// Stopping Tether
		this.stopTether();
		// Remove all notifications
		this.notificationManager.cancelAll();
	}
	
	// ClientDataList Add
	public synchronized void addClientData(ClientData clientData) {
		this.clientDataAddList.add(clientData);
	}

	public synchronized void removeClientMac(String mac) {
		this.clientMacRemoveList.add(mac);
	}
	
	public synchronized ArrayList<ClientData> getClientDataAddList() {
		ArrayList<ClientData> tmp = this.clientDataAddList;
		this.clientDataAddList = new ArrayList<ClientData>();
		return tmp;
	}
	
	public synchronized ArrayList<String> getClientMacRemoveList() {
		ArrayList<String> tmp = this.clientMacRemoveList;
		this.clientMacRemoveList = new ArrayList<String>();
		return tmp;
	}	
	
	// Start/Stop Tethering
    public int startTether() {
    	/*
    	 * ReturnCodes:
    	 *    0 = All OK, Service started
    	 *    1 = Mobile-Data-Connection not established
    	 *    2 = Fatal error 
    	 */
    	this.acquireWakeLock();
    	boolean connected = false;
    	int checkcounter = 0;
    	while (connected == false && checkcounter <= 5) {
	    	NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
	        if (networkInfo != null) {
		    	if (networkInfo != null && networkInfo.getState().equals(NetworkInfo.State.CONNECTED) == true) {
		    		connected = true;
		    	}
	        }
	        if (connected == false) {
		    	checkcounter++;
	        	try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// nothing
				}
	        }
	        else {
	        	break;
	        }
    	}
        if (connected == false) {
        	return 1;
        }
        // Updating dnsmasq-Config
        this.coretask.updateDnsmasqConf();
    	// Starting service
    	if (this.coretask.runRootCommand(this.coretask.DATA_FILE_PATH+"/bin/tether start "+this.coretask.DATA_FILE_PATH)) {
    		// Starting client-Connect-Thread	
    		
    		if (this.clientConnectThread == null || this.clientConnectThread.isAlive() == false) {
	    		this.clientConnectThread = new Thread(new ClientConnect());
	            this.clientConnectThread.start(); 
    		}
    		return 0;
    	}
    	return 2;
    }
    
    public boolean stopTether() {
    	this.releaseWakeLock();
    	if (this.clientConnectThread != null && this.clientConnectThread.isAlive()) {
    		this.clientConnectThread.interrupt();
    	}
    	boolean stopped = this.coretask.runRootCommand(this.coretask.DATA_FILE_PATH+"/bin/tether stop "+this.coretask.DATA_FILE_PATH);
		this.notificationManager.cancelAll();
    	this.enableWifi();
		this.enableSync();
		return stopped;
    }
	
    public boolean restartTether() {
    	boolean stopped = this.coretask.runRootCommand(this.coretask.DATA_FILE_PATH+"/bin/tether stop "+this.coretask.DATA_FILE_PATH);
    	if (this.clientConnectThread != null && this.clientConnectThread.isAlive()) {
    		this.clientConnectThread.interrupt();
    	}
    	if (stopped != true) {
    		Log.d(MSG_TAG, "Couldn't stop tethering.");
    		return false;
    	}
    	if (this.coretask.runRootCommand(this.coretask.DATA_FILE_PATH+"/bin/tether start "+this.coretask.DATA_FILE_PATH)) {
    		// Starting client-Connect-Thread	
    		if (this.clientConnectThread == null || this.clientConnectThread.isAlive() == false) {
	    		this.clientConnectThread = new Thread(new ClientConnect());
	            this.clientConnectThread.start(); 
    		}
    	}
    	else {
    		Log.d(MSG_TAG, "Couldn't stop tethering.");
    		return false;
    	}
    	return true;
    }
   
	
    //gets user preference on whether wakelock should be disabled during tethering
    public boolean getWakeLock(){
		return this.settings.getBoolean("wakelockpref", false);
	} 
	
    //gets user preference on whether wakelock should be disabled during tethering
    public boolean getSync(){
		return this.settings.getBoolean("syncpref", false);
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
    			Thread.sleep(2000);
    		} catch (InterruptedException e) {
    			// nothing
    		}
    	}
    }
    
    public void enableWifi() {
    	if (origWifiState) {
        	// Waiting for interface-restart
    		try {
    			Thread.sleep(2000);
    		} catch (InterruptedException e) {
    			// nothing
    		}
    		this.wifiManager.setWifiEnabled(true);
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
			if (this.getWakeLock() == false) {
				Log.d(MSG_TAG, "Trying to acquire WakeLock NOW!");
				this.wakeLock.acquire();
			}
		} catch (Exception ex) {
			Log.d(MSG_TAG, "Ups ... an exception happend while trying to acquire WakeLock - Here is what I know: "+ex.getMessage());
		}
	}
	
	// Sync
    public void disableSync() {
    	if (getBoolean(getContentResolver(), SETTING_LISTEN_FOR_TICKLES, true)) {
    		origTickleState = true;
    		putBoolean(getContentResolver(), SETTING_LISTEN_FOR_TICKLES, false);
    	}
        if (getBoolean(getContentResolver(), SETTING_BACKGROUND_DATA, true)) {
        	origBackState = true;
    		putBoolean(getContentResolver(), SETTING_BACKGROUND_DATA, false);
    	}
    }
    
    public void enableSync() {
    	if (origTickleState) {
    		putBoolean(getContentResolver(),SETTING_LISTEN_FOR_TICKLES, true);
    	}
    	if (origBackState) {
    		putBoolean(getContentResolver(), SETTING_BACKGROUND_DATA, true);
    	}
    }
    
    public int getNotificationType() {
		return Integer.parseInt(this.settings.getString("notificationpref", "2"));
    }
    
    // Notification
    public void showStartNotification() {
		notification.flags = Notification.FLAG_ONGOING_EVENT;
    	notification.setLatestEventInfo(this, "Wifi Tether", "Tethering is currently running ...", this.mainIntent);
    	this.notificationManager.notify(-1, this.notification);
    }
    
    Handler clientConnectHandler = new Handler() {
 	   public void handleMessage(Message msg) {
 		    ClientData clientData = (ClientData)msg.obj;
 		   TetherApplication.this.showClientConnectNotification(clientData);
 		    Log.d(MSG_TAG, "New client connected (access-control disabled) ==> "+clientData.getClientName()+" - "+clientData.getMacAddress());
 	   }
    };
    
    public void showClientConnectNotification(ClientData clientData) {
 	   	Notification clientConnectNotification = new Notification(R.drawable.secmedium, "Wifi Tether", System.currentTimeMillis());
 	   	clientConnectNotification.tickerText = clientData.getClientName()+" ("+clientData.getMacAddress()+")";
 	   	if (!PreferenceManager.getDefaultSharedPreferences(this).getString("notifyring", "").equals("")){
 	   		clientConnectNotification.sound = Uri.parse(PreferenceManager.getDefaultSharedPreferences(this).getString("notifyring", ""));
 	   	}
 	   	clientConnectNotification.setLatestEventInfo(this, "Wifi Tether - AC disabled", clientData.getClientName()+" ("+clientData.getMacAddress()+") connected ...", this.accessControlIntent);
 	   	clientConnectNotification.flags = Notification.FLAG_AUTO_CANCEL;
 	   	this.notificationManager.notify(this.clientNotificationCount, clientConnectNotification);
 	   	this.clientNotificationCount++;
    }
    
    Handler clientUnauthConnectHandler = new Handler() {
 	   public void handleMessage(Message msg) {
 		    ClientData clientData = (ClientData)msg.obj;
 		    TetherApplication.this.showClientUnauthConnectNotification(clientData);
 		    Log.d(MSG_TAG, "New client connected which is NOT authorized ==> "+clientData.getClientName()+" - "+clientData.getMacAddress());
 	   }
    };
    
    public void showClientUnauthConnectNotification(ClientData clientData) {
 	   	Notification clientConnectNotification = new Notification(R.drawable.seclow, "Wifi Tether", System.currentTimeMillis());
 	   	clientConnectNotification.tickerText = clientData.getClientName()+" ("+clientData.getMacAddress()+")";
 	   	if (!PreferenceManager.getDefaultSharedPreferences(this).getString("notifyring", "").equals("")){
 	   		clientConnectNotification.sound = Uri.parse(PreferenceManager.getDefaultSharedPreferences(this).getString("notifyring", ""));
 	   	}
 	   	clientConnectNotification.setLatestEventInfo(this, "Wifi Tether - Unauthorized", clientData.getClientName()+" ("+clientData.getMacAddress()+") connected ...", this.accessControlIntent);
 	   	clientConnectNotification.flags = Notification.FLAG_AUTO_CANCEL;
 	   	this.notificationManager.notify(this.clientNotificationCount, clientConnectNotification);
 	   	this.clientNotificationCount++;
    }
    
    Handler clientAuthConnectHandler = new Handler() {
 	   public void handleMessage(Message msg) {
 		    ClientData clientData = (ClientData)msg.obj;
 		   TetherApplication.this.showClientAuthConnectNotification(clientData);
 		    Log.d(MSG_TAG, "New client connected which IS authorized ==> "+clientData.getClientName()+" - "+clientData.getMacAddress());
 	   }
    };
    
    public void showClientAuthConnectNotification(ClientData clientData) {
 	   	Notification clientConnectNotification = new Notification(R.drawable.sechigh, "Wifi Tether", System.currentTimeMillis());
 	   	clientConnectNotification.tickerText = clientData.getClientName()+" ("+clientData.getMacAddress()+")";
 	   	if (!PreferenceManager.getDefaultSharedPreferences(this).getString("notifyring", "").equals("")){
 	   		clientConnectNotification.sound = Uri.parse(PreferenceManager.getDefaultSharedPreferences(this).getString("notifyring", ""));
 	   	}
 	   	clientConnectNotification.setLatestEventInfo(this, "Wifi Tether - Authorized", clientData.getClientName()+" ("+clientData.getMacAddress()+") connected ...", this.accessControlIntent);
 	   	clientConnectNotification.flags = Notification.FLAG_AUTO_CANCEL;
 	   	this.notificationManager.notify(this.clientNotificationCount, clientConnectNotification);
 	   	this.clientNotificationCount++;
    }      
    
    // Binary install
    public boolean binariesExists() {
    	File file = new File(this.coretask.DATA_FILE_PATH+"/bin/tether");
    	if (file.exists()) {
    		return true;
    	}
    	return false;
    }
    
    public void installBinaries() {
    	List<String> filenames = new ArrayList<String>();
    	// tether
    	this.copyBinary(this.coretask.DATA_FILE_PATH+"/bin/tether", R.raw.tether);
    	filenames.add("tether");
    	// dnsmasq
    	this.copyBinary(this.coretask.DATA_FILE_PATH+"/bin/dnsmasq", R.raw.dnsmasq);
    	filenames.add("dnsmasq");
    	// iptables
    	this.copyBinary(this.coretask.DATA_FILE_PATH+"/bin/iptables", R.raw.iptables);
    	filenames.add("iptables");
    	try {
			this.coretask.chmodBin(filenames);
		} catch (Exception e) {
			this.displayToastMessage("Unable to change permission on binary files!");
		}
    	// dnsmasq.conf
    	this.copyBinary(this.coretask.DATA_FILE_PATH+"/conf/dnsmasq.conf", R.raw.dnsmasq_conf);
    	// tiwlan.ini
    	this.copyBinary(this.coretask.DATA_FILE_PATH+"/conf/tiwlan.ini", R.raw.tiwlan_ini);
    	this.displayToastMessage("Binaries and config-files installed!");
    }
    
    private void copyBinary(String filename, int resource) {
    	File outFile = new File(filename);
    	InputStream is = this.getResources().openRawResource(resource);
    	byte buf[]=new byte[1024];
        int len;
        try {
        	OutputStream out = new FileOutputStream(outFile);
        	while((len = is.read(buf))>0) {
				out.write(buf,0,len);
			}
        	out.close();
        	is.close();
		} catch (IOException e) {
			this.displayToastMessage("Couldn't install file - "+filename+"!");
		}
    }
    

    private void checkDirs() {
    	File dir = new File(this.coretask.DATA_FILE_PATH);
    	if (dir.exists() == false) {
    			this.displayToastMessage("Application data-dir does not exist!");
    	}
    	else {
	    	dir = new File(this.coretask.DATA_FILE_PATH+"/bin");
	    	if (dir.exists() == false) {
	    		if (!dir.mkdir()) {
	    			this.displayToastMessage("Couldn't create bin-directory!");
	    		}
	    	}
	    	dir = new File(this.coretask.DATA_FILE_PATH+"/var");
	    	if (dir.exists() == false) {
	    		if (!dir.mkdir()) {
	    			this.displayToastMessage("Couldn't create var-directory!");
	    		}
	    	}
	    	dir = new File(this.coretask.DATA_FILE_PATH+"/conf");
	    	if (dir.exists() == false) {
	    		if (!dir.mkdir()) {
	    			this.displayToastMessage("Couldn't create conf-directory!");
	    		}
	    	}   			
    	}
    }    
    
    // Display Toast-Message
	public void displayToastMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}
    
    // Helpers
    public boolean getBoolean(ContentResolver contentResolver,
            String name, boolean def) {
        Cursor cursor = contentResolver.query(
            CONTENT_URI,
            PROJECTION,
            KEY + "=?",
            new String[] { name },
            null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
            	Log.d(MSG_TAG,cursor.getString(0));
                return Boolean.parseBoolean(cursor.getString(1));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return def;
    }
    
    public void putBoolean(ContentResolver contentResolver, String name, boolean val) {
        ContentValues values = new ContentValues();
        values.put(KEY, name);
        values.put(VALUE, Boolean.toString(val));
        // this insert is translated into an update by the underlying Sync provider
        contentResolver.insert(CONTENT_URI, values);
    }
    
    
    class ClientConnect implements Runnable {

        private ArrayList<String> knownWhitelists = new ArrayList<String>();
        private ArrayList<String> knownLeases = new ArrayList<String>();
        private Hashtable<String, ClientData> currentLeases = new Hashtable<String, ClientData>();
        private long timestampLeasefile = -1;
        private long timestampWhitelistfile = -1;

        // @Override
        public void run() {
        	Looper.prepare();
            while (!Thread.currentThread().isInterrupted()) {
            	Log.d(MSG_TAG, "Checking for new clients ... ");
            	// Notification-Type
            	int notificationType = TetherApplication.this.getNotificationType();
            	// Access-Control activated
            	boolean accessControlActive = TetherApplication.this.coretask.whitelistExists();
		        // Checking if Access-Control is activated
		        if (accessControlActive) {
                    // Checking whitelistfile
                    long currentTimestampWhitelistFile = TetherApplication.this.coretask.getModifiedDate(TetherApplication.this.coretask.DATA_FILE_PATH + "/conf/whitelist_mac.conf");
                    if (this.timestampWhitelistfile != currentTimestampWhitelistFile) {
                        try {
                            knownWhitelists = TetherApplication.this.coretask.getWhitelist();
                        } catch (Exception e) {
                            Log.d(MSG_TAG, "Unexpected error detected - Here is what I know: " + e.getMessage());
                            e.printStackTrace();
                        }
                        this.timestampWhitelistfile = currentTimestampWhitelistFile;
                    }
		        }
                // Checking leasefile
                long currentTimestampLeaseFile = TetherApplication.this.coretask.getModifiedDate(TetherApplication.this.coretask.DATA_FILE_PATH + "/var/dnsmasq.leases");
                if (this.timestampLeasefile != currentTimestampLeaseFile) {
                    try {
                    	// Getting current dns-leases
                        this.currentLeases = TetherApplication.this.coretask.getLeases();
                        
                        // Cleaning-up knownLeases after a disconnect (dhcp-release)
                        for (String lease : this.knownLeases) {
                            if (this.currentLeases.containsKey(lease) == false) {
                            	Log.d(MSG_TAG, "Removing '"+lease+"' from known-leases!");
                                this.knownLeases.remove(lease);
                            	
                                notifyActivity();
                            	TetherApplication.this.removeClientMac(lease);
                            }
                        }
                        
                        Enumeration<String> leases = this.currentLeases.keys();
                        while (leases.hasMoreElements()) {
                            String mac = leases.nextElement();
                            Log.d(MSG_TAG, "Mac-Address: '"+mac+"' - Known Whitelist: "+knownWhitelists.contains(mac)+" - Known Lease: "+knownLeases.contains(mac));
                            if (knownLeases.contains(mac) == false) {
	                            if (knownWhitelists.contains(mac) == false) {
	                            	// AddClientData to TetherApplication-Class for AccessControlActivity
	                            	TetherApplication.this.addClientData(this.currentLeases.get(mac));
	                            	
	                            	if (accessControlActive) {
	                            		if (notificationType == 1 || notificationType == 2) {
	                            			this.sendUnAuthClientMessage(this.currentLeases.get(mac));
	                            		}
	                            	}
	                            	else {
	                            		if (notificationType == 2) {
	                            			this.sendClientMessage(this.currentLeases.get(mac));
	                            		}
	                            	}
	                                this.knownLeases.add(mac);
	                            } else if (knownWhitelists.contains(mac) == true) {
	                            	// AddClientData to TetherApplication-Class for AccessControlActivity
	                            	ClientData clientData = this.currentLeases.get(mac);
	                            	clientData.setAccessAllowed(true);
	                            	TetherApplication.this.addClientData(clientData);
	                            	
	                                if (notificationType == 2) {
	                                    this.sendAuthClientMessage(this.currentLeases.get(mac));
	                                    this.knownLeases.add(mac);
	                                }
	                            }
	                            notifyActivity();
                            }
                        }
                        this.timestampLeasefile = currentTimestampLeaseFile;
                    } catch (Exception e) {
                        Log.d(MSG_TAG, "Unexpected error detected - Here is what I know: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void notifyActivity(){
        	if (AccessControlActivity.currentInstance != null){
        		AccessControlActivity.currentInstance.clientConnectHandler.sendMessage(new Message());
        	}
        }
        
        private void sendClientMessage(ClientData clientData) {
            Message m = new Message();
            m.obj = clientData;
            TetherApplication.this.clientConnectHandler.sendMessage(m);
        }

        private void sendUnAuthClientMessage(ClientData clientData) {
            Message m = new Message();
            m.obj = clientData;
            TetherApplication.this.clientUnauthConnectHandler.sendMessage(m);
        }

        private void sendAuthClientMessage(ClientData clientData) {
            Message m = new Message();
            m.obj = clientData;
            TetherApplication.this.clientAuthConnectHandler.sendMessage(m);
        }
    }
}
