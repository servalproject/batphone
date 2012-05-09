package org.servalproject.ui;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class ShareUsActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		setContentView(R.layout.shareus);

		Button shareUs = (Button) findViewById(R.id.share_us_button);
		shareUs.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (ServalBatPhoneApplication.context != null) {
					ServalBatPhoneApplication.context.shareViaBluetooth();
				}
			}
		});

	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

}
