package org.servalproject.system;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servaldna.ServalDInterfaceException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkManager {
	static final String TAG = "NetworkManager";
	public final WifiControl control;
	public final BlueToothControl blueToothControl;
	private static NetworkManager manager;

	public static synchronized NetworkManager getNetworkManager(Context context) {
		if (manager == null)
			manager = new NetworkManager(context);
		return manager;
	}

	// merge scan results based on SSID and capabilities
	public Collection<ScanResults> getScanResults(){
		if (!control.wifiManager.isWifiEnabled())
			return null;
		Map<String, ScanResults> newResults = new HashMap<String, ScanResults>();
		List<ScanResult> resultsList = control.wifiManager.getScanResults();
		if (resultsList!=null){
			// build a map of pre-configured access points
			List<WifiConfiguration> configured = control.wifiManager
					.getConfiguredNetworks();
			Map<String, WifiConfiguration> configuredMap = new HashMap<String, WifiConfiguration>();

			if (configured != null) {
				for (WifiConfiguration c : configured) {
					if (c.BSSID!=null && !c.BSSID.equals("")){
						configuredMap.put(c.BSSID, c);
					}else {
						String ssid = c.SSID;
						if (ssid == null)
							continue;
						if (ssid.startsWith("\"") && ssid.endsWith("\""))
							ssid = ssid.substring(1, ssid.length() - 1);
						configuredMap.put(ssid, c);
					}
				}
			}

			for (ScanResult s:resultsList){
				String key = s.SSID+"|"+s.capabilities;
				ScanResults res = newResults.get(key);
				if (res==null){
					res = new ScanResults(s);
					newResults.put(key, res);

					WifiConfiguration c = configuredMap.get(s.SSID);
					if (c!=null){
						configuredMap.remove(s.SSID);
						res.setConfiguration(c);
					}
				}else{
					res.addResult(s);
				}

				WifiConfiguration c = configuredMap.get(s.BSSID);
				if (c!=null){
					configuredMap.remove(s.BSSID);
					res.setConfiguration(c);
				}
			}
		}
		return newResults.values();
	}

	public void onFlightModeChanged(Intent intent) {
		if (intent.getBooleanExtra("state", false))
			control.off(null);
	}

	private NetworkManager(Context context) {
		this.control = new WifiControl(context);
		BlueToothControl b=null;
		try {
			ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
			b = app.server.getBlueToothControl(context);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (ServalDInterfaceException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		this.blueToothControl=b;
	}

	public InetAddress getAddress() throws SocketException {
		// TODO get actual address from interface
		for (Enumeration<NetworkInterface> interfaces = NetworkInterface
				.getNetworkInterfaces(); interfaces.hasMoreElements();) {
			NetworkInterface i = interfaces.nextElement();
			for (Enumeration<InetAddress> enumIpAddress = i.getInetAddresses();
				 enumIpAddress.hasMoreElements();) {
				InetAddress iNetAddress = enumIpAddress.nextElement();
				if (!iNetAddress.isLoopbackAddress()) {
					// TODO Make sure we don't return cellular interface....
					return iNetAddress;
				}
			}
		}
		return null;
	}

	public boolean isUsableNetworkConnected() {
		if (this.control.wifiManager.isWifiEnabled()) {
			NetworkInfo networkInfo = control.connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			if (networkInfo==null)
				return false;
			return networkInfo.isConnected();
		}

		if (this.control.wifiApManager != null
				&& this.control.wifiApManager.isWifiApEnabled()) {
			return true;
		}

		if (this.control.adhocControl.getState() == NetworkState.Enabled)
			return true;

		return false;
	}

	public void onStopService() {
		// turn off adhoc if running...
		this.control.turnOffAdhoc();
	}
}