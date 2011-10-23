package org.servalproject;

import java.io.IOException;
import java.util.List;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.system.Chipset;
import org.servalproject.system.ChipsetDetection;
import org.servalproject.system.WiFiRadio;
import org.servalproject.system.WifiMode;
import org.servalproject.wizard.Wizard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

// XXX BUG TODO In desperate need of an enum for the preparation actions.

public class PreparationWizard extends Activity {

	private static boolean previouslyPrepared = false;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ServalBatPhoneApplication.context.preparation_activity = this;

	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	private void showInStep(int step) {
		if (step != 1)
			showNotStarted(R.id.starUnpack);
		if (step != 2)
			showNotStarted(R.id.starAdhocWPA);
		if (step != 3)
			showNotStarted(R.id.starRoot);
		if (step != 4)
			showNotStarted(R.id.starChipsetSupported);
		if (step != 5)
			showNotStarted(R.id.starChipsetExperimental);

		int id = R.id.starUnpack;
		switch (step) {
		case 1:
			id = R.id.starUnpack;
			break;
		case 2:
			id = R.id.starAdhocWPA;
			break;
		case 3:
			id = R.id.starRoot;
			break;
		case 4:
			id = R.id.starChipsetSupported;
			break;
		case 5:
			id = R.id.starChipsetExperimental;
			break;
		}
		showInProgress(id);
		return;
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Read state for this layout
		// XXX - For now start at beginning each time

		// Make sure layout has been created
		setContentView(R.layout.preparationlayout);

		// Hide all stars to begin with
		showInStep(1);

		// Start by installing files and continuing
		new PreparationTask().execute(1);

		// XXX Actually do the tasks here, keep track of state and update the
		// stars as we go

		// XXX Eventually guide the user through the process of picking a
		// guessed chipset definition and testing it, and uploading the result
		// if it works. (Also should handle multiple detects)
	}

	public void showInProgress(int item) {
		// TODO Auto-generated method stub
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

	public void showNotStarted(int id) {
		ImageView imageView = (ImageView) findViewById(id);
		if (imageView != null) {
			imageView.setVisibility(ImageView.INVISIBLE);
			imageView.setImageResource(R.drawable.jetxee_tick_yellow);
		}
		return;
	}

	public void showResult(int id, Boolean result) {
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

	public void installedFiles(Boolean result) {
		// Having installed files, move onto dealing with wpa_supplicant tests
		if (result == false) {
			// XXX Complain and exit
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(
					"Sorry, I couldn't extract all the files I needed.")
					.setCancelable(false).setPositiveButton("Quit",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									// Do nothing -- just let the user close the
									// activity.
								}
							});
			AlertDialog alert = builder.create();
			alert.show();
			return;
		}

		// All worked, so on to next
		ServalBatPhoneApplication.context.preparation_activity
				.showInProgress(R.id.starAdhocWPA);
	}

	public void checkedChipsetSupported(Boolean result) {
		// XXX - Need to handle multiple detections here so that we can give the
		// user a choice, and then test that choice.
		ServalBatPhoneApplication.context.chipset_detection = ChipsetDetection
				.getDetection();
		if (result == true) {
			TextView t = (TextView) findViewById(R.id.labelChipsetSupported);
			t.setText("I think your WiFi is '"
					+ ChipsetDetection.getDetection().getChipset() + "'.");
			t = (TextView) findViewById(R.id.labelChipsetExperimental);
			t
					.setText("Skipped check for experimental support, since we already support your handset.");
			checkedChipsetExperimental(false);
		} else {
			if (ServalBatPhoneApplication.context.chipset_detection.detected_chipsets
					.size() == 0) {
				// Failed to detect, so try experimental detection.
				ServalBatPhoneApplication.context.preparation_activity
						.showInProgress(R.id.starChipsetExperimental);
				new PreparationTask().execute(5);
			} else {
				// Multiple detections, work out which one to use.
			}
		}
	}

	public void checkedChipsetExperimental(Boolean result) {
		if (result == true) {
			// Okay, so we have experimental support for this handset.
			// If we do not have a standing identification then prompt the user
			// to see if they would like us to try the various experimental
			// detections
		}

		// Okay, we are all done here, so lets just get everything ready,
		// ditch this activity and switch to the main (dashboard) activity
		// (but going via the wizard if necessary)
		ServalBatPhoneApplication.context.getReady();
		startActivity(new Intent(this, Wizard.class));
		previouslyPrepared = true;
		this.finish();
	}

	public static boolean preparationRequired() {
		// TODO Auto-generated method stub
		if (ServalBatPhoneApplication.context.getState() == State.Installing
			|| previouslyPrepared == false) {
			return true;
		}
		return false;
	}

}

class PreparationTask extends AsyncTask<Integer, Integer, Boolean> {

	private int last_id;

	private static boolean activeP = false;

	private PowerManager.WakeLock wakeLock = null;

	@Override
	protected Boolean doInBackground(Integer... ids) {
		int id = ids[0];
		boolean r;
		// Go through steps until we get to the chipset detection, and then
		// don't automatically do experimental chipset detection unless asked.
		while (true) {
			r = doOneTask(id);
			id++;
			if (id >= 5)
				break;
		}
		return r;
	}

	private Boolean doOneTask(Integer id) {
		if (activeP)
			return false;
		activeP = true;
		if (wakeLock == null)
 {
			PowerManager powerManager = (PowerManager) ServalBatPhoneApplication.context
					.getSystemService(Context.POWER_SERVICE);
			wakeLock = powerManager.newWakeLock(
					PowerManager.SCREEN_DIM_WAKE_LOCK, "PREPARATION_WAKE_LOCK");
		}
		wakeLock.acquire();

		last_id = id;

		switch (id) {
		case 1:
			boolean result = ServalBatPhoneApplication.context
					.installFilesIfRequired();
			return doneInBackground(result);
		case 2:
			// XXX - We don't have a check for this yet

			if (false) {
			// Get wifi manager
			WifiManager wm = (WifiManager) ServalBatPhoneApplication.context
					.getSystemService(Context.WIFI_SERVICE);

			// enable wifi
			wm.setWifiEnabled(true);
			WifiConfiguration wc = new WifiConfiguration();
			wc.SSID = "*supplicant-test";
			int res = wm.addNetwork(wc);
			Log.d("BatPhone", "add Network returned " + res);
			boolean b = wm.enableNetwork(res, true);
			Log.d("WifiPreference", "enableNetwork returned " + b);
			}
			return doneInBackground(false);
		case 3:
			result = ServalBatPhoneApplication.context.coretask
					.hasRootPermission();
			return doneInBackground(result);
		case 4:
			// Start out by only looking for non-experimental chipsets
			result = ChipsetDetection.getDetection().identifyChipset(
					false);
			// If false, we should make note so that we can try experimental
			// support if needed.
			Log
					.d(
							"BatPhone",
							"Detected chipsets are: "
					+ ChipsetDetection.detected_chipsets);
			return doneInBackground(result);
		case 5:
			// See if we need to bother with experimental detection
			ChipsetDetection.inventSupport();
			result = ChipsetDetection.getDetection().identifyChipset(true);

			List<Chipset> l = ChipsetDetection.detected_chipsets;
			// Quit now if there is nothing to detect
			if (l.size() < 1) {
				return doneInBackground(false);
			}
			int i;
			for (i = 0; i < l.size(); i++) {
				Chipset c = l.get(i);
				// XXX - Write a disable file that suppresses attempting this
				// detection again so that re-running the BatPhone preparation
				// wizard will not get stuck on the same chipset every time
				ServalBatPhoneApplication.context.chipset_detection
						.setChipset(c);
				try {
					if (ServalBatPhoneApplication.context.wifiRadio == null)
						ServalBatPhoneApplication.context.wifiRadio = WiFiRadio
								.getWiFiRadio(ServalBatPhoneApplication.context);

					if (ServalBatPhoneApplication.context.wifiRadio != null) {
						ServalBatPhoneApplication.context.wifiRadio
								.setWiFiMode(WifiMode.Adhoc);
						if (WifiMode.getWiFiMode() == WifiMode.Adhoc) {
							// Bingo! this one works
							return doneInBackground(true);
						}
					}
				} catch (IOException e) {
					Log.e("BatPhone", e.toString());
				}
			}
			return doneInBackground(false);
		}
		return doneInBackground(false);

	}

	private boolean doneInBackground(boolean r) {
		if (wakeLock != null)
			wakeLock.release();
		activeP = false;
		return r;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		ServalBatPhoneApplication.context.preparation_activity.showResult(
				last_id, result);
		switch (last_id) {
		case 1:
			ServalBatPhoneApplication.context.preparation_activity
				.installedFiles(result);
			return;
		case 4:
			ServalBatPhoneApplication.context.preparation_activity
					.checkedChipsetSupported(result);
			return;
		case 5:
			ServalBatPhoneApplication.context.preparation_activity
					.checkedChipsetExperimental(result);
		}
		return;
	}
}
