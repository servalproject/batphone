package org.servalproject.system;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.content.Context;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiInfo;

public class WifiClientNetwork extends NetworkConfiguration {
	private final String SSID;
	private final String capabilities;
	private final boolean isSecure;
	final ScanResults results;
	public final WifiConfiguration config;
	private WifiInfo connection;
	private NetworkInfo networkInfo;

	public WifiClientNetwork(ScanResult scan, WifiConfiguration config) {
		this.SSID = scan.SSID;
		this.capabilities = scan.capabilities;
		results = new ScanResults(scan);
		this.isSecure = isSecure(scan);
		if (config == null && !isSecure) {
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
	public InetAddress getAddress() throws UnknownHostException {
		if (connection != null) {
			int addr = connection.getIpAddress();
			return Inet4Address.getByAddress(new byte[] {
					(byte) addr,
					(byte) (addr >> 8),
					(byte) (addr >> 16),
					(byte) (addr >> 24),
			});
		}
		return null;
	}

	@Override
	public String getSSID() {
		return this.SSID;
	}

	public void setNetworkInfo(NetworkInfo networkInfo) {
		this.networkInfo = networkInfo;
	}

	private String tidy(String src) {
		src = src.replace('_', ' ');
		src = src.substring(0, 1).toUpperCase()
				+ src.substring(1).toLowerCase();
		return src;
	}

	@Override
	public String getStatus(Context context) {
		if (this.networkInfo != null)
			return tidy(this.networkInfo.getDetailedState().toString());
		if (this.connection != null)
			return tidy(connection.getSupplicantState().toString());
		return null;
	}

	@Override
	public int getBars() {
		return results.getBars();
	}

	@Override
	public String getType() {
		if (isSecure)
			return "Secured";
		return "Open";
	}
}