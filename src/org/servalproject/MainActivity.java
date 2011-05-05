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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.R.drawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import org.servalproject.R;
import org.servalproject.batman.FileParser;
import org.servalproject.batman.PeerRecord;
import org.servalproject.batman.ServiceStatus;
import org.servalproject.dna.Dna;
import org.servalproject.dna.OpStat;
import org.servalproject.dna.Packet;
import org.servalproject.dna.SubscriberId;
import org.servalproject.dna.VariableResults;
import org.servalproject.dna.VariableType;
import org.servalproject.system.NativeTask;
import org.sipdroid.sipua.ui.Receiver;

import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class MainActivity extends Activity {

	private ServalBatPhoneApplication application = null;

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

	private TextView batteryTemperature = null;
	static boolean instrumentationMode = false;

	private TableRow startTblRow = null;
	private TableRow stopTblRow = null;
	private EditText batphoneNumber = null;

	private ScaleAnimation animation = null;

	private static final int ID_DIALOG_STARTING = 0;
	private static final int ID_DIALOG_STOPPING = 1;
	private static final int ID_DIALOG_INSTALLING = 2;
	private static final int ID_DIALOG_CONFIG = 3;

	public static final int MESSAGE_CHECK_LOG = 1;
	public static final int MESSAGE_CANT_START_ADHOC = 2;
	public static final int MESSAGE_DOWNLOAD_STARTING = 3;
	public static final int MESSAGE_DOWNLOAD_PROGRESS = 4;
	public static final int MESSAGE_DOWNLOAD_COMPLETE = 5;
	public static final int MESSAGE_DOWNLOAD_BLUETOOTH_COMPLETE = 6;
	public static final int MESSAGE_DOWNLOAD_BLUETOOTH_FAILED = 7;
	public static final int MESSAGE_INSTALLED = 12;

	public static final String MSG_TAG = "ADHOC -> MainActivity";
	public static MainActivity currentInstance = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(MSG_TAG, "Calling onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Init Application
		this.application = (ServalBatPhoneApplication)this.getApplication();
		currentInstance = this;

		// Init Table-Rows
		this.startTblRow = (TableRow)findViewById(R.id.startRow);
		this.stopTblRow = (TableRow)findViewById(R.id.stopRow);
		this.radioModeImage = (ImageView)findViewById(R.id.radioModeImage);
		this.progressBar = (ProgressBar)findViewById(R.id.progressBar);
		this.progressText = (TextView)findViewById(R.id.progressText);
		this.progressTitle = (TextView)findViewById(R.id.progressTitle);
		this.downloadUpdateLayout = (RelativeLayout)findViewById(R.id.layoutDownloadUpdate);
		this.batteryTemperatureLayout = (RelativeLayout)findViewById(R.id.layoutBatteryTemp);

		this.batteryTemperature = (TextView)findViewById(R.id.batteryTempText);
		MainActivity.instrumentationMode = this.application.settings.getBoolean("instrumentpref", false);
		this.batphoneNumber = (EditText)findViewById(R.id.batphoneNumberText);
		this.batphoneNumber.setText(application.getPrimaryNumber());
		this.batphoneNumber.setSelectAllOnFocus(true);
		this.batphoneNumber.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN &&
						keyCode == KeyEvent.KEYCODE_ENTER) {
					setNumber();
					return true;
				}
				return false;
			}
		});
		this.batphoneNumber.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				setNumber();
				return true;
			}
		});

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

		// Start Button

		this.startBtn = (ImageView) findViewById(R.id.startAdhocBtn);
		this.startBtnListener = new OnClickListener() {
			public void onClick(View v) {
				/* we'll deal with this properly later, for now just use the auto configured number
				try {
        	    	// PGS Not yet registered.
        	    	AlertDialog.Builder alert = new AlertDialog.Builder(currentInstance);

        	    	alert.setTitle("Choose your number");
        	    	alert.setMessage("Before you can use BatPhone, you must claim your telephone number and record a voice prompt.  Type your telephone number in the box below, then click Record to start and stop recording your voice prompt, and if you are happy, press OK.");

        	    	// Set an EditText view to get user input 
        	    	final EditText input = new EditText(currentInstance);
        	    	alert.setView(input);

        	    	alert.setNeutralButton("Record", new DialogInterface.OnClickListener() {
        	    	public void onClick(DialogInterface dialog, int whichButton) {
        	    	  Editable value = input.getText();
        	    	  // Do something with value! Start recording!
        	    	// PGS Perform an acoustic echo test
        	    	  
        	    	  if (doneRecording==0) { doneRecording=1; return; }
        				
        				int frequency = 11025;
        				  int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        				  int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
        				  File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/voicesig.pcm");
        				   
        				  // Delete any previous recording.
        				  if (file.exists()) file.delete();

        				  // Create the new file.
        				  try {
        				    file.createNewFile();
        				  } catch (IOException e) {
        				    throw new IllegalStateException("Failed to create " + file.toString());
        				  }
        				   
        				  try {
        				    // Create a DataOuputStream to write the audio data into the saved file.
        				    OutputStream os = new FileOutputStream(file);
        				    BufferedOutputStream bos = new BufferedOutputStream(os);
        				    DataOutputStream dos = new DataOutputStream(bos);
        				     
        				    // Create a new AudioRecord object to record the audio.
        				    int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration,  audioEncoding);
        				    AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 
        				                                              frequency, channelConfiguration, 
        				                                              audioEncoding, bufferSize);
        				   
        				    short[] buffer = new short[bufferSize];   
        				    audioRecord.startRecording();

        					// Then record until it stops playing
        				    doneRecording=0;
        					while(doneRecording==0) {
        					  int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
        				      for (int i = 0; i < bufferReadResult; i++)
        				        dos.writeShort(buffer[i]);
        					}
        				      
        				    audioRecord.stop();

        				    dos.close();
        				   
        				  } catch (Throwable t) {
        				    Log.e("AudioRecord","Recording Failed");
        				  }

        	    	  
        	    	  }
        	    	});

        	    	alert.setPositiveButton("Done", new DialogInterface.OnClickListener() {
            	    	  public void onClick(DialogInterface dialog, int whichButton) {
            	    	    // Stop recording
            	    		  doneRecording=1;
            	    	  }
            	    	});

        	    	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            	    	  public void onClick(DialogInterface dialog, int whichButton) {
            	    	    // Stop recording
            	    		  doneRecording=1;
            	    	  }
            	    	});
        	    	
        	    	alert.show();
				}
				*/

				Log.d(MSG_TAG, "StartBtn pressed ...");
				showDialog(MainActivity.ID_DIALOG_STARTING);
				new Thread(new Runnable(){
					public void run(){
						Message message = Message.obtain();
						if (MainActivity.this.application.startAdhoc()){
							if (!NativeTask.getProp("adhoc.status").equals("running")) {
								message.what = MESSAGE_CHECK_LOG;
							}
						}else
							message.what = MESSAGE_CANT_START_ADHOC;
			    		
						MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STARTING);
						MainActivity.this.viewUpdateHandler.sendMessage(message); 
					}
				}).start();
			}
		};
		this.startBtn.setOnClickListener(this.startBtnListener);

		// Stop Button
		this.stopBtn = (ImageView) findViewById(R.id.stopAdhocBtn);
		this.stopBtnListener = new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "StopBtn pressed ...");
				showDialog(MainActivity.ID_DIALOG_STOPPING);
				new Thread(new Runnable(){
					public void run(){
						MainActivity.this.application.stopAdhoc();
						MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STOPPING);
						MainActivity.this.viewUpdateHandler.sendMessage(new Message());
					}
				}).start();
			}
		};
		this.stopBtn.setOnClickListener(this.stopBtnListener);

		this.stopTblRow.setVisibility(View.GONE);
		this.startTblRow.setVisibility(View.INVISIBLE);

		// Run some post open things in another thread
		if (this.application.firstRun) {
			
			//settings.getBoolean("first_run", true); 
			showDialog(MainActivity.ID_DIALOG_INSTALLING);

			// Check root-permission, files
			if (!this.application.coretask.hasRootPermission())
				this.openNotRootDialog();
			
			new Thread(new Runnable(){
				public void run(){
					// Startup-Check
					MainActivity m = MainActivity.this;

					// Check if binaries need to be updated
					if (m.application.binariesExists() == false || m.application.coretask.filesetOutdated()) {
						if (m.application.coretask.hasRootPermission()) {
							m.application.installFiles();
						}
					}

					// Open donate-dialog
					// this.openDonateDialog();

					// Check for updates
					m.application.checkForUpdate();

					// allow start / stop now.
					Message msg=new Message();
					msg.what=MESSAGE_INSTALLED;
					MainActivity.this.viewUpdateHandler.sendMessage(msg);
//					MediaPlayer.create(MainActivity.this, R.raw.installed).start();
				}
			}).start();
		}
	}
	
	private void setNumber(){
		showDialog(MainActivity.ID_DIALOG_CONFIG);
		new Thread(new Runnable(){
			public void run(){
				application.setPrimaryNumber(batphoneNumber.getText().toString());
				MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_CONFIG);
			}
		}).start();
	}
	
	@Override
	public boolean onTrackballEvent(MotionEvent event){
		if (event.getAction() == MotionEvent.ACTION_DOWN){
			Log.d(MSG_TAG, "Trackball pressed ...");
			String adhocStatus = this.application.coretask.getProp("adhoc.status");
			if (!adhocStatus.equals("running")){
				new AlertDialog.Builder(this)
				.setMessage("Trackball pressed. Confirm BatPhone start.")  
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
				.setMessage("Trackball pressed. Confirm BatPhone stop.")  
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
		super.onResume();

		// Check, if the battery-temperature should be displayed
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
		
		// Toggles between start and stop screen
		this.toggleStartStop();
		if (this.startBtn.getVisibility()==View.VISIBLE)
			this.startBtn.requestFocus();
		else
			this.stopBtn.requestFocus();
	}

	private static final int MENU_SETUP = 0;
	private static final int MENU_SIP_SETUP = 1;
	private static final int MENU_PEERS = 2;
	private static final int MENU_LOG = 3;
	private static final int MENU_ABOUT = 4;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean supRetVal = super.onCreateOptionsMenu(menu);
		SubMenu m;
		
		m = menu.addSubMenu(0, MENU_SETUP, 0, getString(R.string.setuptext));
		m.setIcon(drawable.ic_menu_preferences);
		
		m = menu.addSubMenu(0, MENU_SIP_SETUP, 0, R.string.menu_settings);
		m.setIcon(drawable.ic_menu_preferences);
		
		m = menu.addSubMenu(0, MENU_PEERS, 0, "Peers");
		m.setIcon(drawable.ic_dialog_info);
		
		m = menu.addSubMenu(0, MENU_LOG, 0, getString(R.string.logtext));
		m.setIcon(drawable.ic_menu_agenda);
		
		m = menu.addSubMenu(0, MENU_ABOUT, 0, getString(R.string.abouttext));
		m.setIcon(drawable.ic_menu_info_details);
		
		return supRetVal;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		boolean supRetVal = super.onOptionsItemSelected(menuItem);
		Log.d(MSG_TAG, "Menuitem:getId  -  "+menuItem.getItemId()); 
		switch (menuItem.getItemId()) {
		case MENU_SETUP :
			startActivity(new Intent(
					MainActivity.this, SetupActivity.class));
			break;
		case MENU_SIP_SETUP:
			startActivity(new Intent(
					this, org.sipdroid.sipua.ui.Settings.class));
			break;
		case MENU_PEERS:
		{
			showPeerList();
			break;
		}	
		case MENU_LOG :
			startActivity(new Intent(
					MainActivity.this, LogActivity.class));
			break;
		case MENU_ABOUT :
			this.openAboutDialog();
			break;
		}
		return supRetVal;
	}

	private void showPeerList() {
		try {
			ArrayList<PeerRecord> peers=fileParser.getPeerList();
			
			AlertDialog.Builder alert=new AlertDialog.Builder(currentInstance);
			alert.setTitle("Peers");
			
			if (peers.isEmpty()){
				alert.setMessage("No Peers found");
			}else{
				// build a map from IP address to phone number via a dna query
				dna.setDynamicPeers(peers);
				
				final Map<InetAddress, String> peerDids = new HashMap<InetAddress, String>();
				
				dna.readVariable(null, "", VariableType.DIDs, (byte)-1, new VariableResults(){
					@Override
					public void result(SocketAddress peer, SubscriberId sid,
							VariableType varType, byte instance, InputStream value) {
						
						try{
							InetSocketAddress inetAddr=(InetSocketAddress) peer;
							peerDids.put(inetAddr.getAddress(), Packet.unpackDid(value));
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
				
				
				ArrayList<String> labels=new ArrayList<String>();
				final ArrayList<String> numbers=new ArrayList<String>();
				
				for (PeerRecord peer: peers){
					String phNumber=peerDids.get(peer.getAddress());
					numbers.add(phNumber);
					
					if (phNumber==null){
						phNumber = peer.getAddress().toString();
						phNumber = phNumber.substring(phNumber.indexOf('/')+1);
					}
					labels.add(phNumber+" ("+peer.getLinkScore()+")");
				}
			
				alert.setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String number = numbers.get(which);
						if (number!=null){
							Receiver.engine(MainActivity.this).call(number);
						}
					}
				});
			}
			alert.setPositiveButton("Ok", null);
			alert.show();
		} catch (Exception e) {
			Log.e("Batphone",e.toString(),e);
			application.displayToastMessage(e.toString());
		}
	}    

	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id){
		case ID_DIALOG_STARTING:
			return ProgressDialog.show(this, 
					"Starting BatPhone", "Please wait while starting...", 
					false, false);
		case ID_DIALOG_STOPPING:
			return ProgressDialog.show(this, 
					"Stopping BatPhone", "Please wait while stopping...", 
					false, false);
		case ID_DIALOG_INSTALLING:
			return ProgressDialog.show(this, 
					"Installing", "Please wait while additional components are installed...", 
					false, false);
		case ID_DIALOG_CONFIG:
			return ProgressDialog.show(this, 
					"Please Wait", "Changing configuration...", 
					false, false);
		}
		return null;
	}

	/**
	 *Listens for intent broadcasts; Needed for the temperature-display
	 */
	private IntentFilter intentFilter;
	FileParser fileParser = new FileParser(ServiceStatus.PEER_FILE_LOCATION);
	Dna dna=new Dna();

	private BroadcastReceiver intentReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
				int temp = (intent.getIntExtra("temperature", 0))+5;
				batteryTemperature.setText("" + (temp/10) + getString(R.string.temperatureunit));

				// Get battery levels and TX it
				if(instrumentationMode)
				{
					try {
						ArrayList<PeerRecord> peers=fileParser.getPeerList();
						dna.setDynamicPeers(peers);
						Packet p=new Packet();
						p.setSidDid(null, "");
						p.operations.add(new OpStat((short)0x0001,(int)intent.getIntExtra("level",0)));
						p.operations.add(new OpStat((short)0x0002,(int)intent.getIntExtra("scale",0)));
						p.operations.add(new OpStat((short)0x0003,(int)intent.getIntExtra("voltage",0)));
						p.operations.add(new OpStat((short)0x0004,(int)intent.getIntExtra("temperature",0)));
						p.operations.add(new OpStat((short)0x0005,(int)intent.getIntExtra("plugged",0)));
						p.operations.add(new OpStat((short)0x0006,(int)intent.getIntExtra("health",0)));
				
						dna.beaconParallel(p);
					} catch (IOException e) {
						// Ignore it
					}
				}
			}
		}
	};

	public Handler viewUpdateHandler = new Handler(){
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case MESSAGE_CHECK_LOG :
				Log.d(MSG_TAG, "Error detected. Check log.");
				MainActivity.this.application.displayToastMessage("BatPhone started with errors! Please check 'Show log'.");
				MainActivity.this.toggleStartStop();
				break;
			case MESSAGE_CANT_START_ADHOC :
				Log.d(MSG_TAG, "Unable to start BatPhone!");
				MainActivity.this.application.displayToastMessage("Unable to start BatPhone. Please try again!");
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
				break;
			case MESSAGE_INSTALLED:
				Editor edit = MainActivity.this.application.settings.edit();
				edit.putBoolean("first_run", false);
				edit.commit();
				MainActivity.this.batphoneNumber.setText(application.getPrimaryNumber());
				MainActivity.this.application.firstRun = false;
				MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_INSTALLING);
			default:
				MainActivity.this.toggleStartStop();
			}
			super.handleMessage(msg);
		}
	};

	private void toggleStartStop() {
		// wait until all additional files have been installed. 
		if (this.application.firstRun) return;
		if (this.application.meshRunning){
			this.startTblRow.setVisibility(View.GONE);
			this.stopTblRow.setVisibility(View.VISIBLE);
			// Animation
			if (this.animation != null)
				this.stopBtn.startAnimation(this.animation);
		}else{
			this.startTblRow.setVisibility(View.VISIBLE);
			this.stopTblRow.setVisibility(View.GONE);
			// Animation
			if (this.animation != null)
				this.startBtn.startAnimation(this.animation);
		}
		this.showRadioMode();
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
		.setNeutralButton("Donate to WiFi Tether", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				Log.d(MSG_TAG, "Donate pressed");
				Uri uri = Uri.parse(getString(R.string.paypalUrlWifiTether));
				startActivity(new Intent(Intent.ACTION_VIEW, uri));
			}
		})
		.setPositiveButton("Donate to Serval", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				Log.d(MSG_TAG, "Donate pressed");
				Uri uri = Uri.parse(getString(R.string.paypalUrlServal));
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

	@SuppressWarnings("unused")
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
			.setPositiveButton("Donate to Serval", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					Log.d(MSG_TAG, "Donate pressed");
					Uri uri = Uri.parse(getString(R.string.paypalUrlServal));
					startActivity(new Intent(Intent.ACTION_VIEW, uri));
				}
			})
			.setNegativeButton("Donate to Wifi Tether", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					Log.d(MSG_TAG, "Donate pressed");
					Uri uri = Uri.parse(getString(R.string.paypalUrlWifiTether));
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

