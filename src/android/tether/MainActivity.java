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

import android.R.drawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class MainActivity extends Activity {
	
	private TetherApplication application = null;
	private ProgressDialog progressDialog;

	private ImageView startBtn = null;
	private OnClickListener startBtnListener = null;
	private ImageView stopBtn = null;
	private OnClickListener stopBtnListener = null;
	private TextView radioModeLabel = null;
	private ImageView radioModeImage = null;
	private TextView progressTitle = null;
	private TextView progressText = null;
	private ProgressBar progressBar = null;
	private RelativeLayout downloadUpdateLayout = null;
	private RelativeLayout batteryTemperatureLayout = null;
	
	private RelativeLayout trafficRow = null;
	private TextView downloadText = null;
	private TextView uploadText = null;
	private TextView downloadRateText = null;
	private TextView uploadRateText = null;
	private TextView batteryTemperature = null;
	
	private TableRow startTblRow = null;
	private TableRow stopTblRow = null;
	
	private ScaleAnimation animation = null;
	
	private static int ID_DIALOG_STARTING = 0;
	private static int ID_DIALOG_STOPPING = 1;
	
	public static final int MESSAGE_CHECK_LOG = 1;
	public static final int MESSAGE_CANT_START_TETHER = 2;
	public static final int MESSAGE_DOWNLOAD_STARTING = 3;
	public static final int MESSAGE_DOWNLOAD_PROGRESS = 4;
	public static final int MESSAGE_DOWNLOAD_COMPLETE = 5;
	public static final int MESSAGE_DOWNLOAD_BLUETOOTH_COMPLETE = 6;
	public static final int MESSAGE_DOWNLOAD_BLUETOOTH_FAILED = 7;
	public static final int MESSAGE_TRAFFIC_START = 8;
	public static final int MESSAGE_TRAFFIC_COUNT = 9;
	public static final int MESSAGE_TRAFFIC_RATE = 10;
	public static final int MESSAGE_TRAFFIC_END = 11;
	
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
        this.radioModeImage = (ImageView)findViewById(R.id.radioModeImage);
        this.progressBar = (ProgressBar)findViewById(R.id.progressBar);
        this.progressText = (TextView)findViewById(R.id.progressText);
        this.progressTitle = (TextView)findViewById(R.id.progressTitle);
        this.downloadUpdateLayout = (RelativeLayout)findViewById(R.id.layoutDownloadUpdate);
        this.batteryTemperatureLayout = (RelativeLayout)findViewById(R.id.layoutBatteryTemp);
        
        this.trafficRow = (RelativeLayout)findViewById(R.id.trafficRow);
        this.downloadText = (TextView)findViewById(R.id.trafficDown);
        this.uploadText = (TextView)findViewById(R.id.trafficUp);
        this.downloadRateText = (TextView)findViewById(R.id.trafficDownRate);
        this.uploadRateText = (TextView)findViewById(R.id.trafficUpRate);
        this.batteryTemperature = (TextView)findViewById(R.id.batteryTempText);

        // Define animation
        animation = new ScaleAnimation(
                0.9f, 1, 0.9f, 1, // From x, to x, from y, to y
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(600);
        animation.setFillAfter(true); 
        animation.setStartOffset(0);
        animation.setRepeatCount(1);
        animation.setRepeatMode(Animation.REVERSE);

        // Startup-Check
        if (this.application.startupCheckPerformed == false) {
	        this.application.startupCheckPerformed = true;
	        
	    	// Check if required kernel-features are enabled
	    	if (!this.application.coretask.isNetfilterSupported()) {
	    		this.openNoNetfilterDialog();
	    		this.application.accessControlSupported = false;
	    		this.application.whitelist.remove();
	    	}
	    	else {
	    		// Check if access-control-feature is supported by kernel
	    		if (!this.application.coretask.isAccessControlSupported()) {
	    			this.openNoAccessControlDialog();
	    			this.application.accessControlSupported = false;
	    			this.application.whitelist.remove();
	    		}
	    	}
	    		
        	// Check root-permission, files
	    	if (!this.application.coretask.hasRootPermission())
	    		this.openNotRootDialog();
	    	
	    	// Check if binaries need to be updated
	    	if (this.application.binariesExists() == false || this.application.coretask.filesetOutdated()) {
	        	if (this.application.coretask.hasRootPermission()) {
	        		this.application.installFiles();
	        	}
	        }

	        // Check if native-library needs to be moved
	        this.application.renewLibrary();	    	
	    	
	        // Open donate-dialog
			this.openDonateDialog();
        
			// Check for updates
			this.application.checkForUpdate();
        }
        
        // Start Button
        this.startBtn = (ImageView) findViewById(R.id.startTetherBtn);
        this.startBtnListener = new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "StartBtn pressed ...");
		    	showDialog(MainActivity.ID_DIALOG_STARTING);
				new Thread(new Runnable(){
					public void run(){
						boolean started = MainActivity.this.application.startTether();
						MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STARTING);
						Message message = Message.obtain();
						if (started != true) {
							message.what = MESSAGE_CANT_START_TETHER;
						}
						else {
							// Make device discoverable if checked
							if (Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.ECLAIR) {
								boolean bluetoothPref = MainActivity.this.application.settings.getBoolean("bluetoothon", false);
								if (bluetoothPref) {
									boolean bluetoothDiscoverable = MainActivity.this.application.settings.getBoolean("bluetoothdiscoverable", false);
									if (bluetoothDiscoverable) {
										MainActivity.this.makeDiscoverable();
									}
								}
							}
							try {
								Thread.sleep(400);
							} catch (InterruptedException e) {
								// Taking a small nap
							}
							String wifiStatus = MainActivity.this.application.coretask.getProp("tether.status");
							if (wifiStatus.equals("running") == false) {
								message.what = MESSAGE_CHECK_LOG;
							}
						}
						MainActivity.this.viewUpdateHandler.sendMessage(message); 
					}
				}).start();
			}
		};
		this.startBtn.setOnClickListener(this.startBtnListener);

		// Stop Button
		this.stopBtn = (ImageView) findViewById(R.id.stopTetherBtn);
		this.stopBtnListener = new OnClickListener() {
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
		};
		this.stopBtn.setOnClickListener(this.stopBtnListener);

		// Toggles between start and stop screen
		this.toggleStartStop();
    }
    
    @Override
	public boolean onTrackballEvent(MotionEvent event){
		if (event.getAction() == MotionEvent.ACTION_DOWN){
			Log.d(MSG_TAG, "Trackball pressed ...");
			String tetherStatus = this.application.coretask.getProp("tether.status");
            if (!tetherStatus.equals("running")){
				new AlertDialog.Builder(this)
				.setMessage("Trackball pressed. Confirm tether start.")  
			    .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Log.d(MSG_TAG, "Trackball press confirmed ...");
						MainActivity.currentInstance.startBtnListener.onClick(MainActivity.currentInstance.startBtn);
					}
				}) 
			    .setNegativeButton("Cancel", null)  
			    .show();
			}
            else{
				new AlertDialog.Builder(this)
				.setMessage("Trackball pressed. Confirm tether stop.")  
			    .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Log.d(MSG_TAG, "Trackball press confirmed ...");
						MainActivity.currentInstance.stopBtnListener.onClick(MainActivity.currentInstance.startBtn);
					}
				})
			    .setNegativeButton("Cancel", null)  
			    .show();
            }
		}
		return true;
	}
	
	public void onStop() {
    	Log.d(MSG_TAG, "Calling onStop()");
		super.onStop();
	}

	public void onDestroy() {
    	Log.d(MSG_TAG, "Calling onDestroy()");
    	super.onDestroy();
		try {
			unregisterReceiver(this.intentReceiver);
		} catch (Exception ex) {;}    	
	}

	public void onResume() {
		Log.d(MSG_TAG, "Calling onResume()");
		this.showRadioMode();
		super.onResume();
		
		// Check, if the battery-temperatur should be displayed
		if(this.application.settings.getBoolean("batterytemppref", false) == false) {
	        // create the IntentFilter that will be used to listen
	        // to battery status broadcasts
	        this.intentFilter = new IntentFilter();
	        this.intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
	        registerReceiver(this.intentReceiver, this.intentFilter);
	        this.batteryTemperatureLayout.setVisibility(View.VISIBLE);
		}
		else {
			try {
				unregisterReceiver(this.intentReceiver);
			} catch (Exception ex) {;}
			this.batteryTemperatureLayout.setVisibility(View.INVISIBLE);
		}
	}
	
	private static final int MENU_SETUP = 0;
	private static final int MENU_LOG = 1;
	private static final int MENU_ABOUT = 2;
	private static final int MENU_ACCESS = 3;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu setup = menu.addSubMenu(0, MENU_SETUP, 0, getString(R.string.setuptext));
    	setup.setIcon(drawable.ic_menu_preferences);
    	if (this.application.accessControlSupported) { 
    		SubMenu accessctr = menu.addSubMenu(0, MENU_ACCESS, 0, getString(R.string.accesscontroltext));
    		accessctr.setIcon(drawable.ic_menu_manage);   
    	}
    	SubMenu log = menu.addSubMenu(0, MENU_LOG, 0, getString(R.string.logtext));
    	log.setIcon(drawable.ic_menu_agenda);
    	SubMenu about = menu.addSubMenu(0, MENU_ABOUT, 0, getString(R.string.abouttext));
    	about.setIcon(drawable.ic_menu_info_details);    	
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

    /**
     *Listens for intent broadcasts; Needed for the temperature-display
     */
     private IntentFilter intentFilter;

     private BroadcastReceiver intentReceiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent) {
             String action = intent.getAction();
             if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            	 int temp = (intent.getIntExtra("temperature", 0))+5;
            	 batteryTemperature.setText("" + (temp/10) + getString(R.string.temperatureunit));
             }
         }
     };

    public Handler viewUpdateHandler = new Handler(){
        public void handleMessage(Message msg) {
        	switch(msg.what) {
        	case MESSAGE_CHECK_LOG :
        		Log.d(MSG_TAG, "Error detected. Check log.");
        		MainActivity.this.application.displayToastMessage("Tethering started with errors! Please check 'Show log'.");
            	MainActivity.this.toggleStartStop();
            	break;
        	case MESSAGE_CANT_START_TETHER :
        		Log.d(MSG_TAG, "Unable to start tethering!");
        		MainActivity.this.application.displayToastMessage("Unable to start tethering. Please try again!");
            	MainActivity.this.toggleStartStop();
            	break;
        	case MESSAGE_TRAFFIC_START :
        		MainActivity.this.trafficRow.setVisibility(View.VISIBLE);
        		break;
        	case MESSAGE_TRAFFIC_COUNT :
        		MainActivity.this.trafficRow.setVisibility(View.VISIBLE);
	        	long uploadTraffic = ((TetherApplication.DataCount)msg.obj).totalUpload;
	        	long downloadTraffic = ((TetherApplication.DataCount)msg.obj).totalDownload;
	        	long uploadRate = ((TetherApplication.DataCount)msg.obj).uploadRate;
	        	long downloadRate = ((TetherApplication.DataCount)msg.obj).downloadRate;

	        	// Set rates to 0 if values are negative
	        	if (uploadRate < 0)
	        		uploadRate = 0;
	        	if (downloadRate < 0)
	        		downloadRate = 0;
	        	
        		MainActivity.this.uploadText.setText(MainActivity.this.formatCount(uploadTraffic, false));
        		MainActivity.this.downloadText.setText(MainActivity.this.formatCount(downloadTraffic, false));
        		MainActivity.this.downloadText.invalidate();
        		MainActivity.this.uploadText.invalidate();

        		MainActivity.this.uploadRateText.setText(MainActivity.this.formatCount(uploadRate, true));
        		MainActivity.this.downloadRateText.setText(MainActivity.this.formatCount(downloadRate, true));
        		MainActivity.this.downloadRateText.invalidate();
        		MainActivity.this.uploadRateText.invalidate();
        		break;
        	case MESSAGE_TRAFFIC_END :
        		MainActivity.this.trafficRow.setVisibility(View.INVISIBLE);
        		break;
        	case MESSAGE_DOWNLOAD_STARTING :
        		Log.d(MSG_TAG, "Start progress bar");
        		MainActivity.this.progressBar.setIndeterminate(true);
        		MainActivity.this.progressTitle.setText((String)msg.obj);
        		MainActivity.this.progressText.setText("Starting...");
        		MainActivity.this.downloadUpdateLayout.setVisibility(View.VISIBLE);
        		break;
        	case MESSAGE_DOWNLOAD_PROGRESS :
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
        	case MESSAGE_DOWNLOAD_BLUETOOTH_COMPLETE :
        		Log.d(MSG_TAG, "Finished bluetooth download.");
        		MainActivity.this.startBtn.setClickable(true);
        		MainActivity.this.radioModeLabel.setText("Bluetooth");
        		break;
        	case MESSAGE_DOWNLOAD_BLUETOOTH_FAILED :
        		Log.d(MSG_TAG, "FAILED bluetooth download.");
        		MainActivity.this.startBtn.setClickable(true);
        		MainActivity.this.application.preferenceEditor.putBoolean("bluetoothon", false);
        		MainActivity.this.application.preferenceEditor.commit();
        		// TODO: More detailed popup info.
        		MainActivity.this.application.displayToastMessage("No bluetooth module for your kernel! Please report your kernel version.");
        	default:
        		MainActivity.this.toggleStartStop();
        	}
        	super.handleMessage(msg);
        }
   };

   private void makeDiscoverable() {
       Log.d(MSG_TAG, "Making device discoverable ...");
       Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
       discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
       startActivity(discoverableIntent);
   }
   
   private void toggleStartStop() {
    	boolean dnsmasqRunning = false;
    	boolean pandRunning = false;
		try {
			dnsmasqRunning = this.application.coretask.isProcessRunning("bin/dnsmasq");
		} catch (Exception e) {
			MainActivity.this.application.displayToastMessage("Unable to check if dnsmasq is currently running!");
		}
		try {
			pandRunning = this.application.coretask.isProcessRunning("bin/pand");
		} catch (Exception e) {
			MainActivity.this.application.displayToastMessage("Unable to check if pand is currently running!");
		}
    	boolean natEnabled = this.application.coretask.isNatEnabled();
    	boolean usingBluetooth = this.application.settings.getBoolean("bluetoothon", false);
    	if ((dnsmasqRunning == true && natEnabled == true) ||
    			(usingBluetooth == true && pandRunning == true)){
    		this.startTblRow.setVisibility(View.GONE);
    		this.stopTblRow.setVisibility(View.VISIBLE);
    		// Animation
    		if (this.animation != null)
    			this.stopBtn.startAnimation(this.animation);

            // Checking, if "wired tether" is currently running
            String tetherMode = this.application.coretask.getProp("tether.mode");
            String tetherStatus = this.application.coretask.getProp("tether.status");
            if (tetherStatus.equals("running")) {
            	if (!(tetherMode.equals("wifi") == true || tetherMode.equals("bt") == true)) {
            		MainActivity.this.application.displayToastMessage("Wired-tethering seems to be running at the moment. Please disable it first!");
            	}
            }
            
            // Checking, if cyanogens usb-tether is currently running
            tetherStatus = this.application.coretask.getProp("tethering.enabled");
            if  (tetherStatus.equals("1")) {
            	MainActivity.this.application.displayToastMessage("USB-tethering seems to be running at the moment. Please disable it first: Settings -> Wireless & network setting -> Internet tethering.");
            }
            
            this.application.trafficCounterEnable(true);
            this.application.clientConnectEnable(true);
            this.application.dnsUpdateEnable(true);
            
    		this.application.showStartNotification();
    	}
    	else if (dnsmasqRunning == false && natEnabled == false) {
    		this.startTblRow.setVisibility(View.VISIBLE);
    		this.stopTblRow.setVisibility(View.GONE);
    		this.application.trafficCounterEnable(false);
    		// Animation
    		if (this.animation != null)
    			this.startBtn.startAnimation(this.animation);
    		// Notification
        	this.application.notificationManager.cancelAll();
    	}   	
    	else {
    		this.startTblRow.setVisibility(View.VISIBLE);
    		this.stopTblRow.setVisibility(View.VISIBLE);
    		MainActivity.this.application.displayToastMessage("Your phone is currently in an unknown state - try to reboot!");
    	}
    	this.showRadioMode();
    	System.gc();
    }
   
	private String formatCount(long count, boolean rate) {
		// Converts the supplied argument into a string.
		// 'rate' indicates whether is a total bytes, or bits per sec.
		// Under 2Mb, returns "xxx.xKb"
		// Over 2Mb, returns "xxx.xxMb"
		if (count < 1e6 * 2)
			return ((float)((int)(count*10/1024))/10 + (rate ? "kbps" : "kB"));
		return ((float)((int)(count*100/1024/1024))/100 + (rate ? "mbps" : "MB"));
	}
  
   	private void openNoNetfilterDialog() {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.nonetfilterview, null); 
		new AlertDialog.Builder(MainActivity.this)
        .setTitle("No Netfilter!")
        .setView(view)
        .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d(MSG_TAG, "Close pressed");
                        MainActivity.this.finish();
                }
        })
        .setNeutralButton("Ignore", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Log.d(MSG_TAG, "Override pressed");
                    MainActivity.this.application.installFiles();
                    MainActivity.this.application.displayToastMessage("Ignoring, note that this application will NOT work correctly.");
                }
        })
        .show();
   	}
   	
   	private void openNoAccessControlDialog() {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.noaccesscontrolview, null); 
		new AlertDialog.Builder(MainActivity.this)
        .setTitle("No Access Control!")
        .setView(view)
        .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d(MSG_TAG, "Close pressed");
                        MainActivity.this.finish();
                }
        })
        .setNeutralButton("Ignore", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Log.d(MSG_TAG, "Override pressed");
                    MainActivity.this.application.installFiles();
                    MainActivity.this.application.displayToastMessage("Access Control disabled.");
                }
        })
        .show();
   	}
   	
   	private void openNotRootDialog() {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.norootview, null); 
		new AlertDialog.Builder(MainActivity.this)
        .setTitle("Not Root!")
        .setView(view)
        .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d(MSG_TAG, "Close pressed");
                        MainActivity.this.finish();
                }
        })
        .setNeutralButton("Ignore", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Log.d(MSG_TAG, "Override pressed");
                    MainActivity.this.application.installFiles();
                    MainActivity.this.application.displayToastMessage("Ignoring, note that this application will NOT work correctly.");
                }
        })
        .show();
   	}
   
   	private void openAboutDialog() {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.aboutview, null); 
        TextView versionName = (TextView)view.findViewById(R.id.versionName);
        versionName.setText(this.application.getVersionName());        
		new AlertDialog.Builder(MainActivity.this)
        .setTitle("About")
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
	        .setView(view)
	        .setNeutralButton("Close", new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	                        Log.d(MSG_TAG, "Close pressed");
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

  	private void showRadioMode() {
  		boolean usingBluetooth = this.application.settings.getBoolean("bluetoothon", false);
  		if (usingBluetooth) {
  			this.radioModeImage.setImageResource(R.drawable.bluetooth);
  		} else {
  			this.radioModeImage.setImageResource(R.drawable.wifi);
  		}
  	}
	
   	public void openUpdateDialog(final String downloadFileUrl, final String fileName, final String message,
   	    final String updateTitle) {
		LayoutInflater li = LayoutInflater.from(this);
		Builder dialog;
		View view;
		view = li.inflate(R.layout.updateview, null);
        TextView messageView = (TextView) view.findViewById(R.id.updateMessage);
        TextView updateNowText = (TextView) view.findViewById(R.id.updateNowText);
        if (fileName.length() == 0)  // No filename, hide 'download now?' string
          updateNowText.setVisibility(View.GONE);
        messageView.setText(message);
        dialog = new AlertDialog.Builder(MainActivity.this)
        .setTitle(updateTitle)
        .setView(view);
        
        if (fileName.length() > 0) {
          // Display Yes/No for if a filename is available.
          dialog.setNeutralButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d(MSG_TAG, "No pressed");
            }
          });
          dialog.setNegativeButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d(MSG_TAG, "Yes pressed");
                MainActivity.this.application.downloadUpdate(downloadFileUrl, fileName);
            }
          });          
        } else
          dialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d(MSG_TAG, "Ok pressed");
            }
          });

        dialog.show();
   	}

}

