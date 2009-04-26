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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class MainActivity extends Activity {
	
	private TetherApplication application = null;
	private ProgressDialog progressDialog;

	private ImageButton startBtn = null;
	private ImageButton stopBtn = null;
	private TextView progressTitle = null;
	private TextView progressText = null;
	private ProgressBar progressBar = null;
	private RelativeLayout downloadUpdateLayout = null;
	
	private TableRow startTblRow = null;
	private TableRow stopTblRow = null;
	
	private static int ID_DIALOG_STARTING = 0;
	private static int ID_DIALOG_STOPPING = 1;

	public static final int MESSAGE_NO_DATA_CONNECTION = 1;
	public static final int MESSAGE_CANT_START_TETHER = 2;
	public static final int MESSAGE_DOWNLOAD_STARTING = 3;
	public static final int MESSAGE_DOWNLOAD_PROGRESS = 4;
	public static final int MESSAGE_DOWNLOAD_COMPLETE = 5;	
	
	public static final String MSG_TAG = "TETHER -> MainActivity";
	public static MainActivity currentInstance = null;

    private static void setCurrent(MainActivity current){
    	MainActivity.currentInstance = current;
    }
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d(MSG_TAG, "Calling onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Init Application
        this.application = (TetherApplication)this.getApplication();
        MainActivity.setCurrent(this);
        
        // Init Table-Rows
        this.startTblRow = (TableRow)findViewById(R.id.startRow);
        this.stopTblRow = (TableRow)findViewById(R.id.stopRow);
        this.progressBar = (ProgressBar)findViewById(R.id.progressBar);
        this.progressText = (TextView)findViewById(R.id.progressText);
        this.progressTitle = (TextView)findViewById(R.id.progressTitle);
        this.downloadUpdateLayout = (RelativeLayout)findViewById(R.id.layoutDownloadUpdate);
        
        // Startup-Check
        if (this.application.startupCheckPerformed == false) {
	        this.application.startupCheckPerformed = true;
        	// Checking root-permission, files
	        boolean filesetoutdated = false;
	        if (this.application.binariesExists() == false || this.application.coretask.filesetOutdated()) {
	        	if (this.application.coretask.hasRootPermission()) {
	        		if (this.application.coretask.filesetOutdated()) {
	        			filesetoutdated = true;
	        		}
	        		this.application.installBinaries();
	        	}
	        	else {
	        		this.openNotRootDialog();
	        	}
	        }
	        if (filesetoutdated) {
	        	this.openConfigRecoverDialog();
	        }
	        // Open donate-dialog
			this.openDonateDialog();
        
			// Check for updates
			this.application.checkForUpdate();
        }
        
        // Start Button
        this.startBtn = (ImageButton) findViewById(R.id.startTetherBtn);
		this.startBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "StartBtn pressed ...");
		    	showDialog(MainActivity.ID_DIALOG_STARTING);
				new Thread(new Runnable(){
					public void run(){
						MainActivity.this.application.disableWifi();
						if (MainActivity.this.application.isSyncDisabled()){
							MainActivity.this.application.disableSync();
						}
						int started = MainActivity.this.application.startTether();
						MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STARTING);
						Message message = new Message();
						if (started != 0) {
							message.what = started;
						}
						MainActivity.this.viewUpdateHandler.sendMessage(message); 
					}
				}).start();
			}
		});

		// Stop Button
		this.stopBtn = (ImageButton) findViewById(R.id.stopTetherBtn);
		this.stopBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "StopBtn pressed ...");
		    	showDialog(MainActivity.ID_DIALOG_STOPPING);
				new Thread(new Runnable(){
					public void run(){
						MainActivity.this.application.stopTether();
						MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STOPPING);
						MainActivity.this.viewUpdateHandler.sendMessage(new Message()); 
					}
				}).start();
			}
		});			
		this.toggleStartStop();
    }
    
	public void onStop() {
    	Log.d(MSG_TAG, "Calling onStop()");
		super.onStop();
	}

	public void onDestroy() {
    	Log.d(MSG_TAG, "Calling onDestroy()");
    	super.onDestroy();
	}

	private static final int MENU_SETUP = 0;
	private static final int MENU_LOG = 1;
	private static final int MENU_ABOUT = 2;
	private static final int MENU_ACCESS = 3;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu setup = menu.addSubMenu(0, MENU_SETUP, 0, getString(R.string.setuptext));
    	setup.setIcon(R.drawable.setup);
    	SubMenu accessctr = menu.addSubMenu(0, MENU_ACCESS, 0, getString(R.string.accesscontroltext));
    	accessctr.setIcon(R.drawable.acl);    	
    	SubMenu log = menu.addSubMenu(0, MENU_LOG, 0, getString(R.string.logtext));
    	log.setIcon(R.drawable.log);
    	SubMenu about = menu.addSubMenu(0, MENU_ABOUT, 0, getString(R.string.abouttext));
    	about.setIcon(R.drawable.about);    	
    	return supRetVal;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d(MSG_TAG, "Menuitem:getId  -  "+menuItem.getItemId()); 
    	switch (menuItem.getItemId()) {
	    	case MENU_SETUP :
		        startActivityForResult(new Intent(
		        		MainActivity.this, SetupActivity.class), 0);
		        break;
	    	case MENU_LOG :
		        startActivityForResult(new Intent(
		        		MainActivity.this, LogActivity.class), 0);
		        break;
	    	case MENU_ABOUT :
	    		this.openAboutDialog();
	    		break;
	    	case MENU_ACCESS :
		        startActivityForResult(new Intent(
		        		MainActivity.this, AccessControlActivity.class), 0);   		
    	}
    	return supRetVal;
    }    

    @Override
    protected Dialog onCreateDialog(int id) {
    	if (id == ID_DIALOG_STARTING) {
	    	progressDialog = new ProgressDialog(this);
	    	progressDialog.setTitle("Start Tethering");
	    	progressDialog.setMessage("Please wait while starting...");
	    	progressDialog.setIndeterminate(false);
	    	progressDialog.setCancelable(true);
	        return progressDialog;
    	}
    	else if (id == ID_DIALOG_STOPPING) {
	    	progressDialog = new ProgressDialog(this);
	    	progressDialog.setTitle("Stop Tethering");
	    	progressDialog.setMessage("Please wait while stopping...");
	    	progressDialog.setIndeterminate(false);
	    	progressDialog.setCancelable(true);
	        return progressDialog;  		
    	}
    	return null;
    }

    public Handler viewUpdateHandler = new Handler(){
        public void handleMessage(Message msg) {
        	switch(msg.what) {
        	case MESSAGE_NO_DATA_CONNECTION :
        		Log.d(MSG_TAG, "No mobile-data-connection established!");
        		MainActivity.this.application.displayToastMessage("No mobile-data-connection established!");
            	MainActivity.this.toggleStartStop();
            	break;
        	case MESSAGE_CANT_START_TETHER :
        		Log.d(MSG_TAG, "Unable to start tetering!");
        		MainActivity.this.application.displayToastMessage("Unable to start tethering!");
            	MainActivity.this.toggleStartStop();
            	break;
        	case MESSAGE_DOWNLOAD_STARTING :
        		Log.d(MSG_TAG, "Start progress bar");
        		MainActivity.this.progressBar.setIndeterminate(true);
        		MainActivity.this.progressTitle.setText((String)msg.obj);
        		MainActivity.this.progressText.setText("Starting...");
        		MainActivity.this.downloadUpdateLayout.setVisibility(View.VISIBLE);
        		break;
        	case MESSAGE_DOWNLOAD_PROGRESS :
        		Log.d(MSG_TAG, "Downloaded " + msg.arg1 + " of " + msg.arg2);
        		MainActivity.this.progressBar.setIndeterminate(false);
        		MainActivity.this.progressText.setText(msg.arg1 + "k /" + msg.arg2 + "k");
        		MainActivity.this.progressBar.setProgress(msg.arg1*100/msg.arg2);
        		break;
        	case MESSAGE_DOWNLOAD_COMPLETE :
        		Log.d(MSG_TAG, "Finished download.");
        		MainActivity.this.progressText.setText("");
        		MainActivity.this.progressTitle.setText("");
        		MainActivity.this.downloadUpdateLayout.setVisibility(View.GONE);
        		break;
        	default:
        		MainActivity.this.toggleStartStop();
        	}

        	super.handleMessage(msg);
        }
   };

   private void toggleStartStop() {
    	boolean dnsmasqRunning = false;
		try {
			dnsmasqRunning = this.application.coretask.isProcessRunning("bin/dnsmasq");
		} catch (Exception e) {
			MainActivity.this.application.displayToastMessage("Unable to check if dnsmasq is currently running!");
		}
    	boolean natEnabled = this.application.coretask.isNatEnabled();
    	if (dnsmasqRunning == true && natEnabled == true) {
    		this.startTblRow.setVisibility(View.GONE);
    		this.stopTblRow.setVisibility(View.VISIBLE);
    		// Notification
    		this.application.showStartNotification();
    	}
    	else if (dnsmasqRunning == false && natEnabled == false) {
    		this.startTblRow.setVisibility(View.VISIBLE);
    		this.stopTblRow.setVisibility(View.GONE);
    		
    		// Notification
        	this.application.notificationManager.cancelAll();
    	}   	
    	else {
    		this.startTblRow.setVisibility(View.VISIBLE);
    		this.stopTblRow.setVisibility(View.VISIBLE);
    		MainActivity.this.application.displayToastMessage("Your phone is currently in an unknown state - try to reboot!");
    	}
    }
    
   	private void openNotRootDialog() {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.norootview, null); 
		new AlertDialog.Builder(MainActivity.this)
        .setTitle("Not Root!")
        .setIcon(R.drawable.warning)
        .setView(view)
        .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d(MSG_TAG, "Close pressed");
                        MainActivity.this.finish();
                }
        })
        .setNeutralButton("Override", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Log.d(MSG_TAG, "Override pressed");
                    MainActivity.this.application.installBinaries();
                }
        })
        .show();
   	}
   
   	private void openAboutDialog() {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.aboutview, null); 
		new AlertDialog.Builder(MainActivity.this)
        .setTitle("About")
        .setIcon(R.drawable.about)
        .setView(view)
        .setNeutralButton("Donate", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d(MSG_TAG, "Donate pressed");
    					Uri uri = Uri.parse(getString(R.string.paypalUrl));
    					startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
        })
        .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d(MSG_TAG, "Close pressed");
                }
        })
        .show();  		
   	}
   	
   	private void openDonateDialog() {
   		if (this.application.showDonationDialog()) {
   			// Disable donate-dialog for later startups
   			this.application.preferenceEditor.putBoolean("donatepref", false);
   			this.application.preferenceEditor.commit();
   			// Creating Layout
			LayoutInflater li = LayoutInflater.from(this);
	        View view = li.inflate(R.layout.donateview, null); 
	        new AlertDialog.Builder(MainActivity.this)
	        .setTitle("Donate")
	        .setIcon(R.drawable.about)
	        .setView(view)
	        .setNeutralButton("Close", new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	                        Log.d(MSG_TAG, "Close pressed");
	                        MainActivity.this.application.displayToastMessage("Thanks, anyway ...");
	                }
	        })
	        .setNegativeButton("Donate", new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	                        Log.d(MSG_TAG, "Donate pressed");
	    					Uri uri = Uri.parse(getString(R.string.paypalUrl));
	    					startActivity(new Intent(Intent.ACTION_VIEW, uri));
	                }
	        })
	        .show();
   		}
   	}
   	
   	private void openConfigRecoverDialog() {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.recoverconfigview, null); 
		new AlertDialog.Builder(MainActivity.this)
        .setTitle("Recover Settings?")
        .setIcon(R.drawable.warning)
        .setView(view)
        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                	Log.d(MSG_TAG, "No pressed");
                	MainActivity.this.application.coretask.removeWpaSupplicant();
                }
        })
        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Log.d(MSG_TAG, "Yes pressed");
                    MainActivity.this.application.recoverConfig();
                }
        })
        .show();
   	}
   	
   	public void openUpdateDialog(final String downloadFileUrl, final String fileName) {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.updateview, null); 
		new AlertDialog.Builder(MainActivity.this)
        .setTitle("Update Application?")
        .setIcon(R.drawable.download)
        .setView(view)
        .setNeutralButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                	Log.d(MSG_TAG, "No pressed");
                }
        })
        .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Log.d(MSG_TAG, "Yes pressed");
                    MainActivity.this.application.downloadUpdate(downloadFileUrl, fileName);
                }
        })
        .show();
   	}
}

