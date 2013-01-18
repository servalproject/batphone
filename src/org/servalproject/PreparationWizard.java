/* Copyright (C) 2012 The Serval Project
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

/**
 * Wizard - initial settings, reset phone.
 * @author Paul Gardner-Stephen <paul@servalproject.org>
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 * @author Romana Challans <romana@servalproject.org>
 */
package org.servalproject;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.shell.Shell;
import org.servalproject.system.Chipset;
import org.servalproject.system.ChipsetDetection;
import org.servalproject.system.WiFiRadio;
import org.servalproject.system.WifiMode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.AnimationDrawable;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

public class PreparationWizard extends Activity {
	public enum Action {
		NotStarted, Unpacking(R.id.starUnpack, true), AdhocWPA, RootCheck(
				R.id.starRoot), Supported(R.id.starChipsetSupported), Experimental(
				R.id.starChipsetExperimental), CheckSupport(
				R.id.starTestChipset), Finished;

		int viewId = 0;
		boolean fatal = false;

		Action() {
		}

		Action(int viewId) {
			this(viewId, false);
		}

		Action(int viewId, boolean fatal) {
			this.viewId = viewId;
			this.fatal = fatal;
		}
	}

	protected static final int DISMISS_PROGRESS_DIALOG = 0;
	protected static final int CREATE_PROGRESS_DIALOG = 1;

	public static Action currentAction = Action.NotStarted;
	public static boolean results[] = new boolean[Action.values().length];
	public static boolean fatalError = false;

	private ServalBatPhoneApplication app;
	static PreparationWizard instance = null;

	private ProgressDialog progressDialog = null;
	AlertDialog alert = null;

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case DISMISS_PROGRESS_DIALOG:
				if (progressDialog != null)
					progressDialog.cancel();
				break;
			case CREATE_PROGRESS_DIALOG:
				progressDialog = ProgressDialog
						.show(
								instance,
								"",
								"Trying some educated guesses as to how to drive your WiFi chipset.  If it takes more than a couple of minutes, or freezes, try rebooting the phone.  I will remember not to try whichever guess got stuck.",
								true);
				progressDialog.setCancelable(false);
				break;
			}
		}
	};

	public static void showTryExperimentalChipsetDialog() {
		instance.handler.sendEmptyMessage(CREATE_PROGRESS_DIALOG);
	}

	public static void dismissTryExperimentalChipsetDialog() {
		instance.handler.sendEmptyMessage(DISMISS_PROGRESS_DIALOG);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		PreparationWizard.instance = this;

		setContentView(R.layout.preparationlayout);

		app = (ServalBatPhoneApplication) this.getApplication();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private void updateProgress(Action current) {
		for (Action a : Action.values()) {
			ImageView image = (ImageView) this.findViewById(a.viewId);
			if (image == null)
				continue;

			if (a == current) {
				// this is the current action
				showInProgress(image);
			} else if (a.ordinal() < current.ordinal()) {
				// this action has completed
				showResult(image, results[a.ordinal()]);
			} else {
				// this action hasn't started
				showNotStarted(image);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		updateProgress(currentAction);

		// Start by installing files and continuing
		if (currentAction == Action.NotStarted)
			new PreparationTask().execute();

	}

	private void showInProgress(ImageView imageView) {
		final AnimationDrawable yourAnimation;
		imageView.setBackgroundResource(R.drawable.preparation_progress);
		yourAnimation = (AnimationDrawable) imageView.getBackground();
		imageView.setImageDrawable(yourAnimation);
		imageView.setVisibility(ImageView.VISIBLE);
		yourAnimation.start();
	}

	private void showNotStarted(ImageView imageView) {
		imageView.setVisibility(ImageView.INVISIBLE);
		imageView.setImageResource(R.drawable.jetxee_tick_yellow);
	}

	private void showResult(ImageView imageView, Boolean result) {
		int imageid;
		if (result)
			imageid = R.drawable.jetxee_tick_yellow;
		else
			imageid = R.drawable.jetxee_cross_yellow;

		imageView.setBackgroundResource(0);
		imageView.setImageResource(imageid);
		imageView.setVisibility(ImageView.VISIBLE);
	}

	public void installedFiles(boolean result) {
		if (result)
			return;

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Sorry, I couldn't extract all the files I needed.")
				.setCancelable(false).setPositiveButton("Quit",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								// Do nothing -- just let the user close the
								// activity.
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

	public void checkedChipsetSupported(boolean result) {
		// XXX - Need to handle multiple detections here so that we can give the
		// user a choice, and then test that choice.
		if (result) {
			TextView t = (TextView) findViewById(R.id.labelChipsetSupported);
			if (t != null) {
				t.setText("I think I know how to control your WiFi chipset.");
				t = (TextView) findViewById(R.id.labelChipsetExperimental);
				t
					.setText("Skipped check for experimental support, since we already support your handset.");
			}
		}
	}

	public static boolean preparationRequired() {
		State state = ServalBatPhoneApplication.context.getState();
		return state == State.Installing || state == State.Upgrading;
	}

	class PreparationTask extends AsyncTask<Void, Action, Action> {
		private PowerManager.WakeLock wakeLock = null;
		Shell rootShell;

		PreparationTask() {
			PowerManager powerManager = (PowerManager) ServalBatPhoneApplication.context
					.getSystemService(Context.POWER_SERVICE);
			wakeLock = powerManager.newWakeLock(
					PowerManager.SCREEN_DIM_WAKE_LOCK, "PREPARATION_WAKE_LOCK");
		}

		private boolean testSupport() {
			ChipsetDetection detection = ChipsetDetection.getDetection();

			List<Chipset> l = detection.detected_chipsets;
			boolean tryExperimental = false;
			int retries = 3;

			while (retries > 0) {
				retries--;
				for (int i = 0; i < l.size(); i++) {
					Chipset c = l.get(i);

					if (c.isExperimental() != tryExperimental)
						continue;

					// only test scripts if we have root access, otherwise
					// assume the first one is correct
					if (results[Action.RootCheck.ordinal()]) {

						if (!c.supportedModes.contains(WifiMode.Adhoc))
							continue;

						Log.v("BatPhone", "Trying to use chipset " + c.chipset);
						detection.setChipset(c);

						if (!c.supportedModes.contains(WifiMode.Adhoc))
							continue;

						// Write a disable file that suppresses attempting
						// this detection again so that re-running the BatPhone
						// preparation wizard will not get stuck on the same
						// chipset every time
						File attemptFlag = detection.getAdhocAttemptFile(c);

						// If a chipset is marked experimental, then tell the
						// user.
						if (tryExperimental)
							PreparationWizard
									.showTryExperimentalChipsetDialog();
						// FIXME
						File storage = ServalBatPhoneApplication.getStorageFolder();
						if (!new File(storage, "developer-mode/fast-wifi")
								.exists()) {
							try {
								attemptFlag.createNewFile();

								if (app.wifiRadio == null) {
									// this constructor is a bit too convoluted,
									// mainly so we can re-use the single root
									// shell for the entire preparation process
									// TODO refactor
									app.wifiRadio = WiFiRadio.getWiFiRadio(app,
											WifiMode.getWiFiMode(rootShell));
								}

								app.wifiRadio.testAdhoc(rootShell);

							} catch (IOException e) {
								Log.e("BatPhone", e.toString(), e);
								continue;
							} catch (InterruptedException e) {
								Log.e("BatPhone", e.toString(), e);
								continue;
							}
							if (attemptFlag != null)
								attemptFlag.delete();
						}
					} else {
						Log.v("BatPhone", "Assuming chipset " + c.chipset
								+ " as there is no root access.");
						detection.setChipset(c);

					}

					Editor ed = app.settings.edit();
					ed.putString("detectedChipset", c.chipset);
					ed.commit();

					LogActivity.logMessage("detect", "We will use the '"
							+ c.chipset + "' script to control WiFi.", false);
					return true;

				}

				tryExperimental = !tryExperimental;
				if (tryExperimental == false)
					break;
			}
			detection.setChipset(null);
			Editor ed = app.settings.edit();
			ed.putString("detectedChipset", "UnKnown");
			ed.commit();

			return false;
		}

		@Override
		protected Action doInBackground(Void... arg) {
			wakeLock.acquire();

			try {
				ChipsetDetection detection = ChipsetDetection.getDetection();

				while (true) {
					boolean result = false;
					boolean fatal = currentAction.fatal;
					try {
						Log.v("BatPhone", "Performing action " + currentAction);

						switch (currentAction) {
						case Unpacking:
							app.installFilesIfRequired();
							result = true;
							LogActivity.logErase("adhoc");
							LogActivity.logErase("detect");
							LogActivity.logErase("guess");
							break;

						case AdhocWPA:
							if (false) {
								// Get wifi manager
								WifiManager wm = (WifiManager) ServalBatPhoneApplication.context
										.getSystemService(Context.WIFI_SERVICE);

								// enable wifi
								wm.setWifiEnabled(true);
								WifiConfiguration wc = new WifiConfiguration();
								wc.SSID = "*supplicant-test";
								int res = wm.addNetwork(wc);
								Log
										.d("BatPhone", "add Network returned "
												+ res);
								boolean b = wm.enableNetwork(res, true);
								Log.d("WifiPreference",
										"enableNetwork returned " + b);
							}
							break;

						case RootCheck:
							if (rootShell == null) {
								try {
									rootShell = Shell.startRootShell();
									result = true;
								} catch (Exception e) {
									Log.e("BatPhone", e.getMessage(), e);
									result = false;
								}
								app.coretask.rootTested(result);
							}
							break;

						case Supported:
							// Start out by only looking for non-experimental
							// chipsets
							detection.identifyChipset();
							result = detection.detected_chipsets.size() > 0;
							break;

						case Experimental:
							if (!results[Action.Supported.ordinal()]) {
								detection.inventSupport();
								// this will not select a chipset
								detection.detect(true);
								result = detection.detected_chipsets.size() > 0;
							}
							break;

						case CheckSupport:
							result = testSupport();
							break;

						case Finished:
							break;
						}

					} catch (Exception e) {
						result = false;
						Log.e("BatPhone", e.toString(), e);
						app.displayToastMessage(e.getMessage());
						fatal = true;
					}

					results[currentAction.ordinal()] = result;
					Log.v("BatPhone", "Result " + result);

					if (fatal && !result) {
						fatalError = true;
						return currentAction;
					}

					if (currentAction == Action.Finished) {
						ServalBatPhoneApplication.wifiSetup = true;
						return currentAction;
					}

					this.publishProgress(currentAction);
					currentAction = Action.values()[currentAction.ordinal() + 1];
				}
			} finally {
				try {
					if (rootShell != null) {
						rootShell.waitFor();
						rootShell = null;
					}
				} catch (Exception e) {
					Log.e("BatPhone", e.getMessage(), e);
				}
				wakeLock.release();
				dismissTryExperimentalChipsetDialog();
			}
		}

		private void stepProgress(Action a) {

			boolean result = results[a.ordinal()];

			switch (a) {
			case Unpacking:
				installedFiles(result);
				break;

			case Supported:
				checkedChipsetSupported(result);
				break;

			case CheckSupport:
				if (fatalError) {
					Intent intent = new Intent(
							ServalBatPhoneApplication.context,
							WifiJammedActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
					finish();
				}
				break;

			case Finished:
				app.getReady();
				// TODO tell user if we can't do Adhoc??
				finish();
			}

		}

		@Override
		protected void onProgressUpdate(Action... arg) {
			if (arg[0] == Action.Finished) {
				updateProgress(arg[0]);
			} else {
				updateProgress(Action.values()[arg[0].ordinal() + 1]);
			}
			stepProgress(arg[0]);
		}

		@Override
		protected void onPostExecute(Action arg) {
			updateProgress(arg);
			stepProgress(arg);
		}
	}
}
