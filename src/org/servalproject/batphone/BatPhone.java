package org.servalproject.batphone;

import org.servalproject.Control;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.servald.SubscriberId;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;


public class BatPhone extends BroadcastReceiver {

	static BatPhone instance = null;

	public BatPhone() {
		instance = this;
	}

	public static BatPhone getEngine() {
		// TODO Auto-generated method stub
		if (instance == null)
			instance = new BatPhone();
		return instance;
	}

	public static void call(String phoneNumber) {
		// make call by cellular/normal means
		// we need to ignore this number when it is dialed in the next 3 seconds

		dial_time = SystemClock.elapsedRealtime();
		dialed_number = phoneNumber;

		String url = "tel:" + phoneNumber;
		Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
		ServalBatPhoneApplication.context.startActivity(intent);
	}

	static String dialed_number = null;
	static long dial_time = 0;

	public static String getDialedNumber()
	{
		return dialed_number;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		// Log.d("BatPhoneReceiver", "Got an intent: " + intent.toString());
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;

		if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {

			if (app.getState() != State.On)
				return;

			String number = getResultData();

			// Set result data to null if we are claiming the call, else set
			// the result data to the number so that someone else can take it.

			// Let the system complete the call if we have said we aren't
			// interested in it.
			if (dialed_number != null && dialed_number.equals(number)
					&& (SystemClock.elapsedRealtime() - dial_time) < 3000) {
				return;
			}

			// Don't try to complete the call while we are
			// giving the user the choice of how to handle it.
			setResultData(null);

			// Send call to director to select how to handle it.
			Intent myIntent = new Intent(app, CallDirector.class);
			// Create call as a standalone activity stack
			myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			myIntent.putExtra("phone_number", number);
			// Uncomment below if we want to allow multiple mesh calls in
			// progress
			// myIndent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
			app.startActivity(myIntent);

		} else if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			// force a re-test of root permission
			Editor ed = app.settings.edit();
			ed.putInt("has_root", 0);
			ed.commit();

		} else if (intent.getAction().equals(
				Intent.ACTION_AIRPLANE_MODE_CHANGED)) {

			boolean flightMode = intent.getBooleanExtra("state", false);
			if (flightMode) {
				if (app.getState() == State.On) {
					// stop our software completely when flight mode starts
					// but remember that it was running
					app.stopService(new Intent(app, Control.class));

					Editor ed = app.settings.edit();
					ed.putBoolean("start_after_flight_mode", true);
					ed.commit();

					// note, whenever the control service is started this
					// setting will be forgotten, so we'll only restart if the
					// software was not turned on in flight mode

				}
			} else {

				if (app.settings.getBoolean("start_after_flight_mode", false)) {
					// if we were running before flight mode, restart
					app.startService(new Intent(app, Control.class));
				}
			}
		}
	}

	public static void callBySid(SubscriberId sid) {
		if (sid == null)
			throw new IllegalArgumentException("Subscriber id must be supplied");
		// Assume SID has not been authenticated, and thus take the unsecured
		// calling path.
		Log.d("BatPhone", "Calling sid " + sid);

		// Send call to distributor to select how to handle it.
		Intent myIntent = new Intent(ServalBatPhoneApplication.context,
				UnsecuredCall.class);
		myIntent.putExtra("sid", sid.toString());
		// Create call as a standalone activity stack
		myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		// Uncomment below if we want to allow multiple mesh calls in progress
		// myIndent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		ServalBatPhoneApplication.context.startActivity(myIntent);
	}

}
