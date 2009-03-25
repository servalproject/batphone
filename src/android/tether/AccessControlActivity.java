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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import android.app.ListActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.tether.data.ClientData;
import android.tether.data.ClientAdapter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class AccessControlActivity extends ListActivity {
	
	private TetherApplication application = null;
    
	private RelativeLayout layoutHeaderACEnabled = null;
	private RelativeLayout layoutHeaderACDisabled = null;
	
	private Button buttonACEnable = null;
	private Button buttonACDisable = null;

	private ClientAdapter clientAdapter;
    
    public static final String MSG_TAG = "TETHER -> AccessControlActivity";
    public static AccessControlActivity currentInstance = null;
    
    private static void setCurrent(AccessControlActivity current){
    	AccessControlActivity.currentInstance = current;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d(MSG_TAG, "Calling onCreate()");
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.accesscontrolview);
        
        // Init Application
        this.application = (TetherApplication)this.getApplication();
        
        // Header-Layouts
        this.layoutHeaderACDisabled = (RelativeLayout)findViewById(R.id.layoutHeaderACDisabled);
        this.layoutHeaderACEnabled = (RelativeLayout)findViewById(R.id.layoutHeaderACEnabled);
        
        // Buttons
        this.buttonACDisable = (Button)findViewById(R.id.buttonACDisable);
        this.buttonACDisable.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "Disable pressed ...");
				if (application.coretask.removeWhitelist()) {
					AccessControlActivity.this.displayToastMessage("Access-Control disabled.");
					AccessControlActivity.this.toggleACHeader();
					AccessControlActivity.this.clientAdapter.refreshData(AccessControlActivity.this.getCurrentClientData());
					AccessControlActivity.this.restartSecuredWifi();
					AccessControlActivity.this.application.preferenceEditor.putBoolean("acpref", false);
					AccessControlActivity.this.application.preferenceEditor.commit();					
				}
			}
		});
        
        this.buttonACEnable = (Button)findViewById(R.id.buttonACEnable);
        this.buttonACEnable.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "Enable pressed ...");
				try {
					application.coretask.touchWhitelist();
					AccessControlActivity.this.displayToastMessage("Access-Control enabled.");
					AccessControlActivity.this.toggleACHeader();
					AccessControlActivity.this.clientAdapter.refreshData(AccessControlActivity.this.getCurrentClientData());
					AccessControlActivity.this.restartSecuredWifi();
					AccessControlActivity.this.application.preferenceEditor.putBoolean("acpref", true);
					AccessControlActivity.this.application.preferenceEditor.commit();
				} catch (IOException e) {
					// nothing
				}
			}
		});
        
        // Init Application
        this.application = (TetherApplication)this.getApplication();
        AccessControlActivity.setCurrent(this);
		
		//this.updateListView();
        this.clientAdapter = new ClientAdapter(this, this.getCurrentClientData(), this.application);
		this.setListAdapter(this.clientAdapter);
    }
    
	public void onStop() {
    	Log.d(MSG_TAG, "Calling onStop()");
    	if (this.clientAdapter.saveRequired) {
    		this.saveWhiteList();
    		this.clientAdapter.saveRequired = false;
    		AccessControlActivity.this.restartSecuredWifi();
    	}
    	super.onStop();
	}
    
    @Override
    protected void onResume() {
    	Log.d(MSG_TAG, "Calling onResume()");
    	super.onResume();
    	this.toggleACHeader();
    	this.updateListView();
    }
    
    private void toggleACHeader() {
    	if (application.coretask.whitelistExists()) {
    		this.layoutHeaderACDisabled.setVisibility(View.GONE);
    		this.layoutHeaderACEnabled.setVisibility(View.VISIBLE);
    	}
    	else {
    		this.layoutHeaderACDisabled.setVisibility(View.VISIBLE);
    		this.layoutHeaderACEnabled.setVisibility(View.GONE);	
    	}
    }
    
    // Handler
    Handler clientConnectHandler = new Handler() {
  	   public void handleMessage(Message msg) {
  		   AccessControlActivity.this.updateListView();
  	   }
    };
    
    private void saveWhiteList() {
		Log.d(MSG_TAG, "Saving whitelist ...");
		if (application.coretask.whitelistExists()) {
			ArrayList<String> whitelist = new ArrayList<String>();
			for (ClientData tmpClientData : this.clientAdapter.getClientData()) {
				if (tmpClientData.isAccessAllowed()) {
					whitelist.add(tmpClientData.getMacAddress());
				}
			}
			try {
				application.coretask.saveWhitelist(whitelist);
				if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning(application.coretask.DATA_FILE_PATH+"/bin/dnsmasq")) {
					this.restartSecuredWifi();
				}
			}
			catch (Exception ex) {
				this.displayToastMessage("Unable to save whitelist-file!");
			}
		}
		else {
			if (application.coretask.whitelistExists()) {
				if (!application.coretask.removeWhitelist()) {
					this.displayToastMessage("Unable to remove whitelist-file!");
				}
			}
		}
		this.displayToastMessage("Access-Control Configuration saved!");
    }
    
	private void updateListView() {
		ArrayList<ClientData> clientDataAddList = this.application.getClientDataAddList();
		ArrayList<String> clientMacRemoveList = this.application.getClientMacRemoveList();
		for (ClientData tmpClientData : clientDataAddList) {
			this.clientAdapter.addClient(tmpClientData);
		}
		for (String tmpMac : clientMacRemoveList) {
			this.clientAdapter.removeClient(tmpMac);
		}
    }

	private ArrayList<ClientData> getCurrentClientData() {
        ArrayList<ClientData> clientDataList = new ArrayList<ClientData>();
        ArrayList<String> whitelist = null;
        try {
			whitelist = application.coretask.getWhitelist();
		} catch (Exception e) {
			AccessControlActivity.this.displayToastMessage("Unable to read whitelist-file!");
		}
        Hashtable<String,ClientData> leases = null;
        try {
			leases = application.coretask.getLeases();
		} catch (Exception e) {
			AccessControlActivity.this.displayToastMessage("Unable to read leases-file!");
		}
        if (whitelist != null) {
	        for (String macAddress : whitelist) {
	        	ClientData clientData = new ClientData();
	        	clientData.setConnected(false);
	        	clientData.setIpAddress("- Not connected -");
	        	if (leases.containsKey(macAddress)) {
	        		clientData = leases.get(macAddress);
	            	Log.d(MSG_TAG, clientData.isConnected()+" - "+clientData.getIpAddress());
	        		leases.remove(macAddress);
	        	}
	        	clientData.setAccessAllowed(true);
	        	clientData.setMacAddress(macAddress);
	        	clientDataList.add(clientData);
	        }
        }
        if (leases != null) {
	        Enumeration<String> enumLeases = leases.keys();
	        while (enumLeases.hasMoreElements()) {
	        	String macAddress = enumLeases.nextElement();
	        	clientDataList.add(leases.get(macAddress));
	        }
        }
        return clientDataList;
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu refreshClientList = menu.addSubMenu(0, 1, 0, getString(R.string.reloadclientlisttext));
    	refreshClientList.setIcon(R.drawable.refresh);
    	SubMenu saveWhitelist = menu.addSubMenu(0, 0, 0, getString(R.string.applywhitelisttext));
    	saveWhitelist.setIcon(R.drawable.apply);
    	return supRetVal;
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d(MSG_TAG, "Menuitem:getId  -  "+menuItem.getItemId()); 
    	if (menuItem.getItemId() == 0) {
    		this.saveWhiteList();
    		this.clientAdapter.saveRequired = false;
    		AccessControlActivity.this.restartSecuredWifi();
    	}
    	else if (menuItem.getItemId() == 1) {
    		this.clientAdapter.refreshData(AccessControlActivity.this.getCurrentClientData());
    	}
    	return supRetVal;
    }    
    
    private void restartSecuredWifi() {
    	try {
			if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning(application.coretask.DATA_FILE_PATH+"/bin/dnsmasq")) {
		    	Log.d(MSG_TAG, "Restarting iptables for access-control-changes!");
				if (!application.coretask.runRootCommand(application.coretask.DATA_FILE_PATH+"/bin/tether restartsecwifi")) {
					this.displayToastMessage("Unable to restart secured wifi!");
					return;
				}
			}
		} catch (Exception e) {
			// nothing
		}
    }
    
	public void displayToastMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	} 
}