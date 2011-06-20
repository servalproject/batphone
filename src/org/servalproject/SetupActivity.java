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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.servalproject.system.Configuration;
import org.servalproject.system.UnknowndeviceException;
import org.servalproject.system.WiFiRadio;
import org.servalproject.system.WiFiRadio.WifiMode;

import android.R.drawable;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

public class SetupActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private ServalBatPhoneApplication application = null;

	private ProgressDialog progressDialog;

	public static final String MSG_TAG = "ADHOC -> SetupActivity";

    private String currentSSID;
    private String currentChannel;
    private String currentPassphrase;
    private String currentLAN;
    private boolean currentEncryptionEnabled;
    private String currentTransmitPower;

    private EditTextPreference prefPassphrase;
    private EditTextPreference prefSSID;

	private static int ID_DIALOG_UPDATING = 1;
    private static int ID_DIALOG_RESTARTING = 2;
	private int currentDialog = 0;

    private WifiApControl apControl;
    private CheckBoxPreference apPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Init Application
        this.application = (ServalBatPhoneApplication)this.getApplication();

        // Init CurrentSettings
        // PGS 20100613 - MeshPotato compatible settings
        // (Mesh potatoes claim to be on channel 1 when enquired with iwconfig, but iStumbler shows that
        //  they seem to be on channel 11 - so we will try defaulting to channel 11.
        this.currentSSID = this.application.settings.getString("ssidpref", "potato");
        this.currentChannel = this.application.settings.getString("channelpref", "11");
        this.currentPassphrase = this.application.settings.getString("passphrasepref", this.application.DEFAULT_PASSPHRASE);
        this.currentLAN = this.application.settings.getString("lannetworkpref", this.application.DEFAULT_LANNETWORK);
        this.currentEncryptionEnabled = this.application.settings.getBoolean("encpref", false);
        this.currentTransmitPower = this.application.settings.getString("txpowerpref", "disabled");

        addPreferencesFromResource(R.layout.setupview);

        // Disable "Transmit power" if not supported
        if (!this.application.isTransmitPowerSupported()) {
        	PreferenceGroup wifiGroup = (PreferenceGroup)findPreference("wifiprefs");
        	ListPreference txpowerPreference = (ListPreference)findPreference("txpowerpref");
        	wifiGroup.removePreference(txpowerPreference);
        }

        // Disable "encryption-setup-method"
        if (this.application.interfaceDriver.startsWith("softap")) {
        	PreferenceGroup wifiGroup = (PreferenceGroup)findPreference("wifiprefs");
        	ListPreference encsetupPreference = (ListPreference)findPreference("encsetuppref");
        	wifiGroup.removePreference(encsetupPreference);
        }

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

		{
			final ListPreference chipsetPref = (ListPreference) findPreference("chipset");
			List<CharSequence> entries = new ArrayList<CharSequence>();
			entries.add("Automatic");
			final List<WiFiRadio.Chipset> chipsets = this.application.wifiRadio
					.getChipsets();
			for (WiFiRadio.Chipset chipset : chipsets) {
				entries.add(chipset.chipset);
			}
			String values[] = entries.toArray(new String[entries.size()]);
			chipsetPref.setEntries(values);
			chipsetPref.setEntryValues(values);
			chipsetPref.setSummary(application.wifiRadio.getChipset());

			chipsetPref
					.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
						public boolean onPreferenceChange(
								Preference preference, Object newValue) {
							String value = (String) newValue;
							dialogHandler.sendEmptyMessage(ID_DIALOG_UPDATING);
							boolean ret = false;

							if (value.equals("Automatic")) {
								try {
									chipsetPref
											.setSummary(application.wifiRadio
													.getChipset());
									application.wifiRadio.identifyChipset();
									ret = true;
								} catch (UnknowndeviceException e) {
									Log.e("BatPhone", e.toString(), e);
									application.displayToastMessage(e
											.toString());
									ret = false;
								}
							} else {
								for (WiFiRadio.Chipset chipset : chipsets) {
									if (chipset.chipset.equals(value)) {
										ret = application.wifiRadio
												.testForChipset(chipset);
										break;
									}
								}
							}
							dialogHandler.sendEmptyMessage(0);
							return ret;
						}
					});
		}

        // Passphrase-Validation
        this.prefPassphrase = (EditTextPreference)findPreference("passphrasepref");
        final int origTextColorPassphrase = SetupActivity.this.prefPassphrase.getEditText().getCurrentTextColor();

    	// check if personal AP is enabled
		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		apControl = WifiApControl.getApControl(wifi);

		apPref=(CheckBoxPreference) findPreference("ap_enabled");
		apPref.setEnabled(apControl!=null);

        if (Configuration.getWifiInterfaceDriver(this.application.deviceType).startsWith("softap")) {
        	Log.d(MSG_TAG, "Adding validators for WPA-Encryption.");
        	this.prefPassphrase.setSummary(this.prefPassphrase.getSummary()+" (WPA-PSK)");
        	this.prefPassphrase.setDialogMessage("Passphrase must be between 8 and 30 characters long!");
	        // Passphrase Change-Listener for WPA-encryption
        	this.prefPassphrase.getEditText().addTextChangedListener(new TextWatcher() {
						public void afterTextChanged(Editable s) {
	            	// Nothing
	            }

						public void beforeTextChanged(CharSequence s,
								int start, int count, int after) {
		        	// Nothing
		        }

						public void onTextChanged(CharSequence s, int start,
								int before, int count) {
		        	if (s.length() < 8 || s.length() > 30) {
		        		SetupActivity.this.prefPassphrase.getEditText().setTextColor(Color.RED);
		        	}
		        	else {
		        		SetupActivity.this.prefPassphrase.getEditText().setTextColor(origTextColorPassphrase);
		        	}
		        }
	        });

	        this.prefPassphrase.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener(){
						public boolean onPreferenceChange(
								Preference preference,
						Object newValue) {
		        	String validChars = "ABCDEFGHIJKLMONPQRSTUVWXYZ" +
                      "abcdefghijklmnopqrstuvwxyz" +
                      "0123456789";
	        		if (newValue.toString().length() < 8) {
	        			SetupActivity.this.application.displayToastMessage("Passphrase too short! New value was not saved.");
	        			return false;
	        		}
	        		else if (newValue.toString().length() > 30) {
	        			SetupActivity.this.application.displayToastMessage("Passphrase too long! New value was not saved.");
	        			return false;
	        		}
	        		for (int i = 0 ; i < newValue.toString().length() ; i++) {
	        		    if (!validChars.contains(newValue.toString().substring(i, i+1))) {
	        		      SetupActivity.this.application.displayToastMessage("Passphrase contains invalid characters, not saved!");
	        		      return false;
	        		    }
	        		  }
	        		return true;
	        	}
	        });
        }
        else {
        	Log.d(MSG_TAG, "Adding validators for WEP-Encryption.");
        	this.prefPassphrase.setSummary(this.prefPassphrase.getSummary()+" (WEP 128-bit)");
        	this.prefPassphrase.setDialogMessage("Passphrase must be 13 characters (ASCII) long!");
        	// Passphrase Change-Listener for WEP-encryption
	        this.prefPassphrase.getEditText().addTextChangedListener(new TextWatcher() {
						public void afterTextChanged(Editable s) {
	            	// Nothing
	            }

						public void beforeTextChanged(CharSequence s,
								int start, int count, int after) {
		        	// Nothing
		        }

						public void onTextChanged(CharSequence s, int start,
								int before, int count) {
		        	if (s.length() == 13) {
		        		SetupActivity.this.prefPassphrase.getEditText().setTextColor(origTextColorPassphrase);
		        	}
		        	else {
		        		 SetupActivity.this.prefPassphrase.getEditText().setTextColor(Color.RED);
		        	}
		        }
	        });

	        this.prefPassphrase.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener(){
						public boolean onPreferenceChange(
								Preference preference,
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
        }
    }

    @Override
    protected void onResume() {
    	super.onResume();
    	SharedPreferences prefs=getPreferenceScreen().getSharedPreferences();

		if (apControl!=null){
			apPref.setChecked(apControl.getWifiApState()==WifiApControl.WIFI_AP_STATE_ENABLED);
		}

    	prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
    	Log.d(MSG_TAG, "Calling onPause()");
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
    	if (id == ID_DIALOG_RESTARTING) {
	    	progressDialog = new ProgressDialog(this);
	    	progressDialog.setTitle("Restarting BatPhone");
	    	progressDialog.setMessage("Please wait while restarting...");
	    	progressDialog.setIndeterminate(false);
	    	progressDialog.setCancelable(true);
	        return progressDialog;
    	}
    	return null;
    }


	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
    	updateConfiguration(sharedPreferences, key);
    }

	Handler dialogHandler = new Handler() {
        @Override
		public void handleMessage(Message msg) {
        	if (msg.what == 0)
				SetupActivity.this.dismissDialog(currentDialog);
        	else
				SetupActivity.this.showDialog(msg.what);
        	super.handleMessage(msg);
        }
    };

	private void restartAdhoc() {
		if (application.wifiRadio.getCurrentMode() != WifiMode.Adhoc)
			return;
		dialogHandler.sendEmptyMessage(ID_DIALOG_RESTARTING);
		try {
			application.restartAdhoc();
		} catch (Exception ex) {
			application.displayToastMessage(ex.toString());
		}
		dialogHandler.sendEmptyMessage(0);
	}

    private void updateConfiguration(final SharedPreferences sharedPreferences, final String key) {
    	new Thread(new Runnable(){
			public void run(){
			   	String message = null;
				if (key.equals("ssidpref")) {
		    		String newSSID = sharedPreferences.getString("ssidpref", "potato");
		    		if (SetupActivity.this.currentSSID.equals(newSSID) == false) {
	    				SetupActivity.this.currentSSID = newSSID;
						restartAdhoc();
		    		}
		    	}
			   	else if (key.equals("instrumentpref")) {
			   		Instrumentation.setEnabled(sharedPreferences.getBoolean("instrumentpref", false));
			   	}
			   	else if (key.equals("instrument_rec")){
			   		try{
						dialogHandler.sendEmptyMessage(ID_DIALOG_RESTARTING);
						SetupActivity.this.application.meshManager.restartDna();
						dialogHandler.sendEmptyMessage(0);
			   		}catch(Exception e){
			   			SetupActivity.this.application.displayToastMessage(e.toString());
			   		}
			   	}
			   	else if (key.equals("ap_enabled")){
			   		boolean enabled=sharedPreferences.getBoolean("ap_enabled", false);
					dialogHandler.sendEmptyMessage(ID_DIALOG_UPDATING);
			   		try{
				   		if (SetupActivity.this.application.setApEnabled(enabled))
				   			SetupActivity.this.application.displayToastMessage("Access point "+(enabled?"started":"stopped"));
				   		else
				   			SetupActivity.this.application.displayToastMessage("Unable to "+(enabled?"start":"stop")+" access point");
			   		}catch(Exception e){
			   			Log.v("BatPhone",e.toString(),e);
			   			SetupActivity.this.application.displayToastMessage(e.toString());
			   		}
					apPref.setChecked(apControl.getWifiApState()==WifiApControl.WIFI_AP_STATE_ENABLED);
					dialogHandler.sendEmptyMessage(0);
			   	}
		    	else if (key.equals("channelpref")) {
		    		String newChannel = sharedPreferences.getString("channelpref", "1");
		    		if (SetupActivity.this.currentChannel.equals(newChannel) == false) {
	    				SetupActivity.this.currentChannel = newChannel;
						restartAdhoc();
		    		}
		    	}
		    	else if (key.equals("wakelockpref")) {
					SetupActivity.this.application.meshManager
							.wakeLockChanged(sharedPreferences.getBoolean(
									"wakelockpref", true));
		    	}
		    	else if (key.equals("encpref")) {
		    		boolean enableEncryption = sharedPreferences.getBoolean("encpref", false);
		    		if (enableEncryption != SetupActivity.this.currentEncryptionEnabled) {
						SetupActivity.this.currentEncryptionEnabled = enableEncryption;
						restartAdhoc();
		    		}
		    	}
		    	else if (key.equals("passphrasepref")) {
		    		String passphrase = sharedPreferences.getString("passphrasepref", SetupActivity.this.application.DEFAULT_PASSPHRASE);
		    		if (passphrase.equals(SetupActivity.this.currentPassphrase) == false) {
						restartAdhoc();
						SetupActivity.this.currentPassphrase = passphrase;
		    		}
		    	}
		    	else if (key.equals("txpowerpref")) {
		    		String transmitPower = sharedPreferences.getString("txpowerpref", "disabled");
		    		if (transmitPower.equals(SetupActivity.this.currentTransmitPower) == false) {
						restartAdhoc();
						SetupActivity.this.currentTransmitPower = transmitPower;
		    		}
		    	}
		    	else if (key.startsWith("gateway")) {
		    		// Any one of the various lan gateway settings.
		    		// Re-write the included SIP.conf
		    		String server = sharedPreferences.getString("gatewayserver", "");
		    		String username = sharedPreferences.getString("gatewayuser", "");
		    		String password = sharedPreferences.getString("gatewaypass", "");
		    		Boolean enable = sharedPreferences.getBoolean("gatewayenable", false);

		    		try{
						File file = new File(
								"/data/data/org.servalproject/asterisk/etc/asterisk/gatewaysip.conf");
		    		    file.createNewFile();
		    			FileWriter f = new FileWriter(file.getAbsolutePath());
		    			BufferedWriter out = new BufferedWriter(f);
		    			if (enable) {
		    				out.write("register => "+username+":"+password+"@"+server+"/"+username+"\n"+
		    						"[dnagateway]\n"+
		    						"type=friend\n"+
		    						"username="+username+"\n"+
		    						"secret="+password+"\n"+
		    						"fromuser="+username+"\n"+
		    						"host="+server+"\n"+
		    						"dfmtmode=rfc2833\n"+
		    						"fromdomain="+server+"\n"+
		    						"context=dnagatewayinbound\ninsecure=very\n");
		    			}
		    			out.close();
		    		}catch (Exception e){
		    			// Blast -- something went wrong
		    			Log.e(MSG_TAG,"Exception happened while updating DNA Gateway Configuration:"+e);
		    		}
		    		// Restart asterisk: restartAdhoc() is an overkill, but will do the trick.
					if (application.wifiRadio.getCurrentMode() == WifiMode.Adhoc)
						SetupActivity.this.application.restartAdhoc();
		    	}
		    	else if (key.equals("lannetworkpref")) {
		    		String lannetwork = sharedPreferences.getString("lannetworkpref", SetupActivity.this.application.DEFAULT_LANNETWORK);
		    		if (lannetwork.equals(SetupActivity.this.currentLAN) == false) {
						restartAdhoc();
						SetupActivity.this.currentLAN = lannetwork;
		    		}
				} else if (key.equals("routingImpl")) {
					try {
						application.meshManager.setRouting();
					} catch (Exception e) {
						Log.e("BatPhone",
								"Failure while changing routing implementation",
								e);
					}
				}
			}
		}).start();
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
		SubMenu clear = menu.addSubMenu(0, 1, 1, "Release Phone Numbers");
		clear.setIcon(drawable.ic_menu_close_clear_cancel);
    	return supRetVal;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d(MSG_TAG, "Menuitem:getId  -  "+menuItem.getItemId()+" -- "+menuItem.getTitle());
		switch (menuItem.getItemId()) {
		case 0:
    		new Thread(new Runnable(){
				public void run() {
    				SetupActivity.this.application.installFiles();
    			}
    		}).start();
			break;
		case 1: {
			String message = "Cleared DNA claimed phone number database.";

			java.io.File file;
			boolean deleted = true;
			try {
				file = new java.io.File(
						"/data/data/org.servalproject/tmp/myNumber.tmp");
				deleted &= file.delete();
			} catch (Exception e) {
				message = "Unable to erase myNumber.tmp";
			}
			try {
				file = new java.io.File(
						"/data/data/org.servalproject/var/hlr.dat");
				deleted &= file.delete();
			} catch (Exception e) {
				message = "Unable to DNA data file";
			}
			try {
				if (application.coretask.isNatEnabled()
						&& application.coretask.isProcessRunning("bin/batmand")) {
					// Show RestartDialog
					SetupActivity.this.dialogHandler
							.sendEmptyMessage(0);
					// Restart Adhoc
					SetupActivity.this.application.restartAdhoc();
					// Dismiss RestartDialog
					SetupActivity.this.dialogHandler
							.sendEmptyMessage(1);
				}
			} catch (Exception ex) {
				message = "Unable to restart BatPhone!";
			}
			SetupActivity.this.application.displayToastMessage(message);
		}
    	}
		return supRetVal;
    }
}
