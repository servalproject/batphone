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

import android.R.drawable;
import android.app.ListActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.tether.data.ClientData;
import android.tether.data.ClientAdapter;
import android.tether.system.CoreTask;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

public class AccessControlActivity extends ListActivity {
	
	private TetherApplication application = null;
	
	private ToggleButton buttonAC = null;
	private Button buttonApply = null;
	private TextView statusAC = null;
	private RelativeLayout applyFooterAC = null;

	private ClientAdapter clientAdapter;
	
	public CoreTask.Whitelist whitelist;
    
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
        this.whitelist = this.application.whitelist;
        
        // Status-Text
        this.statusAC = (TextView)findViewById(R.id.statusAC);
        
        // Footer
        this.applyFooterAC = (RelativeLayout)findViewById(R.id.layoutFooterAC);
        
        // Buttons
        this.buttonAC = (ToggleButton)findViewById(R.id.buttonAC);
        this.buttonAC.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (buttonAC.isChecked() == false) {
					Log.d(MSG_TAG, "Disable pressed ...");
					if (whitelist.remove()) {
						AccessControlActivity.this.application.displayToastMessage("Access-Control disabled.");
						AccessControlActivity.this.clientAdapter.refreshData(AccessControlActivity.this.getCurrentClientData());
						application.restartSecuredWifi();
						AccessControlActivity.this.application.preferenceEditor.putBoolean("acpref", false);
						AccessControlActivity.this.application.preferenceEditor.commit();					
						AccessControlActivity.this.toggleACHeader();
					}
				}
				else {
					Log.d(MSG_TAG, "Enable pressed ...");
					try {
						whitelist.touch();
						AccessControlActivity.this.application.displayToastMessage("Access-Control enabled.");
						AccessControlActivity.this.clientAdapter.refreshData(AccessControlActivity.this.getCurrentClientData());
						application.restartSecuredWifi();
						AccessControlActivity.this.application.preferenceEditor.putBoolean("acpref", true);
						AccessControlActivity.this.application.preferenceEditor.commit();
						AccessControlActivity.this.toggleACHeader();
					} catch (IOException e) {
						// nothing
					}
				}
			}
		});
        this.buttonApply = (Button)findViewById(R.id.buttonApplyAC);
        this.buttonApply.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "Apply pressed ...");
				AccessControlActivity.this.saveWhiteList();
				AccessControlActivity.this.clientAdapter.saveRequired = false;
				AccessControlActivity.this.toggleACFooter();
				AccessControlActivity.this.application.restartSecuredWifi();
			}
        });
        
        // Init Application
        this.application = (TetherApplication)this.getApplication();

        // Init Clientadapter
        AccessControlActivity.setCurrent(this);
        this.clientAdapter = new ClientAdapter(this, this.getCurrentClientData(), this.application);
		this.setListAdapter(this.clientAdapter);

		// "Toggle" header and footer
        this.toggleACHeader();
        this.toggleACFooter();
    }
    
	public void onStop() {
    	Log.d(MSG_TAG, "Calling onStop()");
    	if (this.clientAdapter.saveRequired) {
    		this.saveWhiteList();
    		this.clientAdapter.saveRequired = false;
    		/*
    		 * TODO
    		 * Need to check if this restart is really needed
    		 */
    		application.restartSecuredWifi();
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
    	if (whitelist.exists()) {
    		this.statusAC.setText("Access-Control is enabled.");
    		this.buttonAC.setChecked(true);
    	}
    	else {
    		this.statusAC.setText("Access-Control is disabled.");
    		this.buttonAC.setChecked(false);
    	}
    }
    
    public void toggleACFooter() {
    	if (this.clientAdapter.saveRequired)
    		this.applyFooterAC.setVisibility(View.VISIBLE);
    	else 
    		this.applyFooterAC.setVisibility(View.GONE);
    }
    
    // Handler
    Handler clientConnectHandler = new Handler() {
  	   public void handleMessage(Message msg) {
  		   AccessControlActivity.this.updateListView();
  	   }
    };
    
    private void saveWhiteList() {
    	Log.d(MSG_TAG, "Saving whitelist ...");
    	new Thread(new Runnable(){
			public void run(){
				Looper.prepare();
		    	if (whitelist.exists()) {
					whitelist.whitelist.clear();
					for (ClientData tmpClientData : AccessControlActivity.this.clientAdapter.getClientData()) {
						if (tmpClientData.isAccessAllowed()) {
							whitelist.whitelist.add(tmpClientData.getMacAddress());
						}
					}
					try {
						whitelist.save();
						if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/dnsmasq")) {
							application.restartSecuredWifi();
						}
					}
					catch (Exception ex) {
						application.displayToastMessage("Unable to save whitelist-file!");
					}
				}
				else {
					if (whitelist.exists()) {
						if (!whitelist.remove()) {
							application.displayToastMessage("Unable to remove whitelist-file!");
						}
					}
				}
				application.displayToastMessage("Access-Control Configuration saved!");
				Looper.loop();
			}
		}).start();
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
        Hashtable<String,ClientData> leases = null;
        try {
			leases = application.coretask.getLeases();
		} catch (Exception e) {
			AccessControlActivity.this.application.displayToastMessage("Unable to read leases-file!");
		}
        if (whitelist != null) {
	        for (String macAddress : whitelist.get()) {
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
        
        // Reset client-mac-lists
        this.application.resetClientMacLists();
        
        return clientDataList;
	}
	
	private static final int MENU_RELOAD_CLIENTS = 0;
	private static final int MENU_APPLY = 1;
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu refreshClientList = menu.addSubMenu(0, MENU_RELOAD_CLIENTS, 0, getString(R.string.reloadclientlisttext));
    	refreshClientList.setIcon(drawable.ic_menu_revert);
    	SubMenu saveWhitelist = menu.addSubMenu(0, MENU_APPLY, 0, getString(R.string.applywhitelisttext));
    	saveWhitelist.setIcon(drawable.ic_menu_save);
    	return supRetVal;
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d(MSG_TAG, "Menuitem:getId  -  "+menuItem.getItemId()); 
    	switch (menuItem.getItemId()) {
	    	case MENU_APPLY :
	    		this.saveWhiteList();
	    		this.clientAdapter.saveRequired = false;
	    		this.toggleACFooter();
	    		/*
	    		 * TODO
	    		 * Need to check if this restart is really needed
	    		 */
	    		application.restartSecuredWifi();
	    		break;
	    	case MENU_RELOAD_CLIENTS : 
	    		this.clientAdapter.refreshData(AccessControlActivity.this.getCurrentClientData());
    	}
    	return supRetVal;
    }    
}