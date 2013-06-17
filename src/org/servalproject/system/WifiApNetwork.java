package org.servalproject.system;

import org.servalproject.R;

import android.content.Context;
import android.net.wifi.WifiConfiguration;

public class WifiApNetwork extends NetworkConfiguration {
	public final WifiConfiguration config;
	int networkState = WifiApControl.WIFI_AP_STATE_DISABLED;

	public WifiApNetwork(WifiConfiguration config) {
		this.config = config;
	}

	private String stateString(Context context) {
		switch (networkState) {
		case WifiApControl.WIFI_AP_STATE_DISABLED:
			return context.getString(R.string.wifi_disabled);
		case WifiApControl.WIFI_AP_STATE_ENABLED:
			return context.getString(R.string.wifi_enabled);
		case WifiApControl.WIFI_AP_STATE_DISABLING:
			return context.getString(R.string.wifi_disabling);
		case WifiApControl.WIFI_AP_STATE_ENABLING:
			return context.getString(R.string.wifi_enabling);
		case WifiApControl.WIFI_AP_STATE_FAILED:
			return context.getString(R.string.wifi_error);
		}
		return null;
	}

	public WifiConfiguration getConfig() {
		if (config == null)
			throw new NullPointerException();
		return config;
	}

	@Override
	public String toString() {
		String ssid = getSSID();
		if (ssid == null)
			ssid = "Android configuration";
		return ssid;
	}

	public void setNetworkState(int state) {
		this.networkState = state;
	}

	@Override
	public String getSSID() {
		String ssid = getConfig().SSID;
		return ssid == null ? "Android configuration" : ssid;
	}

	@Override
	public String getStatus(Context context) {
		return (networkState == WifiApControl.WIFI_AP_STATE_DISABLED ? null
				: stateString(context));
	}

	@Override
	public int getBars() {
		return -1;
	}

	@Override
	public String getType() {
		return "HotSpot";
	}
}