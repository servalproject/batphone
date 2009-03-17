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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.tether.data.ClientData;
import android.tether.system.CoreTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class AccessControlActivity extends ListActivity {
	
    private static ArrayList<ClientData> clientDataList = new ArrayList<ClientData>();
    
    private ImageButton saveBtn;
    private ImageButton refreshBtn;
    private CheckBox checkBoxAccess;
    
    private EfficientAdapter efficientAdapter;
    
    public static final String MSG_TAG = "TETHER -> AccessControlActivity";
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.accesscontrolview);

        // Save-Button
        this.saveBtn = (ImageButton)findViewById(R.id.ImgBtnSave);
		this.saveBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "SaveBtn pressed ...");
				ArrayList<String> whitelist = new ArrayList<String>();
				for (ClientData tmpClientData : clientDataList) {
					if (tmpClientData.isAccessAllowed()) {
						whitelist.add(tmpClientData.getMacAddress());
					}
				}
				if (AccessControlActivity.this.checkBoxAccess.isChecked()) {
					try {
						CoreTask.saveWhitelist(whitelist);
						if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(CoreTask.DATA_FILE_PATH+"/bin/dnsmasq")) {
							AccessControlActivity.this.restartSecuredWifi();
						}
					}
					catch (Exception ex) {
						AccessControlActivity.this.displayToastMessage("Unable to save whitelist-file!");
					}
				}
				else {
					if (CoreTask.whitelistExists()) {
						if (!CoreTask.removeWhitelist()) {
							AccessControlActivity.this.displayToastMessage("Unable to remove whitelist-file!");
						}
					}
				}
				AccessControlActivity.this.finish();
			}
		});
		
		// Refresh-Button
		this.refreshBtn = (ImageButton)findViewById(R.id.ImgBtnRefresh);
		this.refreshBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "RefreshBtn pressed ...");
				AccessControlActivity.this.updateListView();
				AccessControlActivity.this.efficientAdapter.update();
			}
		});

		this.updateListView();
        this.efficientAdapter = new EfficientAdapter(this);
		this.setListAdapter(this.efficientAdapter);
    }
	
	private void updateListView() {
		this.displayToastMessage("Refreshing client-list ...");
        // clientData
        clientDataList = new ArrayList<ClientData>();
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
        
        for (ClientData tmpClientData : clientDataList) {
        	Log.d(MSG_TAG, tmpClientData.getMacAddress()+" -- "+tmpClientData.getClientName()+" -- "+tmpClientData.getIpAddress());
        }
        
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu installBinaries = menu.addSubMenu(0, 0, 0, getString(R.string.installtext));
    	installBinaries.setIcon(R.drawable.install);
    	return supRetVal;
    }    
    
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		ClientData clientData = clientDataList.get(position);
		clientData.setAccessAllowed(!clientData.isAccessAllowed());
		clientDataList.set(position, clientData);
		//setListAdapter(new EfficientAdapter(this));
		this.updateListView();
        Log.d(MSG_TAG, "ListEntry selected - "+position); 
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
	
	private static class EfficientAdapter extends BaseAdapter {
       
    	private LayoutInflater inflater;
        private Bitmap iconConnected;
        private Bitmap iconDisconnected;
        
        public EfficientAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
        	inflater = LayoutInflater.from(context);

            // Icons bound to the rows.
            iconConnected = BitmapFactory.decodeResource(context.getResources(), R.drawable.connected);
            iconDisconnected = BitmapFactory.decodeResource(context.getResources(), R.drawable.disconnected);
        }

        /**
         * The number of items in the list is determined by the number of speeches
         * in our array.
         *
         * @see android.widget.ListAdapter#getCount()
         */
        public int getCount() {
        	return clientDataList.size();
        }

        /**
         * Since the data comes from an array, just returning the index is
         * sufficent to get at the data. If we were using a more complex data
         * structure, we would return whatever object represents one row in the
         * list.
         *
         * @see android.widget.ListAdapter#getItem(int)
         */
        public Object getItem(int position) {
        	return clientDataList.get(position);
        }

        /**
         * Use the array index as a unique id.
         *
         * @see android.widget.ListAdapter#getItemId(int)
         */
        public long getItemId(int position) {
            return position;
        }

        public void update() {
        	this.notifyDataSetChanged();
        }
        
        /**
         * Make a view to hold each row.
         *
         * @see android.widget.ListAdapter#getView(int, android.view.View,
         *      android.view.ViewGroup)
         */
        public View getView(int position, View convertView, ViewGroup parent) {
            // A ViewHolder keeps references to children views to avoid unneccessary calls
            // to findViewById() on each row.
            final ViewHolder holder;

            // When convertView is not null, we can reuse it directly, there is no need
            // to reinflate it. We only inflate a new View when the convertView supplied
            // by ListView is null.
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.clientrow, null);

                // Creates a ViewHolder and store references to the two children views
                // we want to bind data to.
                holder = new ViewHolder();
                holder.position = position;
                holder.macAddress = (TextView) convertView.findViewById(R.id.macaddress);
                holder.ipAddress = (TextView) convertView.findViewById(R.id.ipaddress);
                holder.icon = (ImageView) convertView.findViewById(R.id.icon);
                holder.clientName = (TextView) convertView.findViewById(R.id.clientname);
                
                holder.checkBoxAllowed = (CheckBox) convertView.findViewById(R.id.checkBoxAllowed);
                holder.checkBoxAllowed.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
						ClientData clientData = clientDataList.get(holder.position);
						clientData.setAccessAllowed(isChecked);
						clientDataList.set(holder.position, clientData);
					}
                });

                convertView.setTag(holder);
            } else {
                // Get the ViewHolder back to get fast access to the TextView
                // and the ImageView.
                holder = (ViewHolder) convertView.getTag();
            }

            // Bind the data efficiently with the holder.
            ClientData clientData = clientDataList.get(position);
            
            Log.d(MSG_TAG, clientData.getMacAddress()+" -- "+clientData.getClientName()+" -- "+clientData.getIpAddress());
            
            
            holder.macAddress.setText(clientData.getMacAddress());
            if (clientData.isConnected()) {
            	holder.icon.setImageBitmap(this.iconConnected);
            	if (clientData.getIpAddress() != null) {
            		holder.ipAddress.setText(clientData.getIpAddress());
            	}
            	if (clientData.getClientName() != null) {
            		holder.clientName.setText(clientData.getClientName());
            	}
            }
            else {
            	holder.icon.setImageBitmap(this.iconDisconnected);
            	holder.clientName.setText("- Unknown -");
        		holder.ipAddress.setText("- Not connected -");
            }
            if (clientData.isAccessAllowed()) {
            	holder.checkBoxAllowed.setChecked(true);

            }
            return convertView;
        }

        static class ViewHolder {
        	int position;
            TextView macAddress;
            TextView clientName;
            TextView ipAddress;
            ImageView icon;
            CheckBox checkBoxAllowed;
        }
    }
}