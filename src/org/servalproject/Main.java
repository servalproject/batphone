package org.servalproject;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.system.WifiMode;
import org.servalproject.wizard.Wizard;
import org.sipdroid.sipua.UserAgent;
import org.sipdroid.sipua.ui.Receiver;

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
	Button toggleButton;
	Button btncall;
	Button btnreset;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.app = (ServalBatPhoneApplication) this.getApplication();
		setContentView(R.layout.main);

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
				startActivity(new Intent(Main.this, Wizard.class));
			}
		});

		toggleButton = (Button) this.findViewById(R.id.btntoggle);
		toggleButton.setOnClickListener(new OnClickListener() {
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

		if (app.getSubscriberId() == null) {
			this.startActivity(new Intent(this, Wizard.class));
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
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (registered) {
			this.unregisterReceiver(receiver);
			registered = false;
		}
	}

	private static final int MENU_SETUP = 0;
	private static final int MENU_PEERS = 1;
	private static final int MENU_LOG = 2;
	private static final int MENU_ABOUT = 3;

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
