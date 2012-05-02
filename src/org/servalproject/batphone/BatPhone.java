package org.servalproject.batphone;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.DidResult;
import org.servalproject.servald.SubscriberId;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

	public void call(String phoneNumber) {
		// TODO Auto-generated method stub

	}

	static String dialed_number = null;
	static long dial_time = 0;

	public static String getDialedNumber()
	{
		return dialed_number;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		Log.d("BatPhoneReceiver", "Got an intent: " + intent.toString());
		String intentAction = intent.getAction();
		String number = getResultData();
		Boolean force = false;

		// Set result data to null if we are claiming the call, else set
		// the result data to the number so that someone else can take it.

		// Let the system complete the call if we have said we aren't interested
		// in it.
		if (dialed_number != null && dialed_number.equals(number)
				&& (SystemClock.elapsedRealtime() - dial_time) < 3000) {
			setResultData(number);
			return;
		}

		// Remember this calling attempt
		dialed_number = number;
		dial_time = SystemClock.elapsedRealtime();

		// Don't let anyone else try to complete the call while we are
		// giving the user the choice of how to handle it.
		setResultData(null);

		// Send call to distributor to select how to handle it.
		Intent myIntent = new Intent(ServalBatPhoneApplication.context,
				CallDirector.class);
		// Create call as a standalone activity stack
		myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		myIntent.putExtra("phone_number", number);
		// Uncomment below if we want to allow multiple mesh calls in progress
		// myIndent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		ServalBatPhoneApplication.context.startActivity(myIntent);
	}

	public static void ignoreCall(String number) {
		// Make this call by the normal method
		// (more correctly, tell us to ignore this number if someone
		// dials it in the next 3 seconds)
		dial_time = SystemClock.elapsedRealtime();
		dialed_number = number;
	}

	public static void cancelCall() {
		dial_time = 0;
		dialed_number = null;
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
