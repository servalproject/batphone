package org.servalproject;

import org.servalproject.system.ChipsetDetection;
import org.servalproject.wizard.Wizard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

public class PreparationWizard extends Activity {

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

	@Override
	protected void onResume() {
		super.onResume();

		// Read state for this layout
		// XXX - For now start at beginning each time

		// Make sure layout has been created
		setContentView(R.layout.preparationlayout);

		// Hide all stars to begin with
		showNotStarted(R.id.starUnpack);
		showNotStarted(R.id.starAdhocWPA);
		showNotStarted(R.id.starRoot);

		// Start by installing files
		new PreparationTask().execute(R.id.starUnpack);

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
			imageView.setImageResource(R.drawable.jetxee_tick);
		}
		return;
	}

	public void showResult(int id, Boolean result) {
		ImageView imageView = (ImageView) findViewById(id);
		int imageid;
		if (result)
			imageid = R.drawable.jetxee_tick;
		else
			imageid = R.drawable.jetxee_cross;
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
		new PreparationTask().execute(R.id.starAdhocWPA);
	}

	public void checkedSupplicant(Boolean result) {
		// TODO Auto-generated method stub
		ServalBatPhoneApplication.context.preparation_activity
				.showInProgress(R.id.starRoot);
		new PreparationTask().execute(R.id.starRoot);
	}

	public void checkedRoot(Boolean result) {
		// TODO Auto-generated method stub
		ServalBatPhoneApplication.context.preparation_activity
				.showInProgress(R.id.starChipsetSupported);

		new PreparationTask().execute(R.id.starChipsetSupported);

	}

	public void checkedChipsetSupported(Boolean result) {
		// TODO Auto-generated method stub

		// XXX - Need to handle multiple detections here so that we can give the
		// user a choice, and then test that choice.
		ServalBatPhoneApplication.context.chipset_detection = ChipsetDetection
				.getDetection();
		if (ServalBatPhoneApplication.context.chipset_detection.detected_chipsets
				.size() == 1) {
			TextView t = (TextView) findViewById(R.id.labelChipsetSupported);
			t.setText("I think your WiFi is '"
					+ ChipsetDetection.getDetection().getChipset() + "'.");
			t = (TextView) findViewById(R.id.labelChipsetExperimental);
			t
					.setText("Skipped check for experimental support, since we already support your handset.");
			checkedChipsetExperimental(false);
		} else {
			// Failed to detect, so try experimental detection.
			ServalBatPhoneApplication.context.preparation_activity
					.showInProgress(R.id.starChipsetExperimental);
			new PreparationTask().execute(R.id.starChipsetExperimental);
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
		this.finish();
	}


}

class PreparationTask extends AsyncTask<Integer, Integer, Boolean> {

	private int last_id;

	@Override
	protected Boolean doInBackground(Integer... ids) {
		int id = ids[0];

		last_id = id;

		switch (id) {
		case R.id.starUnpack:
			return ServalBatPhoneApplication.context.installFilesIfRequired();
		case R.id.starAdhocWPA:
			// XXX - We don't have a check for this yet
			return false;
		case R.id.starRoot:
			return ServalBatPhoneApplication.context.coretask
					.hasRootPermission();
		case R.id.starChipsetSupported:
			Boolean result = ChipsetDetection.getDetection().identifyChipset();
			// If false, we should make note so that we can try experimental
			// support if needed.
			return result;
		case R.id.starChipsetExperimental:
			return false;
		}
		return false;

	}

	@Override
	protected void onPostExecute(Boolean result) {
		ServalBatPhoneApplication.context.preparation_activity.showResult(
				last_id, result);
		switch (last_id) {
		case R.id.starUnpack:
			ServalBatPhoneApplication.context.preparation_activity
				.installedFiles(result);
			return;
		case R.id.starAdhocWPA:
			ServalBatPhoneApplication.context.preparation_activity
					.checkedSupplicant(result);
			return;
		case R.id.starRoot:
			ServalBatPhoneApplication.context.preparation_activity
					.checkedRoot(result);
			return;
		case R.id.starChipsetSupported:
			ServalBatPhoneApplication.context.preparation_activity
					.checkedChipsetSupported(result);
			return;
		case R.id.starChipsetExperimental:
			ServalBatPhoneApplication.context.preparation_activity
					.checkedChipsetExperimental(result);
		}
		return;
	}
}
