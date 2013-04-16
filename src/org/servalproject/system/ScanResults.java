package org.servalproject.system;

import java.util.ArrayList;
import java.util.List;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

public class ScanResults {
	private final String SSID;
	private final String capabilities;
	private int level;
	private List<ScanResult> results = new ArrayList<ScanResult>();

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

	@Override
	public String toString() {
		return (results.size() > 1 ? "x" + results.size() + " " : "") +
				getBars() + " bars";
	}
}
