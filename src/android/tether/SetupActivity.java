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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.SharedPreferences;
import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.tether.data.ClientData;
import android.tether.system.CoreTask;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class SetupActivity extends Activity {
	
	private static final String DATA_FILE_PATH = "/data/data/android.tether";
    private static ArrayList<ClientData> clientDataList = new ArrayList<ClientData>();
    
    private ImageButton saveBtn;
    private CheckBox checkBoxSync;
    private EditText SSIDText;
    private Spinner ChanSpin;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setupview);

        // Save-Button
        this.saveBtn = (ImageButton)findViewById(R.id.ImgBtnSave);
		this.saveBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d("*** DEBUG ***", "SaveBtn pressed ...");
				ArrayList<String> whitelist = new ArrayList<String>();
				for (ClientData tmpClientData : clientDataList) {
					if (tmpClientData.isAccessAllowed()) {
						whitelist.add(tmpClientData.getMacAddress());
					}
				}
				//update SSID if it's changed
				if (!SetupActivity.this.getSSID().equals(SetupActivity.this.SSIDText.getText().toString())){
					try {
						SetupActivity.this.setSSID();
						if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(DATA_FILE_PATH+"/bin/dnsmasq")) {
							SetupActivity.this.displayToastMessage("New SSID will be used once tethering is stopped and restarted.");
						}
					}
					catch (Exception ex) {
						SetupActivity.this.displayToastMessage("Unable to save new SSID!");
					}
				}
				//update channel if it's changed
				if (!SetupActivity.this.getChan().equals(SetupActivity.this.ChanSpin.getSelectedItem().toString())){
					try {
						SetupActivity.this.setChan();
						if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(DATA_FILE_PATH+"/bin/dnsmasq")) {
							SetupActivity.this.displayToastMessage("New channel will be used once tethering is stopped and restarted.");
						}
					}
					catch (Exception ex) {
						SetupActivity.this.displayToastMessage("Unable to save new channel!");
					}
				}
				//save Auto-Sync status
				if(SetupActivity.this.getSync() != SetupActivity.this.checkBoxSync.isChecked()){
					try {
						SetupActivity.this.setSync();
						if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(DATA_FILE_PATH+"/bin/dnsmasq")) {
							SetupActivity.this.displayToastMessage("New Auto-Sync settings will be used once tethering is stopped and restarted.");
						}
					}
					catch (Exception ex) {
						SetupActivity.this.displayToastMessage("Unable to save Auto-Sync settings!");
					}
				}
				
				SetupActivity.this.finish();
			}
		});
		
        //SSID
        this.SSIDText = (EditText)findViewById(R.id.SSID);
        this.updateSSIDText();
        
        //Channel
        this.ChanSpin = (Spinner)findViewById(R.id.Chan);
        this.ChanSpin.setAdapter(new ArrayAdapter<String>(this,
        		android.R.layout.simple_spinner_item,
        		new String[] { "1","2","3","4","5","6","7","8","9","10","11","12","13","14" }));
        this.updateChanSelection();
        
        //Auto-Sync
        this.checkBoxSync = (CheckBox)findViewById(R.id.sync);
        this.updateSync();		
    }
	
	public void updateSync(){
		this.checkBoxSync.setChecked(this.getSync());
        this.checkBoxSync.invalidate();
	}
	
	public boolean getSync(){
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		return settings.getBoolean("sync", false);
	}
	
	public void setSync(){
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor e = settings.edit();
		e.putBoolean("sync", this.checkBoxSync.isChecked());
		e.commit();
	}
	
	public void updateSSIDText(){
		this.SSIDText.setText((CharSequence)this.getSSID());
        this.SSIDText.invalidate();
	}
    
    public String getSSID(){
    	String filename = DATA_FILE_PATH+"/conf/tiwlan.ini";
    	File inFile = new File(filename);
    	String currSSID = "undefined";
    	try{
        	InputStream is = new FileInputStream(inFile);
        	BufferedReader br = new BufferedReader(new InputStreamReader(is));
    		String s = br.readLine();
	    	while (s!=null){
	    		if (s.contains("dot11DesiredSSID")){
	    			currSSID = s.substring(s.indexOf("= ")+2).trim();
	    			return currSSID;
	    		}
	    		s = br.readLine();
	    	}
	    	is.close();
	    	br.close();
    	}
    	catch (Exception e){
    		//Nothing
    	}
    	return currSSID;
    }
    
    public void setSSID(){
    	String newSSID = this.SSIDText.getText().toString();
    	if (newSSID.contains("#") || newSSID.contains("`")){
    		SetupActivity.this.displayToastMessage("New SSID cannot contain '#' or '`'!");
    		this.SSIDText.setText(this.getSSID());
    		return;
    	}
    	String filename = DATA_FILE_PATH+"/conf/tiwlan.ini";
    	String fileString = "";
    	String s;
        try {
        	File inFile = new File(filename);
        	BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
        	while((s = br.readLine())!=null) {
        		if (s.contains("dot11DesiredSSID")){
	    			s = "dot11DesiredSSID = "+newSSID;
	    		}
        		s+="\n";
        		fileString += s;
			}
        	File outFile = new File(filename);
        	OutputStream out = new FileOutputStream(outFile);
        	out.write(fileString.getBytes());
        	out.close();
        	br.close();
		} catch (IOException e) {
			this.displayToastMessage("Couldn't install file - "+filename+"!");
		}
    }
    
    public void updateChanSelection(){
    	this.ChanSpin.setSelection(new Integer(this.getChan()) - 1);
        this.ChanSpin.invalidate();
	}
    
    public String getChan(){
    	String filename = DATA_FILE_PATH+"/conf/tiwlan.ini";
    	File inFile = new File(filename);
    	String currChan = "6";
    	try{
        	InputStream is = new FileInputStream(inFile);
        	BufferedReader br = new BufferedReader(new InputStreamReader(is));
    		String s = br.readLine();
	    	while (s!=null){
	    		if (s.contains("dot11DesiredChannel")){
	    			currChan = s.substring(s.indexOf("= ")+2).trim();
	    			return currChan;
	    		}
	    		s = br.readLine();
	    	}
	    	is.close();
	    	br.close();
    	}
    	catch (Exception e){
    		//Nothing
    	}
    	return currChan;
    }
    
    public void setChan(){
    	String filename = DATA_FILE_PATH+"/conf/tiwlan.ini";
    	String fileString = "";
    	String s;
        try {
        	File inFile = new File(filename);
        	BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
        	while((s = br.readLine())!=null) {
        		if (s.contains("dot11DesiredChannel")){
	    			s = "dot11DesiredChannel = "+this.ChanSpin.getSelectedItem().toString();
	    		}
        		s+="\n";
        		fileString += s;
			}
        	File outFile = new File(filename);
        	OutputStream out = new FileOutputStream(outFile);
        	out.write(fileString.getBytes());
        	out.close();
        	br.close();
		} catch (IOException e) {
			this.displayToastMessage("Couldn't install file - "+filename+"!");
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
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d("*** DEBUG ***", "Menuitem:getId  -  "+menuItem.getItemId()); 
    	if (menuItem.getItemId() == 0) {
    		SetupActivity.this.installBinaries();
    	}
    	return supRetVal;
    } 
	
    private void installBinaries() {
    	List<String> filenames = new ArrayList<String>();
    	// tether
    	this.copyBinary(DATA_FILE_PATH+"/bin/tether", R.raw.tether);
    	filenames.add("tether");
    	// dnsmasq
    	this.copyBinary(DATA_FILE_PATH+"/bin/dnsmasq", R.raw.dnsmasq);
    	filenames.add("dnsmasq");
    	// iptables
    	this.copyBinary(DATA_FILE_PATH+"/bin/iptables", R.raw.iptables);
    	filenames.add("iptables");
    	try {
			CoreTask.chmodBin(filenames);
		} catch (Exception e) {
			this.displayToastMessage("Unable to change permission on binary files!");
		}
    	// dnsmasq.conf
    	this.copyBinary(DATA_FILE_PATH+"/conf/dnsmasq.conf", R.raw.dnsmasq_conf);
    	// tiwlan.ini
    	this.copyBinary(DATA_FILE_PATH+"/conf/tiwlan.ini", R.raw.tiwlan_ini);
    	this.displayToastMessage("Binaries and config-files installed!");
    	this.updateSSIDText();
    	this.updateChanSelection();
    }
    
    private void copyBinary(String filename, int resource) {
    	File outFile = new File(filename);
    	InputStream is = this.getResources().openRawResource(resource);
    	byte buf[]=new byte[1024];
        int len;
        try {
        	OutputStream out = new FileOutputStream(outFile);
        	while((len = is.read(buf))>0) {
				out.write(buf,0,len);
			}
        	out.close();
        	is.close();
		} catch (IOException e) {
			this.displayToastMessage("Couldn't install file - "+filename+"!");
		}
    }
    
	public void displayToastMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	} 
}