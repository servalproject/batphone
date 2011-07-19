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
import java.util.Set;

import org.servalproject.system.Chipset;
import org.servalproject.system.ChipsetDetection;
import org.servalproject.system.WiFiRadio;
import org.servalproject.system.WifiMode;

import android.R.drawable;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

public class SetupActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private ServalBatPhoneApplication application = null;

	public static final String MSG_TAG = "ADHOC -> SetupActivity";

    private String currentSSID;
    private String currentChannel;
    private String currentLAN;
    private String currentTransmitPower;

    private EditTextPreference prefSSID;

	private static final int ID_DIALOG_UPDATING = 1;
	private static final int ID_DIALOG_RESTARTING = 2;
	private int currentDialog = 0;

    private WifiApControl apControl;
    private CheckBoxPreference apPref;
	private ListPreference wifiMode;

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
		this.currentLAN = this.application.settings.getString("lannetworkpref",
				ServalBatPhoneApplication.DEFAULT_LANNETWORK);
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

		{
			// add entries to the chipset list based on the detect scripts
			final ListPreference chipsetPref = (ListPreference) findPreference("chipset");
			List<CharSequence> entries = new ArrayList<CharSequence>();
			entries.add("Automatic");
			final ChipsetDetection detection = ChipsetDetection.getDetection();
			final Set<Chipset> chipsets = detection.getChipsets();
			for (Chipset chipset : chipsets) {
				entries.add(chipset.chipset);
			}
			String values[] = entries.toArray(new String[entries.size()]);
			chipsetPref.setEntries(values);
			chipsetPref.setEntryValues(values);
			chipsetPref.setSummary(detection.getChipset());

			chipsetPref
					.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
						@Override
						public boolean onPreferenceChange(
								Preference preference, Object newValue) {
							String value = (String) newValue;
							dialogHandler.sendEmptyMessage(ID_DIALOG_UPDATING);
							boolean ret = false;

							// force a re-test of root permission
							application.coretask.testRootPermission();

							if (value.equals("Automatic")) {
								chipsetPref.setSummary(detection.getChipset());
								detection.identifyChipset();
								ret = true;
							} else {
								for (Chipset chipset : chipsets) {
									if (chipset.chipset.equals(value)) {
										ret = detection.testForChipset(chipset);
										if (ret) {
											detection.setChipset(chipset);
										}
										break;
									}
								}
							}
							setAvailableWifiModes();

							SetupActivity.this.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									chipsetPref.setSummary(detection
											.getChipset());
								}
							});

							dialogHandler.sendEmptyMessage(0);
							return ret;
						}
					});
		}

		this.wifiMode = (ListPreference) findPreference("wifi_mode");
		setAvailableWifiModes();

    	// check if personal AP is enabled
		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		apControl = WifiApControl.getApControl(wifi);

		apPref=(CheckBoxPreference) findPreference("ap_enabled");
		apPref.setEnabled(apControl!=null);

    }

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(WiFiRadio.WIFI_MODE_ACTION)) {
				String mode = intent.getStringExtra(WiFiRadio.EXTRA_NEW_MODE);

				boolean changing = intent.getBooleanExtra(
						WiFiRadio.EXTRA_CHANGING, false);
				boolean changePending = intent.getBooleanExtra(
						WiFiRadio.EXTRA_CHANGE_PENDING, false);

				if (!changing && wifiMode.getValue() != mode) {
					ignoreChange = true;
					wifiMode.setValue(mode);
				}

				wifiMode.setSummary(changing ? "Changing..." : mode
						+ (changePending ? " ..." : ""));

				boolean enabled = application.isRunning() && !changing;
				wifiMode.setEnabled(enabled);
				wifiMode.setSelectable(enabled);
			}
		}
	};

	private void setAvailableWifiModes() {
		Chipset chipset = ChipsetDetection.getDetection().getWifiChipset();
		String values[] = new String[chipset.supportedModes.size()];
		int i = 0;

		for (WifiMode m : chipset.supportedModes) {
			values[i++] = m.toString();
		}

		wifiMode.setEntries(values);
		wifiMode.setEntryValues(values);
	}

    @Override
    protected void onResume() {
    	super.onResume();
    	SharedPreferences prefs=getPreferenceScreen().getSharedPreferences();

		if (apControl!=null){
			apPref.setChecked(apControl.getWifiApState()==WifiApControl.WIFI_AP_STATE_ENABLED);
		}

		IntentFilter filter = new IntentFilter();
		filter.addAction(WiFiRadio.WIFI_MODE_ACTION);
		this.registerReceiver(receiver, filter);

    	prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
    	Log.d(MSG_TAG, "Calling onPause()");
        super.onPause();
		this.unregisterReceiver(receiver);
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
		switch (id) {
		case ID_DIALOG_UPDATING:
			return ProgressDialog.show(this, "Updating Configuration", "Please wait while updating...", false, false);
		case ID_DIALOG_RESTARTING:
			return ProgressDialog.show(this, "Restarting BatPhone", "Please wait while restarting...", false, false);
		}
    	return null;
    }

	private boolean ignoreChange = false;
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (ignoreChange) {
			ignoreChange = false;
			return;
		}
    	updateConfiguration(sharedPreferences, key);
    }

	Handler dialogHandler = new Handler() {
        @Override
		public void handleMessage(Message msg) {
			if (msg.what == 0) {
				if (currentDialog != 0)
					SetupActivity.this.dismissDialog(currentDialog);
			} else {
				SetupActivity.this.showDialog(msg.what);
			}
			currentDialog = msg.what;
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
			@Override
			public void run(){
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
					String lannetwork = sharedPreferences.getString(
							"lannetworkpref",
							ServalBatPhoneApplication.DEFAULT_LANNETWORK);
		    		if (lannetwork.equals(SetupActivity.this.currentLAN) == false) {
						restartAdhoc();
						SetupActivity.this.currentLAN = lannetwork;
		    		}
				} else if (key.equals("routingImpl")) {
					try {
						application.wifiRadio.setRouting();
					} catch (Exception e) {
						Log.e("BatPhone",
								"Failure while changing routing implementation",
								e);
					}
				} else if (key.equals("wifi_auto")) {
					application.wifiRadio.setAutoCycling(sharedPreferences
							.getBoolean("wifi_auto", true));
				} else if (key.equals("wifi_mode")) {
					try {
						String mode = sharedPreferences.getString("wifi_mode",
								"Off");
						application.wifiRadio.setWiFiMode(WifiMode
								.valueOf(mode));
					} catch (Exception e) {
						application.displayToastMessage(e.toString());
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
				@Override
				public void run() {
    				SetupActivity.this.application.installFiles();
    			}
    		}).start();
			break;
		case 1: {
			java.io.File file;
			boolean deleted = true;
			try {
				file = new java.io.File(
						"/data/data/org.servalproject/tmp/myNumber.tmp");
				deleted &= file.delete();
			} catch (Exception e) {
			}
			try {
				file = new java.io.File(
						"/data/data/org.servalproject/var/hlr.dat");
				deleted &= file.delete();
			} catch (Exception e) {
			}
			this.restartAdhoc();
		}
    	}
		return supRetVal;
    }
}
