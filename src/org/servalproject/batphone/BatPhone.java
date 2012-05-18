package org.servalproject.batphone;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.DidResult;
import org.servalproject.servald.SubscriberId;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
		if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
			Log.d("BatPhoneReceiver", "Got an intent: " + intent.toString());
			String number = getResultData();

			// Set result data to null if we are claiming the call, else set
			// the result data to the number so that someone else can take it.

			// Let the system complete the call if we have said we aren't
			// interested
			// in it.
			if (dialed_number != null && dialed_number.equals(number)
					&& (SystemClock.elapsedRealtime() - dial_time) < 3000) {
				return;
			}

			// Don't try to complete the call while we are
			// giving the user the choice of how to handle it.
			setResultData(null);

			// Send call to director to select how to handle it.
			Intent myIntent = new Intent(ServalBatPhoneApplication.context,
					CallDirector.class);
			// Create call as a standalone activity stack
			myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			myIntent.putExtra("phone_number", number);
			// Uncomment below if we want to allow multiple mesh calls in
			// progress
			// myIndent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
			ServalBatPhoneApplication.context.startActivity(myIntent);
		}
	}

	public static void callBySid(DidResult result) {
		callBySid(result.sid, result.did, result.name);
	}

	public static void callBySid(SubscriberId sid, String did, String name) {
		// Assume SID has not been authenticated, and thus take the unsecured
		// calling path.
		Log.d("BatPhone", "Calling sid " + sid);

		// Send call to distributor to select how to handle it.
		Intent myIntent = new Intent(ServalBatPhoneApplication.context,
				UnsecuredCall.class);
		if (sid != null)
			myIntent.putExtra("sid", sid.toString());
		if (did != null)
			myIntent.putExtra("did", did);
		if (name != null)
			myIntent.putExtra("name", name);
		// Create call as a standalone activity stack
		myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		// Uncomment below if we want to allow multiple mesh calls in progress
		// myIndent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		ServalBatPhoneApplication.context.startActivity(myIntent);
	}

}
