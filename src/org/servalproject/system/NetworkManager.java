package org.servalproject.system;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public class NetworkManager {
	static final String TAG = "NetworkManager";
	private OnNetworkChange changes;
	private Map<String, WifiAdhocNetwork> adhocNetworks;
	private Map<String, WifiApNetwork> apNetworks;
	private Map<String, WifiClientNetwork> scannedNetworks;
	private final WifiControl control;

	public interface OnNetworkChange {
		public void onNetworkChange();
	}

	private static NetworkManager manager;

	public static synchronized NetworkManager getNetworkManager(Context context) {
		if (manager == null)
			manager = new NetworkManager(context);
		return manager;
	}

	private void getScanResults() {
		Map<String, WifiClientNetwork> scannedNetworks = new HashMap<String, WifiClientNetwork>();

		WifiInfo connection = control.wifiManager.getConnectionInfo();
		// build a map of pre-configured access points
		List<WifiConfiguration> configured = control.wifiManager
				.getConfiguredNetworks();
		Map<String, WifiConfiguration> configuredMap = new HashMap<String, WifiConfiguration>();

		if (configured != null) {
			for (WifiConfiguration c : configured) {
				configuredMap.put(c.SSID, c);
			}
		}

		// get scan results, and include any known config.
		List<ScanResult> results = control.wifiManager.getScanResults();
		if (results != null) {
			for (int i = 0; i < results.size(); i++) {
				ScanResult s = results.get(i);

				if (s.capabilities.contains("[IBSS]")) {
					WifiAdhocNetwork n = adhocNetworks.get(s.SSID);
					if (n != null) {
						n.addScanResult(s);
						continue;
					}
				}
				String key = s.SSID + s.capabilities;
				WifiClientNetwork conf = scannedNetworks.get(key);
				if (conf != null) {
					conf.addResult(s);
				} else {
					WifiConfiguration c = configuredMap.get(s.SSID);
					conf = new WifiClientNetwork(s, c);
					scannedNetworks.put(key, conf);
				}
				if (connection != null && connection.getBSSID().equals(s.BSSID)) {
					conf.setConnection(connection);
				}
			}
		}

		this.scannedNetworks = scannedNetworks;
		if (this.changes != null)
			changes.onNetworkChange();
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
				getScanResults();
			if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				int previousWifiState = intent.getIntExtra(
						WifiManager.EXTRA_PREVIOUS_WIFI_STATE,
						WifiManager.WIFI_STATE_UNKNOWN);
				int wifiState = intent.getIntExtra(
						WifiManager.EXTRA_WIFI_STATE,
						WifiManager.WIFI_STATE_UNKNOWN);
			}
			if (action.equals(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION))
				;
			if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION))
				;
			if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION))
				;
		}
	};

	private NetworkManager(Context context) {
		// TODO store configured networks in settings
		this.control = new WifiControl(context);

		adhocNetworks = new HashMap<String, WifiAdhocNetwork>();

		// TODO should we ever hide this network?
		adhocNetworks.put("mesh.servalproject.org", new WifiAdhocNetwork(
				"mesh.servalproject.org",
				"disabled", null, null, null, 1));
		// TODO other configured adhoc and AP networks

		if (control.wifiApManager != null) {
			// TODO, persist current AP config and deal with any user
			// changes....
			apNetworks = new HashMap<String, WifiApNetwork>();

			WifiConfiguration system = control.wifiApManager
					.getWifiApConfiguration();
			apNetworks.put(system.SSID, new WifiApNetwork(system));

			WifiConfiguration servalAp = new WifiConfiguration();
			servalAp.SSID = "ap.servalproject.org";
			servalAp.allowedAuthAlgorithms
					.set(WifiConfiguration.AuthAlgorithm.OPEN);
			apNetworks.put(servalAp.SSID, new WifiApNetwork(servalAp));
		}

		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		context.registerReceiver(receiver, filter);

		getScanResults();
	}

	public List<NetworkConfiguration> getNetworks() {
		List<NetworkConfiguration> ret = new ArrayList<NetworkConfiguration>();
		ret.addAll(adhocNetworks.values());
		if (apNetworks != null)
			ret.addAll(apNetworks.values());
		ret.addAll(scannedNetworks.values());
		return ret;
	}

	public void setNetworkChangeListener(OnNetworkChange changes) {
		this.changes = changes;
	}

	public void startScan() {
		// TODO force wifi client on...
		control.wifiManager.startScan();
	}


	// did our last test for adhoc support work?
	public boolean doesAdhocWork() {
		return false;
	}
}
