package org.servalproject;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.system.WifiMode;
import org.servalproject.wizard.Wizard;

import android.R.drawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Main extends Activity {
	ServalBatPhoneApplication app;
	Button button;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);
		button = (Button) this.findViewById(R.id.btn);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {

				final State state = app.getState();

				new Thread() {
					@Override
					public void run() {
						try {
							switch (state) {
							case On:
								app.stopAdhoc();
								break;
							case Off:
								app.startAdhoc();
								break;
							}
						} catch (Exception e) {
							Log.e("BatPhone", e.toString(), e);
							app.displayToastMessage(e.toString());
						}
					}
				}.start();

				// if Client mode ask the user if we should turn it off.
				if (state == State.On
						&& app.wifiRadio.getCurrentMode() == WifiMode.Client) {
					AlertDialog.Builder alert = new AlertDialog.Builder(
							Main.this);
					alert.setTitle("Stop Wifi");
					alert.setMessage("Would you like to turn wifi off completely to save power?");
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
		this.app = (ServalBatPhoneApplication) this.getApplication();

	}

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
		case Starting:
		case Stopping:
			button.setEnabled(false);
			button.setText("Please wait... (" + state + ")");
			break;
		case On:
			button.setEnabled(true);
			button.setText("Turn Off");
			break;
		case Off:
			button.setEnabled(true);
			button.setText("Turn On");
			break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (app.getSubscriberId() == null) {
			this.startActivity(new Intent(this, Wizard.class));
		} else {
			IntentFilter filter = new IntentFilter();
			filter.addAction(ServalBatPhoneApplication.ACTION_STATE);
			this.registerReceiver(receiver, filter);
			registered = true;
			stateChanged(app.getState());
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (registered)
			this.unregisterReceiver(receiver);
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
		switch (menuItem.getItemId()) {
		case MENU_SETUP:
			startActivity(new Intent(this, SetupActivity.class));
			break;
		case MENU_SIP_SETUP:
			startActivity(new Intent(this, org.sipdroid.sipua.ui.Settings.class));
			break;
		case MENU_PEERS:
			startActivity(new Intent(this, PeerList.class));
			break;
		case MENU_LOG:
			startActivity(new Intent(this, LogActivity.class));
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
							public void onClick(DialogInterface dialog,
									int whichButton) {
								Uri uri = Uri
										.parse(getString(R.string.paypalUrlServal));
								startActivity(new Intent(Intent.ACTION_VIEW,
										uri));
							}
				});
		alert.setNegativeButton("Close", null);
		alert.show();
	}
}
