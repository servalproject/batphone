package org.servalproject.system;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.List;

public class ScanResults {
	public final String SSID;
	public final String capabilities;
	private int level;
	public final List<ScanResult> results = new ArrayList<ScanResult>();
	private WifiConfiguration wifiConfig;

	public ScanResults(ScanResult result) {
		this.SSID = result.SSID;
		this.capabilities = result.capabilities;
		this.level = result.level;
		results.add(result);
	}

	public void addResult(ScanResult result) {
		if (WifiManager.compareSignalLevel(level, result.level) < 0)
			level = result.level;
		results.add(result);
	}

	public int getBars() {
		return WifiManager.calculateSignalLevel(level, 5);
	}

	public boolean isAdhoc(){
		return capabilities.contains("[IBSS]");
	}

	public boolean isSecure() {
		return capabilities.contains("WEP") ||
				capabilities.contains("PSK") ||
				capabilities.contains("EAP");
	}

	@Override
	public String toString() {
		return (results.size() > 1 ? "x" + results.size() + " " : "") +
				getBars() + " bars";
	}

	public void setConfiguration(WifiConfiguration wifiConfig) {
		this.wifiConfig = wifiConfig;
	}

	public WifiConfiguration getConfiguration(){
		return wifiConfig;
	}
}
