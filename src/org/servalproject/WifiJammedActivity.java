package org.servalproject;

import android.app.Activity;
import android.os.Bundle;

public class WifiJammedActivity extends Activity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

		setContentView(R.layout.wifijammedlayout);
		ServalBatPhoneApplication.terminate = true;
	}

}
