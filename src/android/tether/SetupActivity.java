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

import java.io.IOException;
import java.util.Hashtable;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.tether.system.CoreTask;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.Toast;

public class SetupActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
	private TetherApplication application = null;
	
	public static final String MSG_TAG = "TETHER -> SetupActivity";

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
        addPreferencesFromResource(R.layout.setupview); 
    }
	
    @Override
    protected void onResume() {
    	Log.d(MSG_TAG, "Calling onResume()");
        super.onResume();
        this.updatePreferences();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    protected void onPause() {
    	Log.d(MSG_TAG, "Calling onPause()");
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);   
    }

    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	String message;
    	if (key.equals("ssidpref")) {
    		String newSSID = sharedPreferences.getString("ssidpref", "G1Tether");
    		if (this.currentSSID.equals(newSSID) == false) {
    			if (this.validateSSID(newSSID)) {
	    			if (CoreTask.writeTiWlanConf("dot11DesiredSSID", newSSID)) {
	    				this.currentSSID = newSSID;
	    				message = "SSID changed to '"+newSSID+"'.";
	    				try{
		    				if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
		    					this.application.stopTether();
		    					this.application.startTether();
		    				}
	    				}
	    				catch (Exception ex) {
	    				}
	    				this.displayToastMessage(message);
	    			}
	    			else {
	    				this.application.preferenceEditor.putString("ssidpref", this.currentSSID);
	    				this.application.preferenceEditor.commit();
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
	    					this.application.stopTether();
	    					this.application.startTether();
	    				}
    				}
    				catch (Exception ex) {
    				}
    				this.displayToastMessage(message);
    			}
    			else {
    				this.application.preferenceEditor.putString("channelpref", this.currentChannel);
    				this.application.preferenceEditor.commit();
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
	    					this.application.stopTether();
	    					this.application.startTether();
	    				}
    				}
    				catch (Exception ex) {
    				}
    				this.displayToastMessage(message);
				}
    			else {
    				this.application.preferenceEditor.putString("powermodepref", this.currentChannel);
    				this.application.preferenceEditor.commit();
    				this.displayToastMessage("Couldn't change powermode to  '"+newPowermode+"'!");
    			}
    		}
    	}    	
    	else if (key.equals("syncpref")) {
    		boolean disableSync = sharedPreferences.getBoolean("syncpref", false);
			try {
				if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
					if (disableSync){
						this.application.disableSync();
						this.displayToastMessage("Auto-Sync is now disabled.");
					}
					else{
						this.application.enableSync();
						this.displayToastMessage("Auto-Sync is now enabled.");
					}
				}
			}
			catch (Exception ex) {
				this.displayToastMessage("Unable to save Auto-Sync settings!");
			}
		}
    	else if (key.equals("wakelockpref")) {
			try {
				boolean disableWakeLock = sharedPreferences.getBoolean("wakelockpref", false);
				if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
					if (disableWakeLock){
						this.application.releaseWakeLock();
						this.displayToastMessage("Wake-Lock is now disabled.");
					}
					else{
						this.application.acquireWakeLock();
						this.displayToastMessage("Wake-Lock is now enabled.");
					}
				}
			}
			catch (Exception ex) {
				this.displayToastMessage("Unable to save Auto-Sync settings!");
			}
    	}
    	else if (key.equals("acpref")) {
    		boolean enableAccessCtrl = sharedPreferences.getBoolean("acpref", false);
    		boolean whitelistFileExists = CoreTask.whitelistExists();
    		if (enableAccessCtrl) {
    			if (whitelistFileExists == false) {
    				try {
						CoreTask.touchWhitelist();
					} catch (IOException e) {
						this.displayToastMessage("Unable to touch 'whitelist_mac.conf'.");
					}
    			}
    			this.displayToastMessage("Access Control enabled.");
    		}
    		else {
    			if (whitelistFileExists == true) {
    				CoreTask.removeWhitelist();
    			}
    			this.displayToastMessage("Access Control disabled.");
    		}
    		this.restartSecuredWifi();
    	}
    }
    
    private void restartSecuredWifi() {
    	try {
			if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
		    	Log.d(MSG_TAG, "Restarting iptables for access-control-changes!");
				if (!CoreTask.runRootCommand(CoreTask.DATA_FILE_PATH+"/bin/tether restartsecwifi")) {
					this.displayToastMessage("Unable to restart secured wifi!");
					return;
				}
			}
		} catch (Exception e) {
			// nothing
		}
    }
    
    private void updatePreferences() {
        // Access Control
    	if (CoreTask.whitelistExists()) {
    		this.application.preferenceEditor.putBoolean("acpref", true);
    	}
    	else {
    		this.application.preferenceEditor.putBoolean("acpref", false);
    	}
    	// SSID
        this.currentSSID = this.getTiWlanConfValue("dot11DesiredSSID");
        this.application.preferenceEditor.putString("ssidpref", this.currentSSID);
        // Channel
        this.currentChannel = this.getTiWlanConfValue("dot11DesiredChannel");
        this.application.preferenceEditor.putString("channelpref", this.currentChannel);
        // Powermode
        this.currentPowermode = this.getTiWlanConfValue("dot11PowerMode");
        this.application.preferenceEditor.putString("powermodepref", this.currentPowermode);
        // Sync-Status
        this.application.preferenceEditor.commit();  
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
    		this.application.installBinaries();
    		this.updatePreferences();
    	}
    	return supRetVal;
    } 

    public void displayToastMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	} 
}