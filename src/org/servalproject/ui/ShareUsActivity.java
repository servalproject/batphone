package org.servalproject.ui;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.system.WifiMode;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ShareUsActivity extends Activity {
	TextView shareWifi, shareWifiOff;
	String orig;

	private void updateHelpText() {
		ServalBatPhoneApplication app = (ServalBatPhoneApplication) this
				.getApplication();
		String ssid = app.nm.getSSID();
		String helpText = orig;
		if (ssid!=null)
			helpText = helpText.replace("[SSID]", ssid);

		// TODO get this url from the network interface
		helpText = helpText.replace("[URL]", "http://192.168.43.1:8080/");

		shareWifi.setText(helpText);

		switch (WifiMode.Ap) {
		case Ap:
			shareWifi.setVisibility(View.VISIBLE);
			shareWifiOff.setVisibility(View.INVISIBLE);
			break;
		default:
			shareWifi.setVisibility(View.INVISIBLE);
			shareWifiOff.setVisibility(View.VISIBLE);
		}
	}

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

		shareWifi = (TextView) findViewById(R.id.share_wifi);
		shareWifiOff = (TextView) findViewById(R.id.share_wifi_off);
		orig = shareWifi.getText().toString();
		updateHelpText();
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
