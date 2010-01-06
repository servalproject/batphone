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

import android.R.drawable;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

public class SetupActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
	private TetherApplication application = null;
	
	private ProgressDialog progressDialog;
	
	public static final String MSG_TAG = "TETHER -> SetupActivity";
	
	public static final String DEFAULT_PASSPHRASE = "abcdefghijklm";
	public static final String DEFAULT_LANNETWORK = "192.168.2.0/24";

    private String currentSSID;
    private String currentChannel;
    private String currentPowermode;
    private String currentPassphrase;
    private String currentLAN;
    private boolean currentEncryptionEnabled;
    
    private EditTextPreference prefPassphrase;
    private EditTextPreference prefSSID;
    
    private Hashtable<String,String> tiWlanConf = null;
    private Hashtable<String,String> wpaSupplicantConf = null;
    
    private static int ID_DIALOG_RESTARTING = 2;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Init Application
        this.application = (TetherApplication)this.getApplication();
        
        // Getting configs
        this.updateConfigFromFile();
        
        addPreferencesFromResource(R.layout.setupview); 
        
        // Passphrase-Validation
        this.prefPassphrase = (EditTextPreference)findPreference("passphrasepref");
        this.prefPassphrase.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener(){
        	public boolean onPreferenceChange(Preference preference,
					Object newValue) {
        	  String validChars = "ABCDEFGHIJKLMONPQRSTUVWXYZ" +
        	                      "abcdefghijklmnopqrstuvwxyz" +
        	                      "0123456789";
        		if(newValue.toString().length() == 13){
        		  for (int i = 0 ; i < 13 ; i++) {
        		    if (!validChars.contains(newValue.toString().substring(i, i+1))) {
        		      SetupActivity.this.application.displayToastMessage("Passphrase contains invalid characters, not saved!");
        		      return false;
        		    }
        		  }
        			return true;
        		}
        		else{
        			SetupActivity.this.application.displayToastMessage("Passphrase too short! New value was not saved.");
        			return false;
        		}
        }});
        // SSID-Validation
        this.prefSSID = (EditTextPreference)findPreference("ssidpref");
        this.prefSSID.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener(){
          public boolean onPreferenceChange(Preference preference,
          Object newValue) {
            String message = validateSSID(newValue.toString());
            if(!message.equals("")) {
              SetupActivity.this.application.displayToastMessage(message);
              return false;
            }
            return true;
        }});
        final int origTextColorPassphrase = SetupActivity.this.prefPassphrase.getEditText().getCurrentTextColor();
        this.prefPassphrase.getEditText().addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
            	// Nothing
            }
	        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	        	// Nothing
	        }
	        public void onTextChanged(CharSequence s, int start, int before, int count) {
	        	if (s.length() == 13) {
	        		SetupActivity.this.prefPassphrase.getEditText().setTextColor(origTextColorPassphrase);
	        	}
	        	else {
	        		 SetupActivity.this.prefPassphrase.getEditText().setTextColor(Color.RED);
	        	}
	        }
        });
		Boolean bluetoothOn = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("bluetoothon", false);
		Message msg = Message.obtain();
		msg.what = bluetoothOn ? 0 : 1;
		SetupActivity.this.setWifiPrefsEnableHandler.sendMessage(msg);
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

    private void updateConfigFromFile() {
        this.tiWlanConf = application.tiwlan.get();
        this.wpaSupplicantConf = application.wpasupplicant.get();   	
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
    	if (id == ID_DIALOG_RESTARTING) {
	    	progressDialog = new ProgressDialog(this);
	    	progressDialog.setTitle("Restart Tethering");
	    	progressDialog.setMessage("Please wait while restarting...");
	    	progressDialog.setIndeterminate(false);
	    	progressDialog.setCancelable(true);
	        return progressDialog;
    	}
    	return null;
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	updateConfiguration(sharedPreferences, key);
    }
    
    Handler restartingDialogHandler = new Handler(){
        public void handleMessage(Message msg) {
        	if (msg.what == 0)
        		SetupActivity.this.showDialog(SetupActivity.ID_DIALOG_RESTARTING);
        	else
        		SetupActivity.this.dismissDialog(SetupActivity.ID_DIALOG_RESTARTING);
        	super.handleMessage(msg);
        	System.gc();
        }
    };
    
   Handler displayToastMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
       		if (msg.obj != null) {
       			SetupActivity.this.application.displayToastMessage((String)msg.obj);
       		}
        	super.handleMessage(msg);
        	System.gc();
        }
    };
    
    private void updateConfiguration(final SharedPreferences sharedPreferences, final String key) {
    	new Thread(new Runnable(){
			public void run(){
			   	String message = null;
		    	if (key.equals("ssidpref")) {
		    		String newSSID = sharedPreferences.getString("ssidpref", "AndroidTether");
		    		if (SetupActivity.this.currentSSID.equals(newSSID) == false) {
		    			application.tethercfg.put("wifi.essid", newSSID);
		    			application.tethercfg.write();
		    			if (application.tiwlan.write("dot11DesiredSSID", newSSID)) {
		    				// Rewriting wpa_supplicant if exists
		    				if (application.wpasupplicant.exists()) {
			        			Hashtable<String,String> values = new Hashtable<String,String>();
			        			values.put("ssid", "\""+sharedPreferences.getString("ssidpref", "AndroidTether")+"\"");
			        			values.put("wep_key0", "\""+sharedPreferences.getString("passphrasepref", DEFAULT_PASSPHRASE)+"\"");
			        			application.wpasupplicant.write(values);
		    				}
		    				SetupActivity.this.currentSSID = newSSID;
		    				message = "SSID changed to '"+newSSID+"'.";
		    				try{
			    				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/dnsmasq")) {
					    			// Show RestartDialog
					    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
			    					// Restart Tethering
					    			SetupActivity.this.application.restartTether();
					    			// Dismiss RestartDialog
					    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
			    				}
		    				}
		    				catch (Exception ex) {
		    					message = "Unable to restart tethering!";
		    				}
		    			}
		    	    	// Update config from Files
		    			SetupActivity.this.tiWlanConf = application.tiwlan.get();
		    	    	// Update preferences with real values
		    			SetupActivity.this.updatePreferences();
		    			
		    			// Send Message
		    			Message msg = new Message();
		    			msg.obj = message;
		    			SetupActivity.this.displayToastMessageHandler.sendMessage(msg);
		    		}
		    	}
		    	else if (key.equals("channelpref")) {
		    		String newChannel = sharedPreferences.getString("channelpref", "6");
		    		if (SetupActivity.this.currentChannel.equals(newChannel) == false) {
		    			application.tethercfg.put("wifi.channel", newChannel);
		    			application.tethercfg.write();
		    			if (application.tiwlan.write("dot11DesiredChannel", newChannel)) {
		    				SetupActivity.this.currentChannel = newChannel;
		    				message = "Channel changed to '"+newChannel+"'.";
		    				try{
			    				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/dnsmasq")) {
					    			// Show RestartDialog
					    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
					    			// Restart Tethering
			    					SetupActivity.this.application.restartTether();
					    			// Dismiss RestartDialog
					    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
			    				}
		    				}
		    				catch (Exception ex) {
		    					message = "Unable to restart tethering!";
		    				}
		    			}
		    			else {
		    				message = "Couldn't change channel to  '"+newChannel+"'!";
		    			}
		    	    	// Update config from Files
		    			SetupActivity.this.tiWlanConf = application.tiwlan.get();
		    	    	// Update preferences with real values
		    			SetupActivity.this.updatePreferences();
		    			
		    			// Send Message
		    			Message msg = new Message();
		    			msg.obj = message;
		    			SetupActivity.this.displayToastMessageHandler.sendMessage(msg);
		    		}
		    	}
		    	else if (key.equals("powermodepref")) {
		    		String newPowermode = sharedPreferences.getString("powermodepref", "0");
		    		if (SetupActivity.this.currentPowermode.equals(newPowermode) == false) {
		    			if (application.tiwlan.write("dot11PowerMode", newPowermode)) {
		    				SetupActivity.this.currentPowermode = newPowermode;
		    				message = "Powermode changed to '"+getResources().getStringArray(R.array.powermodenames)[new Integer(newPowermode)]+"'.";
		    				try{
			    				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/dnsmasq")) {
					    			// Show RestartDialog
					    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
					    			// Restart Tethering
			    					SetupActivity.this.application.restartTether();
					    			// Dismiss RestartDialog
					    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
			    				}
		    				}
		    				catch (Exception ex) {
		    					message = "Unable to restart tethering!";
		    				}
						}
		    			else {
		    				message = "Couldn't change powermode to  '"+newPowermode+"'!";
		    			}
		    	    	// Update config from Files
		    			SetupActivity.this.tiWlanConf = application.tiwlan.get();
		    	    	// Update preferences with real values
		    			SetupActivity.this.updatePreferences();
		    			
		    			// Send Message
		    			Message msg = new Message();
		    			msg.obj = message;
		    			SetupActivity.this.displayToastMessageHandler.sendMessage(msg);
		    		}
		    	}
		    	else if (key.equals("wakelockpref")) {
					try {
						boolean disableWakeLock = sharedPreferences.getBoolean("wakelockpref", false);
						if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/dnsmasq")) {
							if (disableWakeLock){
								SetupActivity.this.application.releaseWakeLock();
								message = "Wake-Lock is now disabled.";
							}
							else{
								SetupActivity.this.application.acquireWakeLock();
								message = "Wake-Lock is now enabled.";
							}
						}
					}
					catch (Exception ex) {
						message = "Unable to save Auto-Sync settings!";
					}
					
					// Send Message
	    			Message msg = new Message();
	    			msg.obj = message;
	    			SetupActivity.this.displayToastMessageHandler.sendMessage(msg);
		    	}
		    	else if (key.equals("acpref")) {
		    		boolean enableAccessCtrl = sharedPreferences.getBoolean("acpref", false);
		    		boolean whitelistFileExists = application.whitelist.exists();
		    		if (enableAccessCtrl) {
		    			if (whitelistFileExists == false) {
		    				try {
								application.whitelist.touch();
								application.restartSecuredWifi();
								message = "Access Control enabled.";
		    				} catch (IOException e) {
		    					message = "Unable to touch 'whitelist_mac.conf'.";
							}
		    			}
		    		}
		    		else {
		    			if (whitelistFileExists == true) {
		    				application.whitelist.remove();
		    				application.restartSecuredWifi();
		    				message = "Access Control disabled.";
		    			}
		    		}
		    		
		    		// Send Message
	    			Message msg = new Message();
	    			msg.obj = message;
	    			SetupActivity.this.displayToastMessageHandler.sendMessage(msg);
		    	}
		    	else if (key.equals("encpref")) {
		    		boolean enableEncryption = sharedPreferences.getBoolean("encpref", false);
		    		if (enableEncryption != SetupActivity.this.currentEncryptionEnabled) {
		    			if (enableEncryption == false) {
				    		if (application.wpasupplicant.exists()) {
				    			application.wpasupplicant.remove();
				    		}
				    		message = "WiFi Encryption disabled.";
				    		SetupActivity.this.currentEncryptionEnabled = false;
			    		}
			    		else {
			    			application.installWpaSupplicantConfig();
			    			Hashtable<String,String> values = new Hashtable<String,String>();
			    			values.put("ssid", "\""+sharedPreferences.getString("ssidpref", "AndroidTether")+"\"");
			    			values.put("wep_key0", "\""+sharedPreferences.getString("passphrasepref", DEFAULT_PASSPHRASE)+"\"");
			    			application.wpasupplicant.write(values);
			    			message = "WiFi Encryption enabled.";
			    			SetupActivity.this.currentEncryptionEnabled = true;
			    		}
			    		// Restarting
						try{
							if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/dnsmasq")) {
				    			// Show RestartDialog
								SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
				    			// Restart Tethering
								SetupActivity.this.application.restartTether();
				    			// Dismiss RestartDialog
								SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
							}
						}
						catch (Exception ex) {
						}
		    	    	// Update wpa-config from Files
						SetupActivity.this.wpaSupplicantConf = application.wpasupplicant.get();   
		    	    	// Update preferences with real values
						SetupActivity.this.updatePreferences();
						
						// Send Message
		    			Message msg = new Message();
		    			msg.obj = message;
		    			SetupActivity.this.displayToastMessageHandler.sendMessage(msg);
		    		}
		    	}
		    	else if (key.equals("passphrasepref")) {
		    		String passphrase = sharedPreferences.getString("passphrasepref", DEFAULT_PASSPHRASE);
		    		if (passphrase.equals(SetupActivity.this.currentPassphrase) == false) {
		    			if (application.wpasupplicant.exists()) {
			    			Hashtable<String,String> values = new Hashtable<String,String>();
			    			values.put("wep_key0", "\""+passphrase+"\"");
			    			application.wpasupplicant.write(values);
		    			}
		    			message = "Passphrase changed to '"+passphrase+"'.";
		    			SetupActivity.this.currentPassphrase = passphrase;
			    		
		    			// Restarting
						try{
							if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/dnsmasq") && application.wpasupplicant.exists()) {
				    			// Show RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
				    			// Restart Tethering
								SetupActivity.this.application.restartTether();
				    			// Dismiss RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
							}
						}
						catch (Exception ex) {
							Log.e(MSG_TAG, "Exception happend while restarting service - Here is what I know: "+ex);
						}
		    			
						message = "Passphrase changed to '"+passphrase+"'.";
						SetupActivity.this.currentPassphrase = passphrase;

		    	    	// Update wpa-config from Files
						SetupActivity.this.wpaSupplicantConf = application.wpasupplicant.get();   

						// Update preferences with real values
						SetupActivity.this.updatePreferences();
						
		    			// Send Message
		    			Message msg = new Message();
		    			msg.obj = message;
		    			SetupActivity.this.displayToastMessageHandler.sendMessage(msg);
		    		}
		    	}
		    	else if (key.equals("lannetworkpref")) {
		    		String lannetwork = sharedPreferences.getString("lannetworkpref", DEFAULT_LANNETWORK);
		    		if (lannetwork.equals(SetupActivity.this.currentLAN) == false) {
		    			
		    			// Updating lan-config
		    			SetupActivity.this.application.coretask.writeLanConf(lannetwork);
		    			String subnet = lannetwork.substring(0, lannetwork.lastIndexOf("."));
		    			SetupActivity.this.application.tethercfg.put("ip.network", lannetwork.split("/")[0]);
		    			SetupActivity.this.application.tethercfg.put("ip.gateway", subnet + "254");
		    			application.tethercfg.write();
		    			// Restarting
						try{
							if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/dnsmasq")) {
				    			// Show RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
				    			// Restart Tethering
								SetupActivity.this.application.restartTether();
				    			// Dismiss RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
							}
						}
						catch (Exception ex) {
							Log.e(MSG_TAG, "Exception happend while restarting service - Here is what I know: "+ex);
						}

						message = "LAN-network changed to '"+lannetwork+"'.";
						SetupActivity.this.currentLAN = lannetwork;

						// Update preferences with real values
						SetupActivity.this.updatePreferences();
						
		    			// Send Message
		    			Message msg = new Message();
		    			msg.obj = message;
		    			SetupActivity.this.displayToastMessageHandler.sendMessage(msg);
		    		}
		    	}		    	
		    	else if (key.equals("bluetoothon")) {
		    		Boolean bluetoothOn = sharedPreferences.getBoolean("bluetoothon", false);
		    		Message msg = Message.obtain();
		    		msg.what = bluetoothOn ? 0 : 1;
		    		SetupActivity.this.setWifiPrefsEnableHandler.sendMessage(msg);
					try{
						if (application.coretask.isNatEnabled() && (application.coretask.isProcessRunning("bin/dnsmasq") || application.coretask.isProcessRunning("bin/pand"))) {
			    			// Show RestartDialog
			    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
			    			// Restart Tethering
				    		if (bluetoothOn) {
								SetupActivity.this.application.restartTether(0, 1);
				    		}
				    		else {
				    			SetupActivity.this.application.restartTether(1, 0);
				    		}
			    			// Dismiss RestartDialog
			    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
						}
					}
					catch (Exception ex) {
					}
		    	}
		    	else if (key.equals("bluetoothkeepwifi")) {
		    		Boolean bluetoothWifi = sharedPreferences.getBoolean("bluetoothkeepwifi", false);
		    		if (bluetoothWifi) {
		    			SetupActivity.this.application.enableWifi();
		    		}
		    	}
			}
		}).start();
    }
    
    private void updatePreferences() {
        // Access Control
    	if (application.whitelist.exists()) {
    		this.application.preferenceEditor.putBoolean("acpref", true);
    	}
    	else {
    		this.application.preferenceEditor.putBoolean("acpref", false);
    	}
    	if (application.wpasupplicant.exists()) {
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
        if (this.wpaSupplicantConf != null) {
        	this.currentPassphrase = this.getWpaSupplicantConfValue("wep_key0");
        }
        else {
        	this.currentPassphrase = DEFAULT_PASSPHRASE;
        }
        // LAN configuration
        this.currentLAN = this.application.coretask.getLanIPConf();
        this.application.preferenceEditor.putString("lannetworkpref", this.currentLAN);
        
        // Sync-Status
        this.application.preferenceEditor.commit(); 
    }
    
    Handler  setWifiPrefsEnableHandler = new Handler() {
    	public void handleMessage(Message msg) {
			PreferenceGroup wifiGroup = (PreferenceGroup)findPreference("wifiprefs");
			wifiGroup.setEnabled(msg.what == 1);
        	super.handleMessage(msg);
    	}
    };
    
    private String getTiWlanConfValue(String name) {
    	if (this.tiWlanConf != null && this.tiWlanConf.containsKey(name)) {
    		if (this.tiWlanConf.get(name) != null && this.tiWlanConf.get(name).length() > 0) {
    			return this.tiWlanConf.get(name);
    		}
    	}
    	return "";
    }
    
    private String getWpaSupplicantConfValue(String name) {
    	if (this.wpaSupplicantConf != null && this.wpaSupplicantConf.containsKey(name)) {
    		return this.wpaSupplicantConf.get(name);
    	}
    	return "";   	
    }
    
    public String validateSSID(String newSSID){
      String message = "";
      String validChars = "ABCDEFGHIJKLMONPQRSTUVWXYZ" +
      "abcdefghijklmnopqrstuvwxyz" +
      "0123456789_.";
      for (int i = 0 ; i < newSSID.length() ; i++) {
        if (!validChars.contains(newSSID.substring(i, i+1))) {
          message = "SSID contains invalid characters";
        }
      }
      if (newSSID.equals("")) {
        message = "New SSID cannot be empty";
    	}
    	if (message.length() > 0)
    	  message += ", not saved.";
    	return message;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu installBinaries = menu.addSubMenu(0, 0, 0, getString(R.string.installtext));
    	installBinaries.setIcon(drawable.ic_menu_set_as);
    	return supRetVal;
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d(MSG_TAG, "Menuitem:getId  -  "+menuItem.getItemId()+" -- "+menuItem.getTitle()); 
    	if (menuItem.getItemId() == 0) {
    		this.application.installFiles();
    		this.updatePreferences();
    	}
    	return supRetVal;
    } 
}
