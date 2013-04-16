package org.servalproject.system;

import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiInfo;

public class WifiClientNetwork extends NetworkConfiguration {
	private final String SSID;
	private final String capabilities;
	final ScanResults results;
	public final WifiConfiguration config;
	private WifiInfo connection;
	private NetworkInfo networkInfo;

	public WifiClientNetwork(ScanResult scan, WifiConfiguration config) {
		this.SSID = scan.SSID;
		this.capabilities = scan.capabilities;
		results = new ScanResults(scan);
		if (config == null && !isSecure(scan)) {
			config = new WifiConfiguration();
			config.SSID = SSID;
			config.allowedKeyManagement.set(KeyMgmt.NONE);
		}
		this.config = config;
	}

	@Override
	public String toString() {
		String state = null;
		if (this.networkInfo != null)
			state = this.networkInfo.getDetailedState().toString();
		else if (this.connection != null)
			state = connection.getSupplicantState().toString();

		return SSID + (state == null ? "" : " " + state)
				+ " - " + results.toString();
	}

	public static boolean isSecure(ScanResult scan) {
		return scan.capabilities.contains("WEP") ||
				scan.capabilities.contains("PSK") ||
				scan.capabilities.contains("EAP");
	}

	public void setConnection(WifiInfo connection) {
		this.connection = connection;
	}

	@Override
	public String getSSID() {
		return this.SSID;
	}

	public void setNetworkInfo(NetworkInfo networkInfo) {
		this.networkInfo = networkInfo;
	}

	@Override
	public String getStatus() {
		if (this.networkInfo != null)
			return this.networkInfo.getDetailedState().toString();
		if (this.connection != null)
			return connection.getSupplicantState().toString();
		return null;
	}

	@Override
	public int getBars() {
		return results.getBars();
	}
}