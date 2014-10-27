package org.servalproject.ui;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ShareUsActivity extends Activity {
	private static final String TAG = "ShareUsActivity";
	TextView shareWifi;

	private void updateHelpText() {
		ServalBatPhoneApplication app = (ServalBatPhoneApplication) this
				.getApplication();

		String ssid = null;
		InetAddress addr = null;

		try {
			if (app.nm.control.wifiManager.isWifiEnabled()){
				NetworkInfo networkInfo = app.nm.control.connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
				WifiInfo connection = app.nm.control.wifiManager.getConnectionInfo();
				if (networkInfo!=null && networkInfo.isConnected() && connection!=null) {
					int iAddr = connection.getIpAddress();
					addr = Inet4Address.getByAddress(new byte[]{
							(byte) iAddr,
							(byte) (iAddr >> 8),
							(byte) (iAddr >> 16),
							(byte) (iAddr >> 24),
					});
					ssid = connection.getSSID();
			}
			}else if(app.nm.control.wifiApManager.isWifiApEnabled()){
				WifiConfiguration conf = app.nm.control.wifiApManager.getWifiApConfiguration();
				if (conf!=null && conf.SSID!=null)
					ssid = conf.SSID;

				// TODO FIXME get the real AP network address
				addr = Inet4Address.getByAddress(new byte[] {
						(byte) 192, (byte) 168, 43, 1,
				});
			}
		} catch (UnknownHostException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		String helpText = null;
		if (addr != null && ssid != null)
			helpText = getString(R.string.share_wifi, ssid,"http://" + addr.getHostAddress()
							+ ":8080/");
		else
			helpText = getString(R.string.share_wifi_off);
		shareWifi.setText(helpText);
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
