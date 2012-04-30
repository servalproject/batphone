package org.servalproject.batphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		Log.d("BatPhoneReceiver", "Got an intent: " + intent.toString());
	}

}
