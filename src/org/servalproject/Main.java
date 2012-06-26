/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import org.servalproject.PreparationWizard.Action;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.rhizome.RhizomeRetriever;
import org.servalproject.system.WifiMode;
import org.servalproject.wizard.Wizard;
import org.sipdroid.sipua.UserAgent;
import org.sipdroid.sipua.ui.Receiver;

import android.R.drawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class Main extends Activity {
	public ServalBatPhoneApplication app;
	private static final String PREF_WARNING_OK = "warningok";
	Button toggleButton;
	Button btncall;
	Button btnreset;
	Button btnSend;
	BroadcastReceiver mReceiver;

	private static final int ID_DIALOG_WARNING = 1;
	private static final int ID_DIALOG_REBOOT = 2;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.app = (ServalBatPhoneApplication) this.getApplication();
		setContentView(R.layout.main);

		RhizomeRetriever.createDirectories();

		if (false) {
			// Tell WiFi radio if the screen turns off for any reason.
			IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			if (mReceiver == null)
				mReceiver = new ScreenReceiver();
			registerReceiver(mReceiver, filter);
		}
		;

		btnSend = (Button) this.findViewById(R.id.btnsend);
		btnSend.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				try {
					File apk = new File(
							Main.this.getApplicationInfo().sourceDir);
					Intent intent = new Intent(Intent.ACTION_SEND);
					intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(apk));
					intent.setType("image/apk");
					intent.addCategory(Intent.CATEGORY_DEFAULT);
					Main.this.startActivity(intent);
				} catch (Exception e) {
					Log.e("BatPhone", e.getMessage(), e);
					app.displayToastMessage("Failed to send file: "+e.getMessage());
				}
			}
		});

		btncall = (Button) this.findViewById(R.id.btncall);
		btncall.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Main.this.startActivity(new Intent(Intent.ACTION_DIAL));
			}
		});

		btnreset = (Button) this.findViewById(R.id.btnreset);
		btnreset.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startActivity(new Intent(Main.this, Wizard.class));
				new Thread() {
					@Override
					public void run() {
						try {
							app.resetNumber();
						} catch (Exception e) {
							Log.e("BatPhone", e.toString(), e);
							app.displayToastMessage(e.toString());
						}
					}
				}.start();
			}
		});

		toggleButton = (Button) this.findViewById(R.id.btntoggle);
		toggleButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {

				State state = app.getState();

				Intent serviceIntent = new Intent(Main.this, Control.class);
				switch (state) {
				case On:
					stopService(serviceIntent);
					break;
				case Off:
					startService(serviceIntent);
					break;
				}

				// if Client mode ask the user if we should turn it off.
				if (state == State.On
						&& app.wifiRadio.getCurrentMode() == WifiMode.Client) {
					AlertDialog.Builder alert = new AlertDialog.Builder(
							Main.this);
					alert.setTitle("Stop Wifi");
					alert
							.setMessage("Would you like to turn wifi off completely to save power?");
					alert.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int whichButton) {
									new Thread() {
										@Override
										public void run() {
											try {
												app.wifiRadio
														.setWiFiMode(WifiMode.Off);
											} catch (Exception e) {
												Log.e("BatPhone", e.toString(),
														e);
												app.displayToastMessage(e
														.toString());
											}
										}
									}.start();
								}
							});
					alert.setNegativeButton("No", null);
					alert.show();
				}
			}
		});

	} // onCreate

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			int stateOrd = intent.getIntExtra(
					ServalBatPhoneApplication.EXTRA_STATE, 0);
			State state = State.values()[stateOrd];
			stateChanged(state);
		}
	};
	boolean registered = false;

	private void stateChanged(State state) {
		// TODO update display of On/Off button
		switch (state) {
		case Installing:
		case Upgrading:
		case Starting:
		case Stopping:
		case Broken:
			toggleButton.setEnabled(false);
			toggleButton.setText("PLEASE WAIT... (" + state + ")");
			break;
		case On:
			toggleButton.setEnabled(true);
			toggleButton.setText("SUSPEND");
			break;
		case Off:
			toggleButton.setEnabled(true);
			toggleButton.setText("START");
			break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (app.terminate_main) {
			app.terminate_main = false;
			finish();
			return;
		}

		dnaKilled();

		// Don't continue unless they've seen the warning
		if (!app.settings.getBoolean(PREF_WARNING_OK, false)) {
			Log.i(getLocalClassName(), "displaying warning dialog");
			showDialog(ID_DIALOG_WARNING);
		} else if (!dnaKilled()) {
			Log.i(getLocalClassName(), "displaying reboot dialog");
			showDialog(ID_DIALOG_REBOOT);
		} else {
			checkAppSetup();
		}
	}

	private boolean dnaKilled() {
		int dnaPort = 4110;
		int uid;
		try {
			// attempt to get current package uid
			PackageInfo pi;
			try {
				pi = getPackageManager().getPackageInfo(getPackageName(), 0);
				uid = pi.applicationInfo.uid;
				Log.i(getLocalClassName(), "found uid " + uid);
			} catch (final NameNotFoundException e) {
				Log.e(getLocalClassName(), "Error retrieving uid", e);
				return true;
			}
			FileInputStream fis = new FileInputStream("/proc/net/udp");
			DataInputStream in = new DataInputStream(fis);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			// Read File Line By Line
			int count = 0;
			while ((strLine = br.readLine()) != null) {
				count++;
				Log.i(getLocalClassName(),
						"proc net udp line: " + strLine);
				// skip the header line
				if (count > 1) {
					SocketEntry se = parseSocket(strLine);
					Log.i(getLocalClassName(), "local port " + se.local_port
							+ ", uid " + se.uid);
					if (se.local_port == dnaPort && uid != se.uid) {
						Log.i(getLocalClassName(),
								"found old DNA with different uid - need to reboot");
						return false;
					}
				}
			}
			// Close the input stream
			in.close();
		} catch (FileNotFoundException e) {
			Log.e(getLocalClassName(),
					"Error reading udp proc file - probably no udp connections",
					e);
		} catch (IOException e) {
			Log.e(getLocalClassName(), "Error reading udp proc file", e);
		}
		// we don't want to loop around if any exceptions are thrown
		Log.i(getLocalClassName(),
				"returning true");
		return true;
	}

	/**
	 * Represents a line in /proc/net/udp
	 *
	 * @author brendon
	 *
	 */
	private class SocketEntry {
		public int local_port;
		public int uid;
	}

	private SocketEntry parseSocket(String line) {
		SocketEntry se = new SocketEntry();
		String[] tokens = line.split("\\s+");
		if (tokens.length >= 3 && tokens[2] != null) {
			String locAddr = tokens[2];
			String[] locAddrSplit = locAddr.split(":");
			if (locAddrSplit[1] != null) {
				se.local_port = Integer.valueOf(locAddrSplit[1].trim(), 16)
						.intValue();
			}
			if (tokens.length >= 9 && tokens[8] != null) {
				se.uid = Integer.valueOf(tokens[8].trim()).intValue();
			}
		}
		return se;
	}

	/**
	 * Run initialisation procedures to setup everything after install.<br/>
	 * Called from onResume() and after agreeing Warning dialog
	 */
	private void checkAppSetup() {
		if (PreparationWizard.preparationRequired()
				|| !ServalBatPhoneApplication.wifiSetup) {
			// Start by showing the preparation wizard
			Intent prepintent = new Intent(this, PreparationWizard.class);
			prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(prepintent);
			return;
		}

		if (app.getSubscriberId() == null) {
			this.startActivity(new Intent(this, Wizard.class));
			// TODO: finish() here so that back button on Wizard exits app
		} else if (Receiver.call_state != UserAgent.UA_STATE_IDLE) {
			Receiver.moveTop();
		} else {
			if (!registered) {
				IntentFilter filter = new IntentFilter();
				filter.addAction(ServalBatPhoneApplication.ACTION_STATE);
				this.registerReceiver(receiver, filter);
				registered = true;
			}
			stateChanged(app.getState());
		}

		TextView pn = (TextView) this.findViewById(R.id.mainphonenumber);
		if (app.getPrimaryNumber() != null)
			pn.setText(app.getPrimaryNumber());
		else
			pn.setText("");

		if (app.showNoAdhocDialog) {
			// We can't figure out how to control adhoc
			// mode on this phone,
			// so warn the user.
			// XXX - Can't display dialog from this thread!
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder
					.setMessage("I could not figure out how to get ad-hoc WiFi working on your phone.  Some mesh services will be degraded.  Obtaining root access may help if you have not already done so.");
			builder.setTitle("No Ad-hoc WiFi :(");
			builder.setPositiveButton("ok", null);
			builder.show();
			app.showNoAdhocDialog = false;
		}

	}

	@Override
	protected void onPause() {
		super.onPause();
		if (registered) {
			this.unregisterReceiver(receiver);
			registered = false;
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		LayoutInflater li = LayoutInflater.from(this);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View view;
		switch (id) {
		case ID_DIALOG_WARNING:
			view = li.inflate(R.layout.warning_dialog, null);
			builder.setView(view);
			builder.setPositiveButton(R.string.agree,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int b) {
							dialog.dismiss();
							app.preferenceEditor.putBoolean(PREF_WARNING_OK,
									true);
							app.preferenceEditor.commit();
							checkAppSetup();
						}
					});
			builder.setNegativeButton(R.string.cancel,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int b) {
							dialog.dismiss();
							finish();
						}
					});
			builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					dialog.dismiss();
					finish();
				}
			});
			break;
		case ID_DIALOG_REBOOT:
			view = li.inflate(R.layout.reboot_dialog, null);
			builder.setView(view);
			builder.setPositiveButton(R.string.btnok,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int b) {
							dialog.dismiss();
							finish();
						}
					});
		}
		return builder.create();
	}

	private static final int MENU_SETUP = 0;
	private static final int MENU_PEERS = 1;
	private static final int MENU_LOG = 2;
	private static final int MENU_REDETECT = 3;
	private static final int MENU_RHIZOME = 4;
	private static final int MENU_ABOUT = 5;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean supRetVal = super.onCreateOptionsMenu(menu);
		SubMenu m;

		m = menu.addSubMenu(0, MENU_SETUP, 0, getString(R.string.setuptext));
		m.setIcon(drawable.ic_menu_preferences);

		m = menu.addSubMenu(0, MENU_PEERS, 0, "Peers");
		m.setIcon(drawable.ic_dialog_info);

		m = menu.addSubMenu(0, MENU_LOG, 0, getString(R.string.logtext));
		m.setIcon(drawable.ic_menu_agenda);

		m = menu.addSubMenu(0, MENU_REDETECT, 0,
				getString(R.string.redetecttext));
		m.setIcon(R.drawable.ic_menu_refresh);

		m = menu
				.addSubMenu(0, MENU_RHIZOME, 0, getString(R.string.rhizometext));
		m.setIcon(drawable.ic_menu_agenda);

		m = menu.addSubMenu(0, MENU_ABOUT, 0, getString(R.string.abouttext));
		m.setIcon(drawable.ic_menu_info_details);

		return supRetVal;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		boolean supRetVal = super.onOptionsItemSelected(menuItem);
		switch (menuItem.getItemId()) {
		case MENU_SETUP:
			startActivity(new Intent(this, SetupActivity.class));
			break;
		case MENU_PEERS:
			startActivity(new Intent(this, PeerList.class));
			break;
		case MENU_LOG:
			startActivity(new Intent(this, LogActivity.class));
			break;
		case MENU_REDETECT:
			// Clear out old attempt_ files
			File varDir = new File("/data/data/org.servalproject/var/");
			if (varDir.isDirectory())
				for (File f : varDir.listFiles()) {
					if (!f.getName().startsWith("attempt_"))
						continue;
					f.delete();
				}
			// Re-run wizard
			PreparationWizard.currentAction = Action.NotStarted;
			Intent prepintent = new Intent(this, PreparationWizard.class);
			prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(prepintent);
			break;
		case MENU_RHIZOME:
			// Check if there's a SD card, because no SD card will lead Rhizome
			// to crash - code from Android doc
			String state = Environment.getExternalStorageState();

			if (Environment.MEDIA_MOUNTED.equals(state)) {
				startActivity(new Intent(this, RhizomeRetriever.class));
			} else {
				app.displayToastMessage(getString(R.string.rhizomesdcard));
			}
			break;
		case MENU_ABOUT:
			this.openAboutDialog();
			break;
		}
		return supRetVal;
	}

	private void openAboutDialog() {
		LayoutInflater li = LayoutInflater.from(this);
		View view = li.inflate(R.layout.aboutview, null);

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("About");
		alert.setView(view);
		alert.setPositiveButton("Donate to Serval",
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int whichButton) {
						Uri uri = Uri
								.parse(getString(R.string.paypalUrlServal));
						startActivity(new Intent(Intent.ACTION_VIEW, uri));
					}
				});
		alert.setNegativeButton("Close", null);
		alert.show();
	}
}

class ScreenReceiver extends BroadcastReceiver {
	public static boolean screenOff;

	@Override
	public void onReceive(Context context, Intent intent) {
		System.out.println("onReceive ");
		if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
			screenOff = true;
			if (ServalBatPhoneApplication.context != null
					&& ServalBatPhoneApplication.context.wifiRadio != null)
				ServalBatPhoneApplication.context.wifiRadio.screenTurnedOff();
		} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
			screenOff = false;
		}
		Intent i = new Intent(context, UpdateService.class);
		i.putExtra("screen_state", screenOff);
		context.startService(i);
	}
}

class UpdateService extends Service {
	@Override
	public void onCreate() {
		super.onCreate();
		// register receiver that handles screen on and screen off logic
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		BroadcastReceiver mReceiver = new ScreenReceiver();
		registerReceiver(mReceiver, filter);
	}

	@Override
	public void onStart(Intent intent, int startId) {
		boolean screenOn = intent.getBooleanExtra("screen_state", false);
		if (!screenOn) {
			System.out.println("Screen is off");
		} else {
			System.out.println("Screen is on");
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
