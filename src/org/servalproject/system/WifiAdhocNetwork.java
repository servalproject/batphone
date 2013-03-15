package org.servalproject.system;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.servalproject.ServalBatPhoneApplication;

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
	private int state = WifiAdhocControl.ADHOC_STATE_DISABLED;
	private List<ScanResult> results;

	public static byte[] lengthToMask(int length) {
		byte maskBytes[] = new byte[4];

		for (int i = 0; i < 4; i++) {
			int bits = length;
			if (bits < 0)
				bits = 0;
			if (bits > 8)
				bits = 8;
			// 0xFF ^ ((2^n)-1)
			maskBytes[i] = (byte) (0xFF ^ ((1 << (8 - bits)) - 1));
			length -= 8;
		}
		return maskBytes;
	}

	// test code;
	// static {
	// for (int i = 0; i < 32; i++) {
	// Log.v("testLengthToMask",
	// i + " = " + Arrays.toString(lengthToMask(i)));
	// }
	// }

	public static void randomiseAddress(byte addrBytes[], byte maskBytes[]) {
		SecureRandom random = new SecureRandom();
		byte[] randomBytes = new byte[4];

		random.nextBytes(randomBytes);
		for (int i = 0; i < addrBytes.length; i++) {
			addrBytes[i] = (byte) ((addrBytes[i] & maskBytes[i])
					| (randomBytes[i] & ~maskBytes[i]));
		}
	}

	public static WifiAdhocNetwork getAdhocNetwork(String SSID, String txPower,
			byte addrBytes[], byte maskBytes[], int channel)
			throws UnknownHostException {

		Inet4Address addr = (Inet4Address) Inet4Address.getByAddress(addrBytes);
		Inet4Address mask = (Inet4Address) Inet4Address.getByAddress(maskBytes);

		Inet4Address gateway = addr;
		return new WifiAdhocNetwork(SSID, txPower, addr, mask, gateway, channel);
	}

	public WifiAdhocNetwork(String SSID, String txPower,
			Inet4Address address, Inet4Address netmask,
			Inet4Address gateway, int channel) {
		super(SSID);

		if (address == null)
			throw new NullPointerException();

		if (netmask == null)
			throw new NullPointerException();

		if (gateway == null)
			gateway = address;

		this.txPower = txPower;
		this.address = address;
		this.netmask = netmask;
		this.gateway = gateway;
		this.channel = channel;
	}

	private void updateAdhocConf() {
		try {
			Properties props = new Properties();
			CoreTask coretask = ServalBatPhoneApplication.context.coretask;
			String adhoc = coretask.DATA_FILE_PATH + "/conf/adhoc.conf";

			props.load(new FileInputStream(adhoc));

			props.put("wifi.essid", SSID);
			props.put("ip.network", address.getHostAddress());
			props.put("ip.netmask", netmask.getHostAddress());
			props.put("ip.gateway", gateway.getHostAddress());
			props.put("wifi.interface", coretask.getProp("wifi.interface"));
			props.put("wifi.txpower", txPower);

			props.store(new FileOutputStream(adhoc), null);
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}

	private void updateTiWLANConf() {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		String find[] = new String[] {
				"WiFiAdhoc", "dot11DesiredSSID",
				"dot11DesiredChannel", "dot11DesiredBSSType", "dot11PowerMode"
		};
		String replace[] = new String[] {
				"1", SSID, Integer.toString(channel), "0", "1"
		};

		app.replaceInFile("/system/etc/wifi/tiwlan.ini",
						app.coretask.DATA_FILE_PATH + "/conf/tiwlan.ini", find,
						replace);
	}

	public void updateConfiguration() {
		updateAdhocConf();
		updateTiWLANConf();
	}

	public void setNetworkState(int state) {
		this.state = state;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return "Adhoc: " + SSID + " " + WifiAdhocControl.stateString(state)
				+ " " +
				+WifiManager.calculateSignalLevel(level, 5) + " bars";
	}

	public void addScanResult(ScanResult result) {
		if (results == null)
			results = new ArrayList<ScanResult>();
		if (results.isEmpty()
				|| WifiManager.compareSignalLevel(level, result.level) > 0)
			level = result.level;
		results.add(result);
	}
}