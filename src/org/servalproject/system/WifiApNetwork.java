package org.servalproject.system;

import android.net.wifi.WifiConfiguration;

public class WifiApNetwork extends NetworkConfiguration {
	public final WifiConfiguration config;
	int networkState = WifiApControl.WIFI_AP_STATE_DISABLED;

	public WifiApNetwork(WifiConfiguration config) {
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

	public WifiConfiguration getConfig() {
		if (config == null)
			throw new NullPointerException();
		return config;
	}

	@Override
	public String toString() {
		return "HotSpot: "
				+ getSSID()
				+ (networkState == WifiApControl.WIFI_AP_STATE_DISABLED ? ""
						: " " + stateString());
	}

	public void setNetworkState(int state) {
		this.networkState = state;
	}

	@Override
	public String getSSID() {
		return getConfig().SSID;
	}
}