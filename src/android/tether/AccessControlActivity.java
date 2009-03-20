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

import android.app.ListActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.tether.data.ClientData;
import android.tether.data.ClientAdapter;
import android.tether.system.CoreTask;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class AccessControlActivity extends ListActivity {
	
	private TetherApplication application = null;
    private CheckBox checkBoxAccess;
    
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
        
        AccessControlActivity.setCurrent(this);
        // Checkbox-Access
        this.checkBoxAccess = (CheckBox)findViewById(R.id.checkBoxAccess);
        this.checkBoxAccess.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
				Log.d(MSG_TAG, ">>> "+arg0.toString()+" - >>> "+arg1);
			}
        });
        if (CoreTask.whitelistExists()) {
        	this.checkBoxAccess.setChecked(true);
        }
		
		//this.updateListView();
        this.clientAdapter = new ClientAdapter(this, this.getCurrentClientData());
		this.setListAdapter(this.clientAdapter);
    }
    
    public void refreshList(){
    	Log.d(MSG_TAG, "RefreshBtn pressed ...");
		AccessControlActivity.this.updateListView();
    }
	
	public void onStop() {
    	Log.d(MSG_TAG, "Calling onStop()");
    	if (this.clientAdapter.saveRequired) {
    		this.saveWhiteList();
    		this.clientAdapter.saveRequired = false;
    	}
    	super.onStop();
	}
    
    @Override
    protected void onResume() {
    	Log.d(MSG_TAG, "Calling onResume()");
    	super.onResume();
    	this.updateListView();
    }
    
    // Handler
    Handler clientConnectHandler = new Handler() {
  	   public void handleMessage(Message msg) {
  		   AccessControlActivity.this.updateListView();
  	   }
    };
    
    private void saveWhiteList() {
		Log.d(MSG_TAG, "Saving whitelist ...");
		if (this.checkBoxAccess.isChecked()) {
			ArrayList<String> whitelist = new ArrayList<String>();
			for (ClientData tmpClientData : this.clientAdapter.getClientData()) {
				if (tmpClientData.isAccessAllowed()) {
					whitelist.add(tmpClientData.getMacAddress());
				}
			}
			try {
				CoreTask.saveWhitelist(whitelist);
				if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
					this.restartSecuredWifi();
				}
			}
			catch (Exception ex) {
				this.displayToastMessage("Unable to save whitelist-file!");
			}
		}
		else {
			if (CoreTask.whitelistExists()) {
				if (!CoreTask.removeWhitelist()) {
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
			whitelist = CoreTask.getWhitelist();
		} catch (Exception e) {
			AccessControlActivity.this.displayToastMessage("Unable to read whitelist-file!");
		}
        Hashtable<String,ClientData> leases = null;
        try {
			leases = CoreTask.getLeases();
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
    	SubMenu saveWhitelist = menu.addSubMenu(0, 0, 0, getString(R.string.savewhitelisttext));
    	saveWhitelist.setIcon(R.drawable.save);
    	return supRetVal;
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d(MSG_TAG, "Menuitem:getId  -  "+menuItem.getItemId()); 
    	if (menuItem.getItemId() == 0) {
    		this.saveWhiteList();
    	}
    	return supRetVal;
    }    
    
    private void restartSecuredWifi() {
    	if (!CoreTask.runRootCommand(CoreTask.DATA_FILE_PATH+"/bin/tether restartsecwifi")) {
    		this.displayToastMessage("Unable to restart secured wifi!");
    		return;
    	}
    }
    
	public void displayToastMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	} 
}