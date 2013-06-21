package org.servalproject.ui;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.system.NetworkConfiguration;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ShareUsActivity extends Activity {
	TextView shareWifi, shareWifiOff;
	String orig;

	private void updateHelpText() {
		ServalBatPhoneApplication app = (ServalBatPhoneApplication) this
				.getApplication();

		NetworkConfiguration config = app.nm.getActiveNetwork();
		String ssid = null;
		InetAddress addr = null;
		if (config != null) {
			ssid = config.getSSID();
			try {
				addr = config.getAddress();
			} catch (UnknownHostException e) {
				Log.e("ShareUs", e.getMessage(), e);
			}
		}

		if (addr != null && ssid != null) {
			String helpText = orig;
			if (ssid != null)
				helpText = helpText.replace("[SSID]", ssid);

			// TODO get this url from the network interface
			helpText = helpText.replace("[URL]",
					"http://" + addr.getHostAddress()
							+ ":8080/");

			shareWifi.setText(helpText);
			shareWifi.setVisibility(View.VISIBLE);
			shareWifiOff.setVisibility(View.INVISIBLE);
		} else {
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
