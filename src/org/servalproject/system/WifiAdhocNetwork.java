package org.servalproject.system;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.List;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiAdhocNetwork extends NetworkConfiguration {
	private final String txPower;
	private final Inet4Address address;
	private final Inet4Address netmask;
	private final Inet4Address gateway;
	private final int channel;
	private int level = -1000;
	private List<ScanResult> results;

	public WifiAdhocNetwork(String SSID, String txPower,
			Inet4Address address, Inet4Address netmask,
			Inet4Address gateway, int channel) {
		super(SSID);
		this.txPower = txPower;
		this.address = address;
		this.netmask = netmask;
		this.gateway = gateway;
		this.channel = channel;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return "Adhoc: " + SSID + " " +
				+WifiManager.calculateSignalLevel(level, 5) + "bars";
	}

	public void addScanResult(ScanResult result) {
		if (results == null)
			results = new ArrayList<ScanResult>();
		if (results.isEmpty()
				|| WifiManager.compareSignalLevel(level, result.level) > 0)
			level = result.level;
		results.add(result);
	}

	@Override
	public void connect() {
		Log.v(NetworkManager.TAG, "TODO connect/start adhoc network " + SSID);
	}
}