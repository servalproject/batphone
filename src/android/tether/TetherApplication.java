/**
 *  This software is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Use this application at your own risk.
 *
 *  Copyright (c) 2009 by Harald Mueller and Seth Lemons.
 */

package android.tether;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

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
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.tether.data.ClientData;
import android.tether.system.CoreTask;
import android.util.Log;

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
	SharedPreferences settings = null;
	
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
	
	@Override
	public void onCreate() {
		Log.d(MSG_TAG, "Calling onCreate()");
		// Preferences
		settings = PreferenceManager.getDefaultSharedPreferences(this);
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
        CoreTask.updateDnsmasqConf();
    	// Starting service
    	if (CoreTask.runRootCommand(CoreTask.DATA_FILE_PATH+"/bin/tether start")) {
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
    	boolean stopped = CoreTask.runRootCommand(CoreTask.DATA_FILE_PATH+"/bin/tether stop");
		this.notificationManager.cancelAll();
    	this.enableWifi();
		this.enableSync();
		return stopped;
    }
	
	
    //gets user preference on whether wakelock should be disabled during tethering
    public boolean getWakeLock(){
		return this.settings.getBoolean("wakelockpref", false);
	} 
	
    //gets user preference on whether wakelock should be disabled during tethering
    public boolean getSync(){
		return this.settings.getBoolean("syncpref", false);
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
 	   	clientConnectNotification.sound = Uri.parse(PreferenceManager.getDefaultSharedPreferences(this).getString("notifyring", ""));
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
 	   	clientConnectNotification.sound = Uri.parse(PreferenceManager.getDefaultSharedPreferences(this).getString("notifyring", ""));
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
 	   	clientConnectNotification.sound = Uri.parse(PreferenceManager.getDefaultSharedPreferences(this).getString("notifyring", ""));
 	   	clientConnectNotification.setLatestEventInfo(this, "Wifi Tether - Authorized", clientData.getClientName()+" ("+clientData.getMacAddress()+") connected ...", this.accessControlIntent);
 	   	clientConnectNotification.flags = Notification.FLAG_AUTO_CANCEL;
 	   	this.notificationManager.notify(this.clientNotificationCount, clientConnectNotification);
 	   	this.clientNotificationCount++;
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
    
    // Client-Connect-Thread
    class ClientConnect implements Runnable {

        private ArrayList<String> knownWhitelists = new ArrayList<String>();
        private ArrayList<String> knownLeases = new ArrayList<String>();
        private Hashtable<String, ClientData> currentLeases = new Hashtable<String, ClientData>();
        private long timestampLeasefile = -1;
        private long timestampWhitelistfile = -1;

        // @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                int notificationType = TetherApplication.this.getNotificationType();
                if (notificationType != 0) {
                    Log.d(MSG_TAG, "Checking for new clients ... ");
                    // Checking if Access-Control is activated
                    if (CoreTask.fileExists(CoreTask.DATA_FILE_PATH + "/conf/whitelist_mac.conf")) {
                        // Checking whitelistfile
                        long currentTimestampWhitelistFile = CoreTask.getModifiedDate(CoreTask.DATA_FILE_PATH + "/conf/whitelist_mac.conf");
                        if (this.timestampWhitelistfile != currentTimestampWhitelistFile) {
                            try {
                                knownWhitelists = CoreTask.getWhitelist();
                            } catch (Exception e) {
                                Log.d(MSG_TAG, "Unexpected error detected - Here is what I know: " + e.getMessage());
                                e.printStackTrace();
                            }
                            this.timestampWhitelistfile = currentTimestampWhitelistFile;
                        }

                        // Checking leasefile
                        long currentTimestampLeaseFile = CoreTask.getModifiedDate(CoreTask.DATA_FILE_PATH + "/var/dnsmasq.leases");
                        if (this.timestampLeasefile != currentTimestampLeaseFile) {
                            try {
                            	// Getting current dns-leases
                                this.currentLeases = CoreTask.getLeases();
                                
                                // Cleaning-up knownLeases after a disconnect (dhcp-release)
                                for (String lease : this.knownLeases) {
                                    if (this.currentLeases.contains(lease) == false) {
                                    	Log.d(MSG_TAG, "Removing '"+lease+"' from known-leases!");
                                        this.knownLeases.remove(lease);
                                    }
                                }
                                
                                Enumeration<String> leases = this.currentLeases.keys();
                                while (leases.hasMoreElements()) {
                                    String mac = leases.nextElement();
                                    if (knownWhitelists.contains(mac) == false && knownLeases.contains(mac) == false) {
                                        this.sendUnAuthClientMessage(this.currentLeases.get(mac));
                                        this.knownLeases.add(mac);
                                    } else if (knownWhitelists.contains(mac) == true && knownLeases.contains(mac) == false) {
                                        if (notificationType == 2) {
                                            this.sendAuthClientMessage(this.currentLeases.get(mac));
                                            this.knownLeases.add(mac);
                                        }
                                    }
                                }
                                this.timestampLeasefile = currentTimestampLeaseFile;
                            } catch (Exception e) {
                                Log.d(MSG_TAG, "Unexpected error detected - Here is what I know: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    } else {
                        long currentTimestampLeaseFile = CoreTask.getModifiedDate(CoreTask.DATA_FILE_PATH + "/var/dnsmasq.leases");
                        if (this.timestampLeasefile != currentTimestampLeaseFile) {
                        	try {
                                // Getting current dns-leases
                        		this.currentLeases = CoreTask.getLeases();

                                // Cleaning-up knownLeases after a disconnect (dhcp-release)
                                for (String lease : this.knownLeases) {
                                    if (this.currentLeases.contains(lease) == false) {
                                    	Log.d(MSG_TAG, "Removing '"+lease+"' from known-leases!");
                                        this.knownLeases.remove(lease);
                                    }
                                }
                                
                                Enumeration<String> leases = this.currentLeases.keys();
                                while (leases.hasMoreElements()) {
                                    String mac = leases.nextElement();
                                    if (knownLeases.contains(mac) == false) {
                                        this.sendClientMessage(this.currentLeases.get(mac));
                                        this.knownLeases.add(mac);
                                    }
                                }
                                this.timestampLeasefile = currentTimestampLeaseFile;
                            } catch (Exception e) {
                                Log.d(MSG_TAG, "Unexpected error detected - Here is what I know: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    Log.d(MSG_TAG, "Checking for new clients is DISABLED ... ");
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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
