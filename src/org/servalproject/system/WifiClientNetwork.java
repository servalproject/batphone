package org.servalproject.system;

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
		return SSID + (connection == null ? "" : " - " + connection
						.getSupplicantState() + " ")
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
}