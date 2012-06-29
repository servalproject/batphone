package org.servalproject;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import android.app.Activity;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class PairingActivity extends Activity {

	public ServalBatPhoneApplication app;

	private WifiManager wifiManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.app = (ServalBatPhoneApplication) this.getApplication();
		setContentView(R.layout.pair);

		wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();

		TextView pairLabel = (TextView) findViewById(R.id.pairLabel);
		pairLabel.setText("Current pairing: "
				+ (ServalBatPhoneApplication.pairingSid == null ? ""
						: ServalBatPhoneApplication.pairingSid));

		TextView networks = (TextView) findViewById(R.id.configuredNetworks);
		HashMap<String, ScanResult> availableNetworks = getAvailableNetworks();
		StringBuilder sb = new StringBuilder();
		for (Entry<String, ScanResult> entry : availableNetworks.entrySet()) {
			sb.append(entry.getKey()).append("\n");
		}
		networks.setText(sb.toString());
	}

	private HashMap<String, ScanResult> getAvailableNetworks() {
		List<ScanResult> configuredNetworks = wifiManager
				.getScanResults();
		HashMap<String, ScanResult> availableNetworks = new HashMap<String, ScanResult>();
		if (configuredNetworks != null) {
			for (ScanResult c : configuredNetworks) {
				Log.i(getLocalClassName(), c.SSID);
				availableNetworks.put(c.SSID, c);
			}
		}
		return availableNetworks;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

}
