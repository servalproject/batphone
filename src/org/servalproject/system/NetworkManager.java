package org.servalproject.system;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.servalproject.system.WifiControl.Completion;
import org.servalproject.system.WifiControl.CompletionReason;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class NetworkManager {
	static final String TAG = "NetworkManager";
	private OnNetworkChange changes;
	private Map<String, WifiAdhocNetwork> adhocNetworks;
	private Map<String, WifiApNetwork> apNetworks;
	private Map<String, WifiClientNetwork> scannedNetworks;
	public final WifiControl control;

	public interface OnNetworkChange {
		public void onNetworkChange();
	}

	private static NetworkManager manager;

	public static synchronized NetworkManager getNetworkManager(Context context) {
		if (manager == null)
			manager = new NetworkManager(context);
		return manager;
	}

	private void updateApState() {
		int state = this.control.wifiApManager.getWifiApState();
		WifiConfiguration config = this.control.wifiApManager
				.getWifiApConfiguration();

		for (WifiApNetwork n : apNetworks.values()) {
			if (this.control.compare(n.config, config)) {
				n.setNetworkState(state);
			} else
				n.setNetworkState(-1);
		}
		if (this.changes != null)
			changes.onNetworkChange();
	}

	private void getScanResults() {
		Map<String, WifiClientNetwork> scannedNetworks = new HashMap<String, WifiClientNetwork>();

		if (control.wifiManager.isWifiEnabled()) {
			WifiInfo connection = control.wifiManager.getConnectionInfo();
			// build a map of pre-configured access points
			List<WifiConfiguration> configured = control.wifiManager
					.getConfiguredNetworks();
			Map<String, WifiConfiguration> configuredMap = new HashMap<String, WifiConfiguration>();

			if (configured != null) {
				for (WifiConfiguration c : configured) {
					String ssid = c.SSID;
					if (ssid.startsWith("\"") && ssid.endsWith("\""))
						ssid = ssid.substring(1, ssid.length() - 1);
					configuredMap.put(ssid, c);
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
						configuredMap.remove(s.SSID);
					}
					if (connection != null && connection.getBSSID() != null
							&& connection.getBSSID().equals(s.BSSID)) {
						conf.setConnection(connection);
					}
				}

				for (WifiConfiguration c : configuredMap.values()) {
					Log.v(TAG, c.SSID + " not found in scan");
				}
			}
		}

		this.scannedNetworks = scannedNetworks;
		// TODO only trigger network change when there is a relevant change...
		if (this.changes != null)
			changes.onNetworkChange();
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION))
				getScanResults();
			if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
				getScanResults();
			if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION))
				getScanResults();
			if (action.equals(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION))
				updateApState();
			if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION))
				;
			if (action.equals(WifiAdhocControl.ADHOC_STATE_CHANGED_ACTION)) {
				if (changes != null)
					changes.onNetworkChange();
			}
		}
	};

	private NetworkManager(Context context) {
		// TODO store configured networks in settings
		this.control = new WifiControl(context);

		adhocNetworks = new HashMap<String, WifiAdhocNetwork>();

		try {
			String ssid = "mesh.servalproject.org";
			byte addrBytes[] = new byte[] {
					28, 0, 0, 0
			};
			byte maskBytes[] = WifiAdhocNetwork.lengthToMask(7);
			WifiAdhocNetwork.randomiseAddress(addrBytes, maskBytes);

			adhocNetworks.put(ssid, WifiAdhocNetwork.getAdhocNetwork(ssid,
					"disabled", addrBytes, maskBytes, 1));
		} catch (UnknownHostException e) {
			Log.e(TAG, e.getMessage(), e);
		}
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
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION);
		filter.addAction(WifiAdhocControl.ADHOC_STATE_CHANGED_ACTION);
		context.registerReceiver(receiver, filter);

		getScanResults();
	}

	public List<NetworkConfiguration> getNetworks() {
		List<NetworkConfiguration> ret = new ArrayList<NetworkConfiguration>();
		ret.addAll(adhocNetworks.values());
		if (apNetworks != null)
			ret.addAll(apNetworks.values());
		if (scannedNetworks.isEmpty())
			ret.add(wifiClient);
		else
			ret.addAll(scannedNetworks.values());
		return ret;
	}

	public void setNetworkChangeListener(OnNetworkChange changes) {
		this.changes = changes;
	}

	public void startScan() {
		control.startClientMode(new Completion() {
			@Override
			public void onFinished(CompletionReason reason) {
				if (reason == CompletionReason.Success)
					control.wifiManager.startScan();
			}
		});
	}

	// did our last test for adhoc support work?
	public boolean doesAdhocWork() {
		return ChipsetDetection.getDetection().isModeSupported(WifiMode.Adhoc);
	}

	public void connect(NetworkConfiguration config) throws IOException {
		if (config == null)
			throw new IOException();

		if (config instanceof WifiClientNetwork) {
			WifiClientNetwork client = (WifiClientNetwork) config;
			if (client.config == null)
				throw new IOException(client.SSID
						+ " requires a password that I don't know");
			control.connectClient(client.config, null);
		} else if (config instanceof WifiApNetwork) {
			control.connectAp(((WifiApNetwork) config).config, null);
		} else if (config instanceof WifiAdhocNetwork) {
			control.connectAdhoc((WifiAdhocNetwork) config, null);
		} else if (config == wifiClient) {
			startScan();
		} else {
			throw new IOException("Unsupported network type");
		}
	}

	private WifiClient wifiClient = new WifiClient();
	class WifiClient extends NetworkConfiguration {
		public WifiClient() {
			super("Client Mode");
		}

		private String stateString() {
			switch (control.wifiManager.getWifiState()) {
			case WifiManager.WIFI_STATE_DISABLED:
				return "Disabled";
			case WifiManager.WIFI_STATE_ENABLED:
				return "Enabled";
			case WifiManager.WIFI_STATE_DISABLING:
				return "Disabling";
			case WifiManager.WIFI_STATE_ENABLING:
				return "Enabling";
			case WifiManager.WIFI_STATE_UNKNOWN:
				return "Unknown";
			}
			return "";
		}

		@Override
		public String toString() {
			return super.toString() + " " + stateString();
		}
	}

}