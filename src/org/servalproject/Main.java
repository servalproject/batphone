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

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.account.AccountService;
import org.servalproject.rhizome.RhizomeMain;
import org.servalproject.servald.Identity;
import org.servalproject.system.WifiMode;
import org.servalproject.ui.ShareUsActivity;
import org.servalproject.ui.help.HelpActivity;
import org.servalproject.wizard.Wizard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

/**
 *
 * Main activity which presents the Serval launcher style screen. On the first
 * time Serval is installed, this activity ensures that a warning dialog is
 * presented and the user is taken through the setup wizard. Once setup has been
 * confirmed the user is taken to the main screen.
 *
 * @author Paul Gardner-Stephen <paul@servalproject.org>
 * @author Andrew Bettison <andrew@servalproject.org>
 * @author Corey Wallis <corey@servalproject.org>
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 * @author Romana Challans <romana@servalproject.org>
 */
public class Main extends Activity {
	public ServalBatPhoneApplication app;
	private static final String PREF_WARNING_OK = "warningok";
//	Button btnreset;
	ImageView btncall;
	ImageView helpLabel;
	ImageView settingsLabel;
	ImageView btnShare;
	ImageView btnShareServal;
	BroadcastReceiver mReceiver;
	boolean mContinue;
	private TextView buttonToggle;
	private ImageView buttonToggleImg;
	private Drawable powerOnDrawable;
	private Drawable powerOffDrawable;
	private boolean changingState;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.app = (ServalBatPhoneApplication) this.getApplication();
		setContentView(R.layout.main);

		// if (false) {
		// // Tell WiFi radio if the screen turns off for any reason.
		// IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		// filter.addAction(Intent.ACTION_SCREEN_OFF);
		// if (mReceiver == null)
		// mReceiver = new ScreenReceiver();
		// registerReceiver(mReceiver, filter);
		// };

		// adjust the power button label on startup
		buttonToggle = (TextView) findViewById(R.id.btntoggle);
		buttonToggleImg = (ImageView) findViewById(R.id.powerLabel);

		// load the power drawables
		powerOnDrawable = getResources().getDrawable(
				R.drawable.ic_launcher_power);
		powerOffDrawable = getResources().getDrawable(
				R.drawable.ic_launcher_power_off);

		switch (app.getState()) {
		case On:
			// set the drawable to the power on image
			buttonToggle.setText(R.string.state_power_on);
			buttonToggleImg.setImageDrawable(powerOnDrawable);
			break;
		default:
			// for every other state use the power off drawable
			buttonToggle.setText(R.string.state_power_off);
			buttonToggleImg.setImageDrawable(powerOffDrawable);
		}

		// make with the phone call screen
		btncall = (ImageView) this.findViewById(R.id.btncall);
		btncall.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Main.this.startActivity(new Intent(Intent.ACTION_DIAL));
			}
		});

		// show the messages activity
		ImageView mImageView = (ImageView) findViewById(R.id.messageLabel);
		mImageView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startActivityForResult(new Intent(getApplicationContext(),
						org.servalproject.messages.MessagesListActivity.class),
						0);
			}
		});

		// show the maps application
		mImageView = (ImageView) findViewById(R.id.mapsLabel);
		mImageView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				// check to see if maps is installed
				try {
					PackageManager mManager = getPackageManager();
					mManager.getApplicationInfo("org.servalproject.maps",
							PackageManager.GET_META_DATA);

					Intent mIntent = mManager
							.getLaunchIntentForPackage("org.servalproject.maps");
					mIntent.addCategory(Intent.CATEGORY_LAUNCHER);
					startActivity(mIntent);

				} catch (NameNotFoundException e) {
					startActivityForResult(new Intent(getApplicationContext(),
							org.servalproject.ui.MapsActivity.class),
							0);
				}
			}
		});


		// show the contacts activity
		mImageView = (ImageView) findViewById(R.id.contactsLabel);
		mImageView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startActivityForResult(new Intent(getApplicationContext(),
						org.servalproject.ui.ContactsActivity.class),
						0);
			}
		});


		// make with the settings section
		mImageView = (ImageView) findViewById(R.id.settingsLabel);
		mImageView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startActivityForResult(new Intent(getApplicationContext(),
						org.servalproject.ui.SettingsScreenActivity.class),
						0);
			}
		});


		// The Share button leads to rhizome
		btnShare = (ImageView) this.findViewById(R.id.sharingLabel);
		btnShare.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Main.this.startActivity(new Intent(Main.this, RhizomeMain.class));
			}
		});

		// make with the help screen
		helpLabel = (ImageView) this.findViewById(R.id.helpLabel);
		helpLabel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Main.this.startActivity(new Intent(Main.this,
						HelpActivity.class));
			}
		});

		// Get thee hence to the Donate Screen
		/*
		 * Button btnDonate = (Button) this.findViewById(R.id.btnDonate);
		 * btnDonate.setOnClickListener(new View.OnClickListener() {
		 *
		 * @Override public void onClick(View arg0) {
		 * Main.this.startActivity(new Intent(Main.this, DonateScreen.class)); }
		 * });
		 */

		buttonToggleImg.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (changingState) {
					return;
				}
				changingState = true;
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

		ImageView shareUs = (ImageView) this.findViewById(R.id.servalLabel);
		shareUs.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Main.this.startActivity(new Intent(Main.this,
						ShareUsActivity.class));
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
		changingState = false;
		buttonToggle.setText(state.getResourceId());

		// change the image for the power button
		// TODO add other drawables for other state if req'd
		switch (state) {
		case On:
			// set the drawable to the power on image
			buttonToggleImg.setImageDrawable(powerOnDrawable);
			break;
		default:
			// for every other state use the power off drawable
			buttonToggleImg.setImageDrawable(powerOffDrawable);
		}

	}

	@Override
	protected void onResume() {
		super.onResume();

		checkAppSetup();
	}

	/**
	 * Run initialisation procedures to setup everything after install. Called
	 * from onResume() and after agreeing Warning dialog
	 */
	private void checkAppSetup() {
		if (ServalBatPhoneApplication.terminate_main) {
			ServalBatPhoneApplication.terminate_main = false;
			finish();
			return;
		}

		// Don't continue unless they've seen the warning
		if (!app.settings.getBoolean(PREF_WARNING_OK, false)) {
			showDialog(R.layout.warning_dialog);
			return;
		}

		if (PreparationWizard.preparationRequired()
				|| !ServalBatPhoneApplication.wifiSetup) {
			// Start by showing the preparation wizard
			Intent prepintent = new Intent(this, PreparationWizard.class);
			prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(prepintent);
			return;
		}

		Identity main = Identity.getMainIdentity();
		if (main == null || AccountService.getAccount(this) == null
				|| main.getDid() == null) {
			Log.v("MAIN",
					"Keyring doesn't seem to be initialised, starting wizard");

			this.startActivity(new Intent(this, Wizard.class));
			finish();
			return;
		}

		if (!registered) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(ServalBatPhoneApplication.ACTION_STATE);
			this.registerReceiver(receiver, filter);
			registered = true;
		}
		stateChanged(app.getState());

		TextView pn = (TextView) this.findViewById(R.id.mainphonenumber);
		String id = "";

		if (main.getDid() != null)
			id = main.getDid();
		else
			id = main.subscriberId.abbreviation();

		// if (main.getName() != null)
		// id += "/" + main.getName();
		pn.setText(id);

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
		View view = li.inflate(R.layout.warning_dialog, null);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(view);
		builder.setPositiveButton(R.string.agree,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int b) {
						dialog.dismiss();
						app.preferenceEditor.putBoolean(PREF_WARNING_OK, true);
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
		return builder.create();
	}
	// Menu deprecated by Romana, moved to Settings menu.

	// /**
	// * MENU SETTINGS
	// */
	// @Override
	// public boolean onCreateOptionsMenu(Menu menu) {
	// MenuInflater inflater = getMenuInflater();
	// inflater.inflate(R.menu.main_menu, menu);
	// return true;
	// }
	//
	// @Override
	// public boolean onOptionsItemSelected(MenuItem menuItem) {
	//
	// switch (menuItem.getItemId()) {
	// case R.id.main_menu_show_log:
	// startActivity(new Intent(this, LogActivity.class));
	// return true;
	// case R.id.main_menu_set_number:
	// startActivity(new Intent(Main.this, Wizard.class));
	// return true;
	// case R.id.main_menu_redetect_wifi:
	// // Clear out old attempt_ files
	// File varDir = new File("/data/data/org.servalproject/var/");
	// if (varDir.isDirectory())
	// for (File f : varDir.listFiles()) {
	// if (!f.getName().startsWith("attempt_"))
	// continue;
	// f.delete();
	// }
	// // Re-run wizard
	// PreparationWizard.currentAction = Action.NotStarted;
	// Intent prepintent = new Intent(this, PreparationWizard.class);
	// prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	// startActivity(prepintent);
	// return true;
	// default:
	// return super.onOptionsItemSelected(menuItem);
	// }
	// }
}
