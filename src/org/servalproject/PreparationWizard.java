package org.servalproject;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.servalproject.ServalBatPhoneApplication.State;
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

	private void updateProgress() {
		for (Action a : Action.values()) {
			if (a == currentAction) {
				// this is the current action
				showInProgress(a.viewId);
			} else if (a.ordinal() < currentAction.ordinal()) {
				// this action has completed
				showResult(a.viewId, results[a.ordinal()]);
			} else {
				// this action hasn't started
				showNotStarted(a.viewId);
			}
		}
		ChipsetDetection detection = ChipsetDetection.getDetection();
		if (currentAction == Action.Finished)
			if (detection == null
					|| detection.getWifiChipset() == null
					|| detection.getWifiChipset().supportedModes == null
					|| detection.getWifiChipset().supportedModes
							.contains(WifiMode.Adhoc) == false) {
				app.showNoAdhocDialog = true;
				LogActivity
						.logMessage(
								"detect",
								"Could not work out how to control your WiFi chipset. Relying on operating system, so no ad-hoc WiFi.",
								false);
			}

	}

	@Override
	protected void onResume() {
		super.onResume();

		updateProgress();

		// Start by installing files and continuing
		if (currentAction == Action.NotStarted)
			new PreparationTask().execute();

	}

	private void showInProgress(int item) {
		ImageView imageView = (ImageView) findViewById(item);
		if (imageView != null) {
			final AnimationDrawable yourAnimation;
			imageView.setBackgroundResource(R.drawable.preparation_progress);
			yourAnimation = (AnimationDrawable) imageView.getBackground();
			imageView.setImageDrawable(yourAnimation);
			imageView.setVisibility(ImageView.VISIBLE);
			yourAnimation.start();
		}
		return;
	}

	private void showNotStarted(int id) {
		ImageView imageView = (ImageView) findViewById(id);
		if (imageView != null) {
			imageView.setVisibility(ImageView.INVISIBLE);
			imageView.setImageResource(R.drawable.jetxee_tick_yellow);
		}
		return;
	}

	private void showResult(int id, Boolean result) {
		ImageView imageView = (ImageView) findViewById(id);
		int imageid;
		if (result)
			imageid = R.drawable.jetxee_tick_yellow;
		else
			imageid = R.drawable.jetxee_cross_yellow;
		if (imageView != null) {
			imageView.setImageResource(imageid);
			imageView.setBackgroundResource(0);
			imageView.setVisibility(ImageView.VISIBLE);
		}
		return;
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
			t.setText("I think your WiFi is '"
					+ ChipsetDetection.getDetection().getChipset() + "'.");
			t = (TextView) findViewById(R.id.labelChipsetExperimental);
			t
					.setText("Skipped check for experimental support, since we already support your handset.");
		}
	}

	public static boolean preparationRequired() {
		return ServalBatPhoneApplication.context.getState() == State.Installing;
	}

	class PreparationTask extends AsyncTask<Void, Action, Action> {
		private PowerManager.WakeLock wakeLock = null;

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

			while (true) {
				for (int i = 0; i < l.size(); i++) {
					Chipset c = l.get(i);

					if (c.isExperimental() != tryExperimental)
						continue;

					// only test scripts if we have root access, otherwise
					// assume the first one is correct
					if (results[Action.RootCheck.ordinal()]) {

						if (!c.supportedModes.contains(WifiMode.Adhoc))
							continue;

						// Write a disable file that suppresses attempting
						// this detection again so that re-running the BatPhone
						// preparation wizard will not get stuck on the same
						// chipset every time
						File attemptFlag = new File(app.coretask.DATA_FILE_PATH
								+ "/var/attempt_" + c.chipset);
						if (attemptFlag.exists()) {
							Log.v("BatPhone", "Skipping " + c.chipset
									+ " as I think it failed before");
							continue;
						}

						// If a chipset is marked experimental, then tell the
						// user.
						if (tryExperimental)
							PreparationWizard.showTryExperimentalChipsetDialog();

						try {
							attemptFlag.createNewFile();

							Log.v("BatPhone", "Trying to use chipset " + c.chipset);
							detection.setChipset(c);

							if (app.wifiRadio == null)
								app.wifiRadio = WiFiRadio.getWiFiRadio(app);

							// make sure we aren't still in adhoc mode from a
							// previous
							// install / test
							if (WifiMode.getWiFiMode() != WifiMode.Off)
								app.wifiRadio.setWiFiMode(WifiMode.Off);

							if (WifiMode.getWiFiMode() != WifiMode.Off) {
								throw new IllegalStateException(
										"Could not turn wifi off");
							}

							// test adhoc on & off
							try {
								app.wifiRadio.setWiFiMode(WifiMode.Adhoc);
								app.wifiRadio.setWiFiMode(WifiMode.Off);
							} finally {
								if (WifiMode.getWiFiMode() != WifiMode.Off) {
									attemptFlag = null;
									throw new IllegalStateException(
											"Could not turn wifi off");
								}
							}
						} catch (IOException e) {
							Log.e("BatPhone", e.toString(), e);
						} finally {
							// If we couldn't turn off wifi, just fail completely
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
							result = ServalBatPhoneApplication.context.coretask
									.hasRootPermission();
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
				wakeLock.release();
				dismissTryExperimentalChipsetDialog();
			}
		}

		private void stepProgress(Action a) {

			updateProgress();
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
			stepProgress(arg[0]);
		}

		@Override
		protected void onPostExecute(Action arg) {
			stepProgress(arg);
		}
	}
}
