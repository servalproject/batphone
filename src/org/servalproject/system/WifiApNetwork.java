package org.servalproject.system;

import android.net.wifi.WifiConfiguration;
import android.util.Log;

public class WifiApNetwork extends NetworkConfiguration {
	final WifiConfiguration config;

	public WifiApNetwork(WifiConfiguration config) {
		super(config.SSID);
		this.config = config;
	}

	@Override
	public void connect() {
		Log.v(NetworkManager.TAG, "TODO start AP network " + SSID);
	}
}