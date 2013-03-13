package org.servalproject.system;

import java.util.ArrayList;
import java.util.List;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public class WifiClientNetwork extends NetworkConfiguration {
	private final String capabilities;
	private int level;
	private List<ScanResult> scans = new ArrayList<ScanResult>();
	public final WifiConfiguration config;
	private WifiInfo connection;

	public WifiClientNetwork(ScanResult scan, WifiConfiguration config) {
		super(scan.SSID);
		this.capabilities = scan.capabilities;
		scans.add(scan);
		this.level = scan.level;
		if (config == null && !isSecure(scan)) {
			config = new WifiConfiguration();
			config.SSID = SSID;
			config.allowedKeyManagement.set(KeyMgmt.NONE);
		}
		this.config = config;
	}

	public void addResult(ScanResult result) {
		if (WifiManager.compareSignalLevel(level, result.level) < 0)
			level = result.level;
		scans.add(result);
	}

	@Override
	public String toString() {
		return SSID + (scans.size() > 1 ? " x" + scans.size() : "") + " - "
				+ (connection == null ? "" : connection
						.getSupplicantState() + " ")
				+ WifiManager.calculateSignalLevel(level, 5) + " bars";
	}

	public static boolean isSecure(ScanResult scan) {
		return scan.capabilities.contains("WEP") ||
				scan.capabilities.contains("PSK") ||
				scan.capabilities.contains("EAP");
	}

	public void setConnection(WifiInfo connection) {
		this.connection = connection;
	}
}