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
import java.util.Hashtable;
import java.util.List;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.tether.system.CoreTask;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.Toast;

public class SetupActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
	private TetherApplication application = null;
	
	public static final String MSG_TAG = "TETHER -> SetupActivity";
	
    private SharedPreferences.Editor preferenceEditor = null;
    private String currentSSID;
    private String currentChannel;
    private String currentPowermode;
    
    private Hashtable<String,String> tiWlanConf = null;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Init Application
        this.application = (TetherApplication)this.getApplication();
        
        this.tiWlanConf = CoreTask.getTiWlanConf();
        this.preferenceEditor = PreferenceManager.getDefaultSharedPreferences(this).edit(); 
        this.updatePreferences();
        addPreferencesFromResource(R.layout.setupview); 
    }
	
    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);   
    }

    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	String message;
    	String whileRunning = "This action will take effect when tethering is stopped and started again.";
    	if (key.equals("ssidpref")) {
    		String newSSID = sharedPreferences.getString("ssidpref", "G1Tether");
    		if (this.currentSSID.equals(newSSID) == false) {
    			if (this.validateSSID(newSSID)) {
	    			if (CoreTask.writeTiWlanConf("dot11DesiredSSID", newSSID)) {
	    				this.currentSSID = newSSID;
	    				message = "SSID changed to '"+newSSID+"'.";
	    				try{
		    				if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
		    					message += " " + whileRunning;
		    				}
	    				}
	    				catch (Exception ex) {
	    				}
	    				this.displayToastMessage(message);
	    			}
	    			else {
	    				this.preferenceEditor.putString("ssidpref", this.currentSSID);
	    				this.preferenceEditor.commit();
	    				this.displayToastMessage("Couldn't change ssid to '"+newSSID+"'!");
	    			}
    			}
    		}
    	}
    	else if (key.equals("channelpref")) {
    		String newChannel = sharedPreferences.getString("channelpref", "6");
    		if (this.currentChannel.equals(newChannel) == false) {
    			if (CoreTask.writeTiWlanConf("dot11DesiredChannel", newChannel)) {
    				this.currentChannel = newChannel;
    				message = "Channel changed to '"+newChannel+"'.";
    				try{
	    				if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
	    					message += " " + whileRunning;
	    				}
    				}
    				catch (Exception ex) {
    				}
    				this.displayToastMessage(message);
    			}
    			else {
    				this.preferenceEditor.putString("channelpref", this.currentChannel);
    				this.preferenceEditor.commit();
    				this.displayToastMessage("Couldn't change channel to  '"+newChannel+"'!");
    			}
    		}
    	}
    	else if (key.equals("powermodepref")) {
    		String newPowermode = sharedPreferences.getString("powermodepref", "0");
    		if (this.currentPowermode.equals(newPowermode) == false) {
    			if (CoreTask.writeTiWlanConf("dot11PowerMode", newPowermode)) {
    				this.currentPowermode = newPowermode;
    				message = "Powermode changed to '"+getResources().getStringArray(R.array.powermodenames)[new Integer(newPowermode)]+"'.";
    				try{
	    				if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
	    					message += " " + whileRunning;
	    				}
    				}
    				catch (Exception ex) {
    				}
    				this.displayToastMessage(message);
				}
    			else {
    				this.preferenceEditor.putString("powermodepref", this.currentChannel);
    				this.preferenceEditor.commit();
    				this.displayToastMessage("Couldn't change powermode to  '"+newPowermode+"'!");
    			}
    		}
    	}    	
    	else if (key.equals("syncpref")) {
    		boolean disableSync = sharedPreferences.getBoolean("syncpref", false);
			try {
				if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
					if (disableSync){
						SetupActivity.this.application.disableSync();
						SetupActivity.this.displayToastMessage("Auto-Sync is now disabled.");
					}
					else{
						SetupActivity.this.application.enableSync();
						SetupActivity.this.displayToastMessage("Auto-Sync is now enabled.");
					}
				}
			}
			catch (Exception ex) {
				SetupActivity.this.displayToastMessage("Unable to save Auto-Sync settings!");
			}
		}
    	else if (key.equals("wakelockpref")) {
			try {
				boolean disableWakeLock = sharedPreferences.getBoolean("wakelockpref", false);
				if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
					if (disableWakeLock){
						SetupActivity.this.application.releaseWakeLock();
						SetupActivity.this.displayToastMessage("Wake-Lock is now disabled.");
					}
					else{
						SetupActivity.this.application.acquireWakeLock();
						SetupActivity.this.displayToastMessage("Wake-Lock is now enabled.");
					}
				}
			}
			catch (Exception ex) {
				SetupActivity.this.displayToastMessage("Unable to save Auto-Sync settings!");
			}
    	}
    }
    
    private void updatePreferences() {
        // SSID
        this.currentSSID = this.getTiWlanConfValue("dot11DesiredSSID");
        this.preferenceEditor.putString("ssidpref", this.currentSSID);
        // Channel
        this.currentChannel = this.getTiWlanConfValue("dot11DesiredChannel");
        this.preferenceEditor.putString("channelpref", this.currentChannel);
        // Powermode
        this.currentPowermode = this.getTiWlanConfValue("dot11PowerMode");
        this.preferenceEditor.putString("powermodepref", this.currentPowermode);
        // Sync-Status
        this.preferenceEditor.commit();  
    }
    
    private String getTiWlanConfValue(String name) {
    	if (this.tiWlanConf != null && this.tiWlanConf.containsKey(name)) {
    		if (this.tiWlanConf.get(name) != null && this.tiWlanConf.get(name).length() > 0) {
    			return this.tiWlanConf.get(name);
    		}
    	}
    	SetupActivity.this.displayToastMessage("Oooooops ... tiwlan.conf does not exist or config-parameter '"+name+"' is not available!");
    	return "";
    }
    
    public boolean validateSSID(String newSSID){
    	if (newSSID.contains("#") || newSSID.contains("`")){
    		SetupActivity.this.displayToastMessage("New SSID cannot contain '#' or '`'!");
    		return false;
    	}
		return true;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu installBinaries = menu.addSubMenu(0, 0, 0, getString(R.string.installtext));
    	installBinaries.setIcon(R.drawable.install);
    	return supRetVal;
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d(MSG_TAG, "Menuitem:getId  -  "+menuItem.getItemId()+" -- "+menuItem.getTitle()); 
    	if (menuItem.getItemId() == 0) {
    		SetupActivity.this.installBinaries();
    	}
    	return supRetVal;
    } 
	
    private void installBinaries() {
    	List<String> filenames = new ArrayList<String>();
    	// tether
    	this.copyBinary(CoreTask.DATA_FILE_PATH+"/bin/tether", R.raw.tether);
    	filenames.add("tether");
    	// dnsmasq
    	this.copyBinary(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq", R.raw.dnsmasq);
    	filenames.add("dnsmasq");
    	// iptables
    	this.copyBinary(CoreTask.DATA_FILE_PATH+"/bin/iptables", R.raw.iptables);
    	filenames.add("iptables");
    	try {
			CoreTask.chmodBin(filenames);
		} catch (Exception e) {
			this.displayToastMessage("Unable to change permission on binary files!");
		}
    	// dnsmasq.conf
    	this.copyBinary(CoreTask.DATA_FILE_PATH+"/conf/dnsmasq.conf", R.raw.dnsmasq_conf);
    	// tiwlan.ini
    	this.copyBinary(CoreTask.DATA_FILE_PATH+"/conf/tiwlan.ini", R.raw.tiwlan_ini);
    	this.displayToastMessage("Binaries and config-files installed!");
    	// Update preferences
    	this.updatePreferences();
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
    
	public void displayToastMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	} 
}