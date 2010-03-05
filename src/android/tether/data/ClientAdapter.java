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

package android.tether.data;

import java.util.ArrayList;

import android.graphics.Color;
import android.tether.AccessControlActivity;
import android.tether.R;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.tether.TetherApplication;

public class ClientAdapter extends BaseAdapter {
	
	public static final String MSG_TAG = "TETHER -> ClientAdapter";
	
	private LayoutInflater inflater;
    private ArrayList<ClientData> rows = new ArrayList<ClientData>();
    
    public boolean saveRequired = false;
    public boolean accessControlActive = false;
    
    public TetherApplication application;
    public AccessControlActivity accessControlActivity;
    
	public ClientAdapter(AccessControlActivity accessControlActivity, ArrayList<ClientData> rows, TetherApplication app) {
		super();
		this.accessControlActivity = accessControlActivity;
		this.application = app;
		this.accessControlActive = application.whitelist.exists();
		this.rows = rows;
		this.inflater = LayoutInflater.from(accessControlActivity);
	}

	public ArrayList<ClientData> getClientData() {
		return this.rows;
	}
	
	public synchronized void refreshData(ArrayList<ClientData> rows) {
		this.accessControlActive = application.whitelist.exists();
		this.rows = rows;
		this.notifyDataSetChanged();
	}
	
	public synchronized void addClient(ClientData clientData) {
		Log.d(MSG_TAG, "addClient() called: = "+clientData.getClientName());
		this.rows.add(clientData);
		this.notifyDataSetChanged();
	}
	
	public synchronized void removeClient(String mac) {
		for (int i=0;i<this.rows.size();i++) {
    		ClientData tmpClientData = this.rows.get(i);
    		if (tmpClientData.getMacAddress().equals(mac)) {
    			this.rows.remove(i);
    			break;
    		}
    	}
		this.notifyDataSetChanged();
	}
	
	public void toggleChecked(int position, boolean isChecked) {
		ClientData tmpClientData = this.rows.get(position);
		if (tmpClientData.isAccessAllowed() != isChecked) {
			tmpClientData.setAccessAllowed(isChecked);
			Log.d(MSG_TAG, "Client ==> "+tmpClientData.getClientName()+"-"+tmpClientData.isAccessAllowed());
			this.rows.set(position, tmpClientData);
			this.saveRequired = true;
			this.accessControlActivity.toggleACFooter();
		}
	}
	
	public View getView(final int position, View returnView, ViewGroup parent) {
		Log.d(MSG_TAG, "getView() called: position = "+position);
		ClientData row = this.rows.get(position);

    	returnView = inflater.inflate(R.layout.clientrow, null);
		TextView macaddress = (TextView) returnView.findViewById(R.id.macaddress);
		TextView clientname = (TextView) returnView.findViewById(R.id.clientname);
		TextView ipaddress = (TextView) returnView.findViewById(R.id.ipaddress);
		CheckBox checkBoxAllowed = (CheckBox) returnView.findViewById(R.id.checkBoxAllowed);
		if (this.accessControlActive == false) {
			checkBoxAllowed.setVisibility(View.GONE);
		}
		else {
			checkBoxAllowed.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
					toggleChecked(position, isChecked);
				}
			});
		}
		macaddress.setText(row.getMacAddress());
        if (row.isConnected()) {
        	// Change textcolor to green
        	macaddress.setTextColor(Color.rgb(0, 182, 39));
        	clientname.setTextColor(Color.rgb(0, 182, 39));
        	ipaddress.setTextColor(Color.rgb(0, 182, 39));       	
        	if (row.getIpAddress() != null) {
        		ipaddress.setText(row.getIpAddress());
        	}
        	if (row.getClientName() != null) {
        		clientname.setText(row.getClientName());
        	}
        }
        else {
        	// Set connected-icon
        	clientname.setText("- Unknown -");
        	ipaddress.setText("- Not connected -");
        	// Change textcolor to red
        	macaddress.setTextColor(Color.rgb(255, 34, 17));
        	clientname.setTextColor(Color.rgb(255, 34, 17));
        	ipaddress.setTextColor(Color.rgb(255, 34, 17));
        }
        if (row.isAccessAllowed()) {
        	checkBoxAllowed.setChecked(true);

        }
		return returnView;
	}

	public int getCount() {
		return rows.size();
	}

	public Object getItem(int position) {
		return rows.get(position);
	}

	public long getItemId(int position) {
		return position;
	}
}

