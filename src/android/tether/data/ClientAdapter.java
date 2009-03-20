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

package android.tether.data;

import java.util.ArrayList;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.tether.R;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class ClientAdapter extends BaseAdapter {
	
	public static final String MSG_TAG = "TETHER -> ClientAdapter";
	
	private LayoutInflater inflater;
    private Bitmap iconConnected;
    private Bitmap iconDisconnected;
    private ArrayList<ClientData> rows = new ArrayList<ClientData>();
    
    public boolean saveRequired = false;
    
	public ClientAdapter(Activity context, ArrayList<ClientData> rows) {
		super();
		this.rows = rows;
		this.inflater = LayoutInflater.from(context);
        // Icons bound to the rows.
        this.iconConnected = BitmapFactory.decodeResource(context.getResources(), R.drawable.connected);
        this.iconDisconnected = BitmapFactory.decodeResource(context.getResources(), R.drawable.disconnected);
	}

	public ArrayList<ClientData> getClientData() {
		return this.rows;
	}
	
	public void addClient(ClientData clientData) {
		Log.d(MSG_TAG, "addClient() called: position = "+clientData.getClientName());
		this.rows.add(clientData);
		this.notifyDataSetChanged();
	}
	
	public void removeClient(String mac) {
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
			rows.set(position, tmpClientData);
			saveRequired = true;
		}
	}
	
	public View getView(final int position, View returnView, ViewGroup parent) {
		Log.d(MSG_TAG, "getView() called: position = "+position);
		ClientData row = this.rows.get(position);

    	returnView = inflater.inflate(R.layout.clientrow, null);
		TextView macaddress = (TextView) returnView.findViewById(R.id.macaddress);
		TextView clientname = (TextView) returnView.findViewById(R.id.clientname);
		TextView ipaddress = (TextView) returnView.findViewById(R.id.ipaddress);
		ImageView icon = (ImageView) returnView.findViewById(R.id.icon);
		CheckBox checkBoxAllowed = (CheckBox) returnView.findViewById(R.id.checkBoxAllowed);
		
		checkBoxAllowed.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
				toggleChecked(position, isChecked);
			}
		});
	
		macaddress.setText(row.getMacAddress());
        if (row.isConnected()) {
        	icon.setImageBitmap(this.iconConnected);
        	if (row.getIpAddress() != null) {
        		ipaddress.setText(row.getIpAddress());
        	}
        	if (row.getClientName() != null) {
        		clientname.setText(row.getClientName());
        	}
        }
        else {
        	icon.setImageBitmap(this.iconDisconnected);
        	clientname.setText("- Unknown -");
        	ipaddress.setText("- Not connected -");
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

