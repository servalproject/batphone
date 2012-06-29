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
import org.servalproject.servald.Identities;
import org.servalproject.system.WifiMode;
import org.servalproject.wizard.Wizard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

/**
 *
 * Main activity which presents the Serval launcher style screen. On the first
 * time Serval is installed, this activity ensures that a warning dialog is
 * presented and the user is taken through the setup wizard. Once setup has been
 * confirmed the user is taken to the main screen.
 *
 */
public class Main extends Activity {
	public ServalBatPhoneApplication app;
	private static final String PREF_WARNING_OK = "warningok";
	TextView btnSearch;
	TextView btnpair;
	TextView settingsLabel;
	BroadcastReceiver mReceiver;
	boolean mContinue;
	private boolean changingState;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.app = (ServalBatPhoneApplication) this.getApplication();
		setContentView(R.layout.main);

		setupWifi();

		TextView deviceLabel = (TextView) findViewById(R.id.deviceLabel);
		deviceLabel.setText((app.isBabyMonitorServer() ? "Baby" : "Parent")
				+ "\n"
				+ (Identities.getCurrentIdentity() != null ? Identities
						.getCurrentIdentity().abbreviation() : "no SID"));

		// make with the phone call screen
		btnpair = (TextView) this.findViewById(R.id.btnpair);
		btnpair.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Main.this.startActivity(new Intent(Main.this,
						PairingActivity.class));
			}
		});

		// make with the settings screen
		settingsLabel = (TextView) this.findViewById(R.id.settingsLabel);
		settingsLabel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Main.this.startActivity(new Intent(Main.this,
						SetupActivity.class));
			}
		});

		btnSearch = (TextView) this.findViewById(R.id.searchLabel);
		btnSearch.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Main.this.startActivity(new Intent(Main.this,
						PeerList.class));
			}
		});

		// switch (app.getState()) {
		// case On:
		// // set the drawable to the power on image
		// btnPower.setText(R.string.state_power_on);
		// break;
		// default:
		// // for every other state use the power off drawable
		// btnPower.setText(R.string.state_power_off);
		// }

		// btnPower.setOnClickListener(new OnClickListener() {
		// @Override
		// public void onClick(View arg0) {
		// if (changingState) {
		// return;
		// }
		// changingState = true;
		// State state = app.getState();
		//
		// Intent serviceIntent = new Intent(Main.this, Control.class);
		// switch (state) {
		// case On:
		// stopService(serviceIntent);
		// break;
		// case Off:
		// startService(serviceIntent);
		// break;
		// }
		// }
		// });

	} // onCreate

	private void setupWifi() {
		// if this app is the baby monitor sender, set the wifi mode to AP if
		// supported by the chipset, otherwise set to client mode.
		ServalBatPhoneApplication app = (ServalBatPhoneApplication) getApplication();
		WifiMode mode = app.isBabyMonitorServer()
				? WifiMode.Ap : WifiMode.Client;
		// WifiMode mode = WifiMode.Client;
		try {
			if (app.wifiRadio != null) {
				app.wifiRadio.setWiFiMode(mode);
				app.wifiRadio.setAutoCycling(false);
			}
		} catch (Exception e) {
			Log.e("SetupActivity", e.getMessage(), e);
			app.displayToastMessage("Could not set WiFi mode to " + mode);
		} finally {
			app.setState(State.Off);
		}
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
		changingState = false;
		// btnSearch.setText(state.getResourceId());
	}

	@Override
	protected void onResume() {
		super.onResume();
		checkAppSetup();
		Intent serviceIntent = new Intent(Main.this, Control.class);
		startService(serviceIntent);
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
		// if (!app.settings.getBoolean(PREF_WARNING_OK, false)) {
		// showDialog(R.layout.warning_dialog);
		// return;
		// }

		if (PreparationWizard.preparationRequired()
				|| !ServalBatPhoneApplication.wifiSetup) {
			// Start by showing the preparation wizard
			Intent prepintent = new Intent(this, PreparationWizard.class);
			prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(prepintent);
			return;
		}

		if (Identities.getCurrentDid() == null
				|| Identities.getCurrentDid().equals("")
				|| AccountService.getAccount(this) == null) {
			// this.startActivity(new Intent(this, AccountAuthActivity.class));
			// use the wizard rather than the test AccountAuthActivity

			Log.v("MAIN", "currentDid(): " + Identities.getCurrentDid());
			Log.v("MAIN", "getAccount(): " + AccountService.getAccount(this));

			this.startActivity(new Intent(this, Wizard.class));
			return;
		}

		// Put in-call display on top if it is not currently so
		// if (Receiver.call_state != UserAgent.UA_STATE_IDLE) {
		// Receiver.moveTop();
		// return;
		// }

		if (!registered) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(ServalBatPhoneApplication.ACTION_STATE);
			this.registerReceiver(receiver, filter);
			registered = true;
		}
		stateChanged(app.getState());

	}

	@Override
	protected void onPause() {
		super.onPause();
		if (registered) {
			this.unregisterReceiver(receiver);
			registered = false;
		}
		// Intent serviceIntent = new Intent(Main.this, Control.class);
		// stopService(serviceIntent);
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


	// Microphone check
	private Thread recordingThread;
	private int bufferSize = 800;
	private short[][] buffers = new short[256][bufferSize];
	private int[] averages = new int[256];
	private int lastBuffer = 0;
	private boolean recorderStarted;
	private AudioRecord recorder;

	protected void startListenToMicrophone(final TextView view,
			final String labelText) {
		if (!recorderStarted) {

			recordingThread = new Thread() {
				@Override
				public void run() {
					int minBufferSize = AudioRecord.getMinBufferSize(8000,
							AudioFormat.CHANNEL_CONFIGURATION_MONO,
							AudioFormat.ENCODING_PCM_16BIT);
					recorder = new AudioRecord(AudioSource.MIC, 8000,
							AudioFormat.CHANNEL_CONFIGURATION_MONO,
							AudioFormat.ENCODING_PCM_16BIT, minBufferSize * 10);
					recorder.setPositionNotificationPeriod(bufferSize);
					recorder.setRecordPositionUpdateListener(new OnRecordPositionUpdateListener() {
						@Override
						public void onPeriodicNotification(AudioRecord recorder) {
							short[] buffer = buffers[++lastBuffer
									% buffers.length];
							recorder.read(buffer, 0, bufferSize);
							long sum = 0;
							for (int i = 0; i < bufferSize; ++i) {
								sum += Math.abs(buffer[i]);
							}
							int i = lastBuffer % buffers.length;
							averages[i] = (int) (sum / bufferSize);
							lastBuffer = i;
							view.setText(labelText + "\n[" + averages[i] + "]");
						}

						@Override
						public void onMarkerReached(AudioRecord recorder) {
						}
					});
					recorder.startRecording();
					short[] buffer = buffers[lastBuffer % buffers.length];
					recorder.read(buffer, 0, bufferSize);
					while (true) {
						if (isInterrupted()) {
							recorder.stop();
							recorder.release();
							break;
						}
					}
				}
			};
			recordingThread.start();

			recorderStarted = true;
		}
	}

	private void stopListenToMicrophone() {
		if (recorderStarted) {
			if (recordingThread != null && recordingThread.isAlive()
					&& !recordingThread.isInterrupted()) {
				recordingThread.interrupt();
			}
			recorderStarted = false;
		}
	}

}
