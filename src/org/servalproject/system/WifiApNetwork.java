package org.servalproject.system;

import android.net.wifi.WifiConfiguration;

public class WifiApNetwork extends NetworkConfiguration {
	final WifiConfiguration config;

	public WifiApNetwork(WifiConfiguration config) {
		super(config.SSID);
		this.config = config;
	}
}