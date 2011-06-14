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

import org.servalproject.batman.Batman;
import org.servalproject.batman.Olsr;
import org.servalproject.system.Configuration;

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
import android.preference.PreferenceManager;
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

    private static int ID_DIALOG_RESTARTING = 2;

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
          @Override
		public boolean onPreferenceChange(Preference preference,
          Object newValue) {
            String message = validateSSID(newValue.toString());
            if(!message.equals("")) {
              SetupActivity.this.application.displayToastMessage(message);
              return false;
            }
            return true;
        }});

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
	            @Override
				public void afterTextChanged(Editable s) {
	            	// Nothing
	            }
		        @Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		        	// Nothing
		        }
		        @Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
		        	if (s.length() < 8 || s.length() > 30) {
		        		SetupActivity.this.prefPassphrase.getEditText().setTextColor(Color.RED);
		        	}
		        	else {
		        		SetupActivity.this.prefPassphrase.getEditText().setTextColor(origTextColorPassphrase);
		        	}
		        }
	        });

	        this.prefPassphrase.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener(){
	        	@Override
				public boolean onPreferenceChange(Preference preference,
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
	            @Override
				public void afterTextChanged(Editable s) {
	            	// Nothing
	            }
		        @Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		        	// Nothing
		        }
		        @Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
		        	if (s.length() == 13) {
		        		SetupActivity.this.prefPassphrase.getEditText().setTextColor(origTextColorPassphrase);
		        	}
		        	else {
		        		 SetupActivity.this.prefPassphrase.getEditText().setTextColor(Color.RED);
		        	}
		        }
	        });

	        this.prefPassphrase.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener(){
	        	@Override
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
        }
		Boolean bluetoothOn = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("bluetoothon", false);
		Message msg = Message.obtain();
		msg.what = bluetoothOn ? 0 : 1;
		this.setWifiPrefsEnableHandler.sendMessage(msg);
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


    @Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	updateConfiguration(sharedPreferences, key);
    }

    Handler restartingDialogHandler = new Handler(){
        @Override
		public void handleMessage(Message msg) {
        	if (msg.what == 0)
        		SetupActivity.this.showDialog(SetupActivity.ID_DIALOG_RESTARTING);
        	else
        		SetupActivity.this.dismissDialog(SetupActivity.ID_DIALOG_RESTARTING);
        	super.handleMessage(msg);
        	System.gc();
        }
    };

    private void updateConfiguration(final SharedPreferences sharedPreferences, final String key) {
    	new Thread(new Runnable(){
			@Override
			public void run(){
			   	String message = null;
			   	if (key.equals("hlrclear")) {
    				message = "Cleared DNA claimed phone number database.";

    				java.io.File file;
    				boolean deleted= true;
    				try { file = new java.io.File("/data/data/org.servalproject/tmp/isFirst.tmp");
    				deleted &= file.delete(); } catch (Exception e) { message = "Unable to erase isFirst.tmp";}
    				try { file = new java.io.File("/data/data/org.servalproject/tmp/location.tmp");
    				deleted &= file.delete(); } catch (Exception e) { message = "Unable to erase location.tmp";}
    				try { file = new java.io.File("/data/data/org.servalproject/tmp/myNumber.tmp");
    				deleted &= file.delete(); } catch (Exception e) { message = "Unable to erase myNumber.tmp";}
    				try { file = new java.io.File("/data/data/org.servalproject/tmp/newSid.tmp");
    				deleted &= file.delete(); } catch (Exception e) { message = "Unable to erase newSid.tmp";}
    				try { file = new java.io.File("/data/data/org.servalproject/var/hlr.dat");
    				deleted &= file.delete(); } catch (Exception e) { message = "Unable to DNA data file"; }
    				try{
    					if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/batmand")) {
    						// Show RestartDialog
    						SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
    						// Restart Adhoc
    						SetupActivity.this.application.restartAdhoc();
    						// Dismiss RestartDialog
    						SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
    					}
    				} catch (Exception ex) { message = "Unable to restart BatPhone!"; }
    				SetupActivity.this.application.displayToastMessage(message);
			   	}
			   	else if (key.equals("ssidpref")) {
		    		String newSSID = sharedPreferences.getString("ssidpref", "potato");
		    		if (SetupActivity.this.currentSSID.equals(newSSID) == false) {
	    				SetupActivity.this.currentSSID = newSSID;
	    				message = "SSID changed to '"+newSSID+"'.";
	    				try{
		    				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/batmand")) {
				    			// Show RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
		    					// Restart Adhoc
				    			SetupActivity.this.application.restartAdhoc();
				    			// Dismiss RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
		    				}
	    				}
	    				catch (Exception ex) {
	    					message = "Unable to restart BatPhone!";
	    				}
	    				SetupActivity.this.application.displayToastMessage(message);
		    		}
		    	}
			   	else if (key.equals("instrumentpref")) {
			   		Instrumentation.setEnabled(sharedPreferences.getBoolean("instrumentpref", false));
			   	}
			   	else if (key.equals("instrument_rec")){
			   		try{
			   			SetupActivity.this.application.restartDna();
			   		}catch(Exception e){
			   			SetupActivity.this.application.displayToastMessage(e.toString());
			   		}
			   	}
			   	else if (key.equals("ap_enabled")){
			   		boolean enabled=sharedPreferences.getBoolean("ap_enabled", false);
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
			   	}
		    	else if (key.equals("channelpref")) {
		    		String newChannel = sharedPreferences.getString("channelpref", "1");
		    		if (SetupActivity.this.currentChannel.equals(newChannel) == false) {
	    				SetupActivity.this.currentChannel = newChannel;
	    				message = "Channel changed to '"+newChannel+"'.";
	    				try{
		    				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/batmand")) {
				    			// Show RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
				    			// Restart Adhoc
		    					SetupActivity.this.application.restartAdhoc();
				    			// Dismiss RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
		    				}
	    				}
	    				catch (Exception ex) {
	    					message = "Unable to restart BatPhone!";
	    				}
	    				SetupActivity.this.application.displayToastMessage(message);
		    		}
		    	}
		    	else if (key.equals("wakelockpref")) {
					try {
						boolean enableWakeLock = sharedPreferences.getBoolean("wakelockpref", true);
						if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/batmand")) {
							if (enableWakeLock){
								SetupActivity.this.application
										.acquireWakeLock();
								message = "Wake-Lock is now enabled.";
							} else {
								SetupActivity.this.application.releaseWakeLock();
								message = "Wake-Lock is now disabled.";
							}
						}
					}
					catch (Exception ex) {
						message = "Unable to save Auto-Sync settings!";
					}
    				SetupActivity.this.application.displayToastMessage(message);
		    	}
		    	else if (key.equals("encpref")) {
		    		boolean enableEncryption = sharedPreferences.getBoolean("encpref", false);
		    		if (enableEncryption != SetupActivity.this.currentEncryptionEnabled) {
			    		// Restarting
						try{
							if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/batmand")) {
				    			// Show RestartDialog
								SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
				    			// Restart Adhoc
								SetupActivity.this.application.restartAdhoc();
				    			// Dismiss RestartDialog
								SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
							}
						}
						catch (Exception ex) {
							message=ex.toString();
						}

						SetupActivity.this.currentEncryptionEnabled = enableEncryption;
	    				SetupActivity.this.application.displayToastMessage(message);
		    		}
		    	}
		    	else if (key.equals("passphrasepref")) {
		    		String passphrase = sharedPreferences.getString("passphrasepref", SetupActivity.this.application.DEFAULT_PASSPHRASE);
		    		if (passphrase.equals(SetupActivity.this.currentPassphrase) == false) {
		    			// Restarting
						try{
							if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/batmand") && application.wpasupplicant.exists()) {
				    			// Show RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
				    			// Restart Adhoc
								SetupActivity.this.application.restartAdhoc();
				    			// Dismiss RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
							}
						}
						catch (Exception ex) {
							Log.e(MSG_TAG, "Exception happend while restarting service - Here is what I know: "+ex);
						}

						SetupActivity.this.currentPassphrase = passphrase;
	    				SetupActivity.this.application.displayToastMessage("Passphrase changed to '"+passphrase+"'.");
		    		}
		    	}
		    	else if (key.equals("txpowerpref")) {
		    		String transmitPower = sharedPreferences.getString("txpowerpref", "disabled");
		    		if (transmitPower.equals(SetupActivity.this.currentTransmitPower) == false) {
		    			// Restarting
						try{
							if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/batmand")) {
				    			// Show RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
				    			// Restart Adhoc
								SetupActivity.this.application.restartAdhoc();
				    			// Dismiss RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
							}
						}
						catch (Exception ex) {
							Log.e(MSG_TAG, "Exception happend while restarting service - Here is what I know: "+ex);
						}

						SetupActivity.this.currentTransmitPower = transmitPower;
	    				SetupActivity.this.application.displayToastMessage("Transmit power changed to '"+transmitPower+"'.");
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
		    			File file = new File("/data/data/org.servalproject/etc/asterisk/gatewaysip.conf");
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
					if (application.meshRunning)
						SetupActivity.this.application.restartAdhoc();
		    	}
		    	else if (key.equals("lannetworkpref")) {
		    		String lannetwork = sharedPreferences.getString("lannetworkpref", SetupActivity.this.application.DEFAULT_LANNETWORK);
		    		if (lannetwork.equals(SetupActivity.this.currentLAN) == false) {
		    			// Restarting
						try{
							if (application.coretask.isNatEnabled()
									&& application.routingImp != null
									&& application.routingImp.isRunning()) {
				    			// Show RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
				    			// Restart Adhoc
								SetupActivity.this.application.restartAdhoc();
				    			// Dismiss RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
							}
						}
						catch (Exception ex) {
							Log.e(MSG_TAG, "Exception happend while restarting service - Here is what I know: "+ex);
						}

						SetupActivity.this.currentLAN = lannetwork;
	    				SetupActivity.this.application.displayToastMessage("LAN-network changed to '"+lannetwork+"'.");
		    		}
				} else if (key.equals("routingImpl")) {
					try {
						String routing = sharedPreferences.getString(
								"routingImpl", "olsr");
						boolean running = (application.routingImp == null ? false
								: application.routingImp.isRunning());

						if (running)
							application.routingImp.stop();

						if (routing.equals("batman")) {
							Log.v("BatPhone", "Using batman routing");
							application.routingImp = new Batman(
									application.coretask);
						} else if (routing.equals("olsr")) {
							Log.v("BatPhone", "Using olsr routing");
							application.routingImp = new Olsr(
									application.coretask);
						} else
							throw new IllegalStateException(
									"Unknown routing implementation "
									+ routing);

						if (running)
							application.routingImp.start();
					} catch (Exception e) {
						Log.e("BatPhone",
								"Failure while changing routing implementation",
								e);
					}
				}
		    	else if (key.equals("bluetoothon")) {
		    		Boolean bluetoothOn = sharedPreferences.getBoolean("bluetoothon", false);
		    		Message msg = Message.obtain();
		    		msg.what = bluetoothOn ? 0 : 1;
		    		SetupActivity.this.setWifiPrefsEnableHandler.sendMessage(msg);
					try{
						if (application.coretask.isNatEnabled() && (application.coretask.isProcessRunning("bin/batmand") || application.coretask.isProcessRunning("bin/pand"))) {
			    			// Show RestartDialog
			    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);

			    			// Restart Adhoc
			    			SetupActivity.this.application.restartAdhoc();

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

    Handler  setWifiPrefsEnableHandler = new Handler() {
    	@Override
		public void handleMessage(Message msg) {
			PreferenceGroup wifiGroup = (PreferenceGroup)findPreference("wifiprefs");
			wifiGroup.setEnabled(msg.what == 1);
        	super.handleMessage(msg);
    	}
    };

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
    		new Thread(new Runnable(){
    			@Override
				public void run(){
    				SetupActivity.this.application.installFiles();
    			}
    		}).start();
    	}
    	return supRetVal;
    }
}
