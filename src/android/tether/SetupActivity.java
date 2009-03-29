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
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.MetaKeyKeyListener;
import android.text.method.TextKeyListener;
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
    private String currentPassphrase;
    private boolean currentEncryptionEnabled;
    
    private EditTextPreference prefPassphrase;
    
    private Hashtable<String,String> tiWlanConf = null;
    private Hashtable<String,String> wpaSupplicantConf = null;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Init Application
        this.application = (TetherApplication)this.getApplication();
        
        // Getting configs
        this.tiWlanConf = application.coretask.getTiWlanConf();
        this.wpaSupplicantConf = application.coretask.getWpaSupplicantConf();
        
        addPreferencesFromResource(R.layout.setupview); 
        
        this.prefPassphrase = (EditTextPreference)findPreference("passphrasepref");
        this.prefPassphrase.getEditText().addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
            	// Nothing
            }
	        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	        	// Nothing
	        }
	        public void onTextChanged(CharSequence s, int start, int before, int count) {
	        	if (s.length() == 13) {
	        		 SetupActivity.this.prefPassphrase.setDialogMessage("WEP-key is valid!");
	        	 }
	        	 else {
	        		 SetupActivity.this.prefPassphrase.setDialogMessage("WEP-key must be 13 characters (ASCII) long!");
	        	 }
	        }
        });        
    }
	
    @Override
    protected void onResume() {
    	Log.d(MSG_TAG, "Calling onResume()");
    	super.onResume();
    	getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        this.updatePreferences();
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
	    			if (application.coretask.writeTiWlanConf("dot11DesiredSSID", newSSID)) {
	    				// Rewriting wpa_supplicant if exists
	    				if (application.coretask.wpaSupplicantExists()) {
		        			Hashtable<String,String> values = new Hashtable<String,String>();
		        			values.put("ssid", "\""+sharedPreferences.getString("ssidpref", "G1Tether")+"\"");
		        			values.put("wep_key0", "\""+sharedPreferences.getString("passphrasepref", "abcdefghijklm")+"\"");
		        			application.coretask.writeWpaSupplicantConf(values);
	    				}
	    				this.currentSSID = newSSID;
	    				message = "SSID changed to '"+newSSID+"'.";
	    				try{
		    				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning(application.coretask.DATA_FILE_PATH+"/bin/dnsmasq")) {
		    					this.application.restartTether();
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
    			if (application.coretask.writeTiWlanConf("dot11DesiredChannel", newChannel)) {
    				this.currentChannel = newChannel;
    				message = "Channel changed to '"+newChannel+"'.";
    				try{
	    				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning(application.coretask.DATA_FILE_PATH+"/bin/dnsmasq")) {
	    					this.application.restartTether();
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
    			if (application.coretask.writeTiWlanConf("dot11PowerMode", newPowermode)) {
    				this.currentPowermode = newPowermode;
    				message = "Powermode changed to '"+getResources().getStringArray(R.array.powermodenames)[new Integer(newPowermode)]+"'.";
    				try{
	    				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning(application.coretask.DATA_FILE_PATH+"/bin/dnsmasq")) {
	    					this.application.restartTether();
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
				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning(application.coretask.DATA_FILE_PATH+"/bin/dnsmasq")) {
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
				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning(application.coretask.DATA_FILE_PATH+"/bin/dnsmasq")) {
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
    		boolean whitelistFileExists = application.coretask.whitelistExists();
    		if (enableAccessCtrl) {
    			if (whitelistFileExists == false) {
    				try {
						application.coretask.touchWhitelist();
		    			this.displayToastMessage("Access Control enabled.");
		    			this.restartSecuredWifi();
    				} catch (IOException e) {
						this.displayToastMessage("Unable to touch 'whitelist_mac.conf'.");
					}
    			}
    		}
    		else {
    			if (whitelistFileExists == true) {
    				application.coretask.removeWhitelist();
        			this.displayToastMessage("Access Control disabled.");
        			this.restartSecuredWifi();
    			}
    		}
    	}
    	else if (key.equals("encpref")) {
    		boolean enableEncryption = sharedPreferences.getBoolean("encpref", false);
    		if (enableEncryption != this.currentEncryptionEnabled) {
	    		if (enableEncryption == false) {
		    		if (application.coretask.wpaSupplicantExists()) {
		    			application.coretask.removeWpaSupplicant();
		    		}
		    		this.displayToastMessage("WiFi Encryption disabled.");
		    		this.currentEncryptionEnabled = false;
	    		}
	    		else {
	    			application.installWpaSupplicantConfig();
	    			Hashtable<String,String> values = new Hashtable<String,String>();
	    			values.put("ssid", "\""+sharedPreferences.getString("ssidpref", "G1Tether")+"\"");
	    			values.put("wep_key0", "\""+sharedPreferences.getString("passphrasepref", "abcdefghijklm")+"\"");
	    			application.coretask.writeWpaSupplicantConf(values);
	    			this.displayToastMessage("WiFi Encryption enabled.");
	    			this.currentEncryptionEnabled = true;
	    		}
	    		// Restarting
				try{
					if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning(application.coretask.DATA_FILE_PATH+"/bin/dnsmasq")) {
						this.application.restartTether();
					}
				}
				catch (Exception ex) {
				}
    		}
    	}
    	else if (key.equals("passphrasepref")) {
    		String passphrase = sharedPreferences.getString("passphrasepref", "abcdefghijklm");
    		if (passphrase.equals(this.currentPassphrase) == false) {
    			
    			Hashtable<String,String> values = new Hashtable<String,String>();
    			values.put("wep_key0", "\""+passphrase+"\"");
    			application.coretask.writeWpaSupplicantConf(values);
    			
    			this.displayToastMessage("Passphrase changed to '"+passphrase+"'.");
    			this.currentPassphrase = passphrase;
	    		// Restarting
				try{
					if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning(application.coretask.DATA_FILE_PATH+"/bin/dnsmasq")) {
						this.application.restartTether();
					}
				}
				catch (Exception ex) {
				}
    			this.displayToastMessage("Passphrase changed to '"+passphrase+"'.");
    			this.currentPassphrase = passphrase;
    		}
    	}
    }
    
    private void restartSecuredWifi() {
    	try {
			if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning(application.coretask.DATA_FILE_PATH+"/bin/dnsmasq")) {
		    	Log.d(MSG_TAG, "Restarting iptables for access-control-changes!");
				if (!application.coretask.runRootCommand("cd "+application.coretask.DATA_FILE_PATH+";./bin/tether restartsecwifi")) {
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
    	if (application.coretask.whitelistExists()) {
    		this.application.preferenceEditor.putBoolean("acpref", true);
    	}
    	else {
    		this.application.preferenceEditor.putBoolean("acpref", false);
    	}
    	if (application.coretask.wpaSupplicantExists()) {
    		this.application.preferenceEditor.putBoolean("encpref", true);
    		this.currentEncryptionEnabled = true;
    	}
    	else {
    		this.application.preferenceEditor.putBoolean("encpref", false);
    		this.currentEncryptionEnabled = false;
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
        // Passphrase
        this.currentPassphrase = this.getWpaSupplicantConfValue("wep_key0");
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
    
    private String getWpaSupplicantConfValue(String name) {
    	if (this.wpaSupplicantConf != null && this.wpaSupplicantConf.containsKey(name)) {
    		return this.wpaSupplicantConf.get(name);
    	}
    	SetupActivity.this.displayToastMessage("Oooooops ... wpa_supplicant.conf does not exist or config-parameter '"+name+"' is not available!");
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