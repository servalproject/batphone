package org.servalproject.system;

import android.net.wifi.WifiConfiguration;

public class WifiApNetwork extends NetworkConfiguration {
	final WifiConfiguration config;
	int networkState;
	final String SSID;

	public WifiApNetwork(WifiConfiguration config) {
		this.SSID = config.SSID;
		this.config = config;
	}

	private String stateString() {
		switch (networkState) {
		case WifiApControl.WIFI_AP_STATE_DISABLED:
			return "Disabled";
		case WifiApControl.WIFI_AP_STATE_ENABLED:
			return "Enabled";
		case WifiApControl.WIFI_AP_STATE_DISABLING:
			return "Disabling";
		case WifiApControl.WIFI_AP_STATE_ENABLING:
			return "Enabling";
		case WifiApControl.WIFI_AP_STATE_FAILED:
			return "Failed";
		}
		return "";
	}
	@Override
	public String toString() {
		return "HotSpot: " + this.SSID + " " + stateString();
	}

	public void setNetworkState(int state) {
		this.networkState = state;
	}

	@Override
	public String getSSID() {
		return SSID;
	}
}