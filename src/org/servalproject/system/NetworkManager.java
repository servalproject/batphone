package org.servalproject.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.system.WifiControl.Completion;
import org.servalproject.system.WifiControl.CompletionReason;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class NetworkManager {
	static final String TAG = "NetworkManager";
	private OnNetworkChange changes;
	public final WifiControl control;
	private Map<String, WifiClientNetwork> scannedNetworks;

	public interface OnNetworkChange {
		public void onNetworkChange();
	}

	private static NetworkManager manager;

	public static synchronized NetworkManager getNetworkManager(Context context) {
		if (manager == null)
			manager = new NetworkManager(context);
		return manager;
	}

	public static boolean isOurApConfig(WifiConfiguration config) {
		return config.SSID.contains("servalproject");
	}

	public void updateApState() {
		if (this.changes != null)
			changes.onNetworkChange();
	}

	public void getScanResults() {
		Map<String, WifiClientNetwork> scannedNetworks = new HashMap<String, WifiClientNetwork>();

		for (WifiAdhocNetwork n : this.control.adhocControl.getNetworks()) {
			n.results = null;
		}

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
			List<ScanResult> resultsList = control.wifiManager.getScanResults();

			if (resultsList != null) {
				for (int i = 0; i < resultsList.size(); i++) {
					ScanResult s = resultsList.get(i);

					if (s.capabilities.contains("[IBSS]")) {
						WifiAdhocNetwork n = this.control.adhocControl
								.getNetwork(s.SSID);
						if (n != null) {
							if (n.results == null) {
								n.results = new ScanResults(s);
							} else {
								n.results.addResult(s);
							}
						}
					} else {
						String key = s.SSID + s.capabilities;
						WifiClientNetwork conf = scannedNetworks.get(key);
						if (conf != null) {
							conf.results.addResult(s);
						} else {
							WifiConfiguration c = configuredMap.get(s.SSID);
							conf = new WifiClientNetwork(s, c);
							scannedNetworks.put(key, conf);
							configuredMap.remove(s.SSID);
						}
						if (connection != null
								&& connection.getBSSID() != null
								&& connection.getBSSID().equals(s.BSSID)) {
							conf.setConnection(connection);
						}
					}
				}
			}
		}

		this.scannedNetworks = scannedNetworks;
		// TODO only trigger network change when there is a relevant change...
		if (this.changes != null)
			changes.onNetworkChange();
	}

	private static final String FLIGHT_MODE_PROFILE = "flight_mode_profile";

	private void setFlightModeProfile(WifiAdhocNetwork profile) {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		Editor ed = app.settings.edit();
		ed.putString(FLIGHT_MODE_PROFILE,
				profile == null ? null : profile.getSSID());
		ed.commit();
	}

	public void onFlightModeChanged(Intent intent) {
		boolean flightMode = intent.getBooleanExtra("state", false);
		if (flightMode) {
			setFlightModeProfile(this.control.adhocControl.getConfig());
			control.off(null);
		} else {
			ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
			String profileName = app.settings.getString(FLIGHT_MODE_PROFILE,
					null);
			WifiAdhocNetwork profile = this.control.adhocControl
					.getNetwork(profileName);
			connect(profile);
		}
	}

	public void onAdhocStateChanged() {
		if (changes != null)
			changes.onNetworkChange();
	}

	private NetworkManager(Context context) {
		// TODO store configured networks in settings
		this.control = new WifiControl(context);
		getScanResults();
	}

	public List<NetworkConfiguration> getNetworks() {
		List<NetworkConfiguration> ret = new ArrayList<NetworkConfiguration>();
		ret.addAll(this.control.adhocControl.getNetworks());
		if (control.wifiApManager != null) {
			ret.add(control.wifiApManager.userNetwork);
			ret.addAll(control.wifiApManager.getNetworks());
			control.wifiApManager.onApStateChanged(control.wifiApManager
					.getWifiApState());
		}
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

	public String getSSID() {
		if (this.control.wifiManager.isWifiEnabled()) {
			WifiInfo clientInfo = this.control.wifiManager.getConnectionInfo();
			return clientInfo != null ? clientInfo.getSSID() : null;
		}

		if (this.control.wifiApManager != null
				&& this.control.wifiApManager.isWifiApEnabled()) {
			WifiConfiguration config = control.wifiApManager
					.getWifiApConfiguration();
			return config == null ? null : config.SSID;
		}

		if (this.control.adhocControl.getState() == WifiAdhocControl.ADHOC_STATE_ENABLED) {
			WifiAdhocNetwork config = this.control.adhocControl.getConfig();
			return config == null ? null : config.getSSID();
		}

		return null;
	}

	public boolean isUsableNetworkConnected() {
		if (this.control.wifiManager.isWifiEnabled()) {
			WifiInfo clientInfo = this.control.wifiManager.getConnectionInfo();
			return (clientInfo != null && clientInfo.getSupplicantState() == SupplicantState.COMPLETED);
		}

		if (this.control.wifiApManager != null
				&& this.control.wifiApManager.isWifiApEnabled()) {
			return true;
		}

		if (this.control.adhocControl.getState() == WifiAdhocControl.ADHOC_STATE_ENABLED)
			return true;

		return false;
	}

	public void connect(NetworkConfiguration config) {
		try {
			if (config == null)
				return;

			setFlightModeProfile(null);
			if (config instanceof WifiClientNetwork) {
				WifiClientNetwork client = (WifiClientNetwork) config;
				if (client.config == null)
					throw new IOException(client.getSSID()
							+ " requires a password that I don't know");
				control.connectClient(client.config, null);
			} else if (config instanceof WifiApNetwork) {
				WifiApNetwork network = (WifiApNetwork) config;
				if (network.config == null) {
					control.connectAp(null);
				} else {
					control.connectAp(network.config, null);
				}
			} else if (config instanceof WifiAdhocNetwork) {
				control.connectAdhoc((WifiAdhocNetwork) config, null);
			} else if (config == wifiClient) {
				startScan();
			} else {
				throw new IOException("Unsupported network type");
			}
		} catch (IOException e) {
			Log.e("Networks", e.getMessage(), e);
			ServalBatPhoneApplication.context.displayToastMessage(e
					.getMessage());
		}
	}

	private WifiClient wifiClient = new WifiClient();
	class WifiClient extends NetworkConfiguration {

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
			return "Client Mode " + stateString();
		}

		@Override
		public String getSSID() {
			return "Client Mode";
		}
	}

}