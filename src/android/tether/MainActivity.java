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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.tether.system.CoreTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TableRow;
import android.widget.Toast;
import android.net.Uri;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;

public class MainActivity extends Activity {
	
	private WifiManager wifiManager;
	private NotificationManager notificationManager;
	private ConnectivityManager connectivityManager;
	
	private Notification notification;
	private PendingIntent contentIntent;
	private ProgressDialog progressDialog;

	private ImageButton startBtn = null;
	private ImageButton stopBtn = null;
	
	private TableRow startTblRow = null;
	private TableRow stopTblRow = null;
	
	public static final String SETTING_LISTEN_FOR_TICKLES = "listen_for_tickles";
    public static final String SETTING_BACKGROUND_DATA = "background_data";

	private static final String DATA_FILE_PATH = "/data/data/android.tether";
	
	private static final int REQUEST_CODE_NOTIFICATION = 0;
	private static final int ID_NOTIFICATION = 0;
	
	private static int ID_DIALOG_STARTING = 0;
	private static int ID_DIALOG_STOPPING = 1;
	
	public static final String KEY = "name";
	public static final String VALUE = "value";
	private static final String[] PROJECTION = { KEY, VALUE };
	
	private static boolean origWifiState = false;
	public static boolean origTickleState = false;
	public static boolean origBackState = false;
	
	public static final Uri CONTENT_URI = Uri.parse("content://sync/settings");

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Init Table-Rows
        this.startTblRow = (TableRow)findViewById(R.id.startRow);
        this.stopTblRow = (TableRow)findViewById(R.id.stopRow);

        // Check Homedir, or create it
        this.checkDirs();        
        
        // Check for binaries
        if (this.binariesExists() == false || CoreTask.filesetOutdated()) {
        	if (CoreTask.hasRootPermission()) {
        		this.installBinaries();
        	}
        	else {
        		LayoutInflater li = LayoutInflater.from(this);
                View view = li.inflate(R.layout.norootview, null); 
    			new AlertDialog.Builder(MainActivity.this)
    	        .setTitle("Not Root!")
    	        .setIcon(R.drawable.warning)
    	        .setView(view)
    	        .setNegativeButton("Close", new DialogInterface.OnClickListener() {
    	                public void onClick(DialogInterface dialog, int whichButton) {
    	                        Log.d("*** DEBUG ***", "Close pressed");
    	                        MainActivity.this.finish();
    	                }
    	        })
    	        .setNeutralButton("Override", new DialogInterface.OnClickListener() {
		                public void onClick(DialogInterface dialog, int whichButton) {
	                        Log.d("*** DEBUG ***", "Override pressed");
	                        MainActivity.this.installBinaries();
		                }
    	        })
    	        .show();
        	}
        }
        
        // init wifiManager
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE); 
        
        // init connectivityManager
        connectivityManager = (ConnectivityManager) this.getSystemService(CONNECTIVITY_SERVICE);
        
        // init notificationManager
        this.notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
    	this.notification = new Notification(R.drawable.start_notification, "Wifi Tether", System.currentTimeMillis());
    	this.contentIntent = PendingIntent.getActivity(getBaseContext(), REQUEST_CODE_NOTIFICATION, new Intent(this, MainActivity.class), 0);
        
        // Start Button
        this.startBtn = (ImageButton) findViewById(R.id.startTetherBtn);
		this.startBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d("*** DEBUG ***", "StartBtn pressed ...");
		    	showDialog(MainActivity.ID_DIALOG_STARTING);
				new Thread(new Runnable(){
					public void run(){
						MainActivity.this.disableWifi();
						origTickleState = getBoolean(getContentResolver(),
				    			SETTING_LISTEN_FOR_TICKLES, true);
						origBackState = getBoolean(getContentResolver(),
				    			SETTING_BACKGROUND_DATA, true);
						if (MainActivity.this.getSync()){
							MainActivity.this.disableSync();
						}
						int started = MainActivity.this.startTether();
						MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STARTING);
						Message message = new Message();
						if (started != 0) {
							message.what = started;
						}
						MainActivity.this.viewUpdateHandler.sendMessage(message); 
					}
				}).start();
			}
		});

		// Stop Button
		this.stopBtn = (ImageButton) findViewById(R.id.stopTetherBtn);
		this.stopBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d("*** DEBUG ***", "StopBtn pressed ...");
		    	showDialog(MainActivity.ID_DIALOG_STOPPING);
				new Thread(new Runnable(){
					public void run(){
						MainActivity.this.stopTether();
						MainActivity.this.enableWifi();
						MainActivity.this.enableSync();
						MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STOPPING);
						MainActivity.this.viewUpdateHandler.sendMessage(new Message()); 
					}
				}).start();
			}
		});			
		this.toggleStartStop();
    }
    
    //gets user preference on whether auto-sync should be disabled during tethering
    public boolean getSync(){
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		return settings.getBoolean("syncpref", false);
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu setup = menu.addSubMenu(0, 0, 0, getString(R.string.setuptext));
    	setup.setIcon(R.drawable.setup);
    	SubMenu accessctr = menu.addSubMenu(0, 3, 0, getString(R.string.accesscontroltext));
    	accessctr.setIcon(R.drawable.acl);    	
    	SubMenu log = menu.addSubMenu(0, 1, 0, getString(R.string.logtext));
    	log.setIcon(R.drawable.log);
    	SubMenu about = menu.addSubMenu(0, 2, 0, getString(R.string.abouttext));
    	about.setIcon(R.drawable.about);    	
    	return supRetVal;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d("*** DEBUG ***", "Menuitem:getId  -  "+menuItem.getItemId()); 
    	if (menuItem.getItemId() == 0) {
    		Intent i = new Intent(MainActivity.this, SetupActivity.class);
	        startActivityForResult(i, 0);
    	}
    	else if (menuItem.getItemId() == 1) {
	        Intent i = new Intent(MainActivity.this, LogActivity.class);
	        startActivityForResult(i, 0);
    	}
    	else if (menuItem.getItemId() == 2) {
    		LayoutInflater li = LayoutInflater.from(this);
            View view = li.inflate(R.layout.aboutview, null); 
			new AlertDialog.Builder(MainActivity.this)
	        .setTitle("About")
	        .setIcon(R.drawable.about)
	        .setView(view)
	        .setNeutralButton("Close", new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	                        Log.d("*** DEBUG ***", "Close pressed");
	                }
	        })
	        .show();
    	} 
    	else if (menuItem.getItemId() == 3) {
	        Intent i = new Intent(MainActivity.this, AccessControlActivity.class);
	        startActivityForResult(i, 0);   		
    	}
    	return supRetVal;
    }    

    @Override
    protected Dialog onCreateDialog(int id) {
    	if (id == ID_DIALOG_STARTING) {
	    	progressDialog = new ProgressDialog(this);
	    	progressDialog.setTitle("Start Tethering");
	    	progressDialog.setMessage("Please wait while starting...");
	    	progressDialog.setIndeterminate(false);
	    	progressDialog.setCancelable(true);
	        return progressDialog;
    	}
    	else if (id == ID_DIALOG_STOPPING) {
	    	progressDialog = new ProgressDialog(this);
	    	progressDialog.setTitle("Stop Tethering");
	    	progressDialog.setMessage("Please wait while stopping...");
	    	progressDialog.setIndeterminate(false);
	    	progressDialog.setCancelable(true);
	        return progressDialog;  		
    	}
    	return null;
    }

    Handler viewUpdateHandler = new Handler(){
        public void handleMessage(Message msg) {
        	if (msg.what == 1) {
        		Log.d("*** DEBUG ***", "No mobile-data-connection established!");
        		MainActivity.this.displayToastMessage("No mobile-data-connection established!");
        	}
        	else if (msg.what == 2) {
        		Log.d("*** DEBUG ***", "Unable to start tetering!");
        		MainActivity.this.displayToastMessage("Unable to start tethering!");
        	}
        	MainActivity.this.toggleStartStop();
        	super.handleMessage(msg);
        }
   };  
    
    private void toggleStartStop() {
    	boolean dnsmasqRunning = false;
		try {
			dnsmasqRunning = CoreTask.isProcessRunning(DATA_FILE_PATH+"/bin/dnsmasq");
		} catch (Exception e) {
			MainActivity.this.displayToastMessage("Unable to check if dnsmasq is currently running!");
		}
    	boolean natEnabled = CoreTask.isNatEnabled();
    	if (dnsmasqRunning == true && natEnabled == true) {
    		this.startTblRow.setVisibility(View.GONE);
    		this.stopTblRow.setVisibility(View.VISIBLE);
    		
    		// Notification
    		notification.flags = Notification.FLAG_ONGOING_EVENT;
        	notification.setLatestEventInfo(this, "Wifi Tether", "Tethering is currently running ...", this.contentIntent);
        	this.notificationManager.notify(ID_NOTIFICATION, this.notification);
    	}
    	else if (dnsmasqRunning == false && natEnabled == false) {
    		this.startTblRow.setVisibility(View.VISIBLE);
    		this.stopTblRow.setVisibility(View.GONE);
    		
    		// Notification
        	this.notificationManager.cancel(ID_NOTIFICATION);
    	}   	
    	else {
    		this.startTblRow.setVisibility(View.VISIBLE);
    		this.stopTblRow.setVisibility(View.VISIBLE);
    		MainActivity.this.displayToastMessage("Your phone is currently in an unknown state - try to reboot!");
    	}
    }
    
    private void disableWifi() {
    	if (this.wifiManager.isWifiEnabled()) {
    		origWifiState = true;
    		this.wifiManager.setWifiEnabled(false);
        	// Waiting for interface-shutdown
    		try {
    			Thread.sleep(5000);
    		} catch (InterruptedException e) {
    			// nothing
    		}
    	}
    }
    
    private void enableWifi() {
    	if (origWifiState) {
    		this.wifiManager.setWifiEnabled(true);
    	}
    }
    
    //function for changing sync settings
    static public void putBoolean(ContentResolver contentResolver, String name, boolean val) {
        ContentValues values = new ContentValues();
        values.put(KEY, name);
        values.put(VALUE, Boolean.toString(val));
        // this insert is translated into an update by the underlying Sync provider
        contentResolver.insert(CONTENT_URI, values);
    }
    
    //function for checking sync settings
    static public boolean getBoolean(ContentResolver contentResolver,
            String name, boolean def) {
        Cursor cursor = contentResolver.query(
            CONTENT_URI,
            PROJECTION,
            KEY + "=?",
            new String[] { name },
            null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
            	Log.d("*** DEBUG ***",cursor.getString(0));
                return Boolean.parseBoolean(cursor.getString(1));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return def;
    }
    
    private void disableSync() {
    	if (getBoolean(getContentResolver(),
    			SETTING_LISTEN_FOR_TICKLES, true)) {
    		origTickleState = true;
    		putBoolean(getContentResolver(),
    				SETTING_LISTEN_FOR_TICKLES, false);
    	}
        if (getBoolean(getContentResolver(),
    			SETTING_BACKGROUND_DATA, true)) {
        	origBackState = true;
    		putBoolean(getContentResolver(),
    				SETTING_BACKGROUND_DATA, false);
    	}
    }
    
    private void enableSync() {
    	if (origTickleState) {
    		putBoolean(getContentResolver(),
    				SETTING_LISTEN_FOR_TICKLES, true);
    	}
    	if (origBackState) {
    		putBoolean(getContentResolver(),
    				SETTING_BACKGROUND_DATA, true);
    	}
    }
    
    private int startTether() {
    	/*
    	 * ReturnCodes:
    	 *    0 = All OK, Service started
    	 *    1 = Mobile-Data-Connection not established
    	 *    2 = Fatal error 
    	 */
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
    	if (CoreTask.runRootCommand(DATA_FILE_PATH+"/bin/tether start")) {
    		return 0;
    	}
    	return 2;
    }
    
    private boolean stopTether() {
    	return CoreTask.runRootCommand(DATA_FILE_PATH+"/bin/tether stop");
    }
    
    public boolean binariesExists() {
    	File file = new File(DATA_FILE_PATH+"/bin/tether");
    	if (file.exists()) {
    		return true;
    	}
    	return false;
    }
    
    public void installBinaries() {
    	List<String> filenames = new ArrayList<String>();
    	// tether
    	this.copyBinary(DATA_FILE_PATH+"/bin/tether", R.raw.tether);
    	filenames.add("tether");
    	// dnsmasq
    	this.copyBinary(DATA_FILE_PATH+"/bin/dnsmasq", R.raw.dnsmasq);
    	filenames.add("dnsmasq");
    	// iptables
    	this.copyBinary(DATA_FILE_PATH+"/bin/iptables", R.raw.iptables);
    	filenames.add("iptables");
    	try {
			CoreTask.chmodBin(filenames);
		} catch (Exception e) {
			this.displayToastMessage("Unable to change permission on binary files!");
		}
    	// dnsmasq.conf
    	this.copyBinary(DATA_FILE_PATH+"/conf/dnsmasq.conf", R.raw.dnsmasq_conf);
    	// tiwlan.ini
    	this.copyBinary(DATA_FILE_PATH+"/conf/tiwlan.ini", R.raw.tiwlan_ini);
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
			MainActivity.this.displayToastMessage("Couldn't install file - "+filename+"!");
		}
    }
    

    private void checkDirs() {
    	File dir = new File(DATA_FILE_PATH);
    	if (dir.exists() == false) {
    			MainActivity.this.displayToastMessage("Application data-dir does not exist!");
    	}
    	else {
	    	dir = new File(DATA_FILE_PATH+"/bin");
	    	if (dir.exists() == false) {
	    		if (!dir.mkdir()) {
	    			MainActivity.this.displayToastMessage("Couldn't create bin-directory!");
	    		}
	    	}
	    	dir = new File(DATA_FILE_PATH+"/var");
	    	if (dir.exists() == false) {
	    		if (!dir.mkdir()) {
	    			MainActivity.this.displayToastMessage("Couldn't create var-directory!");
	    		}
	    	}
	    	dir = new File(DATA_FILE_PATH+"/conf");
	    	if (dir.exists() == false) {
	    		if (!dir.mkdir()) {
	    			MainActivity.this.displayToastMessage("Couldn't create conf-directory!");
	    		}
	    	}   			
    	}
    }
    
	public void displayToastMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}
}