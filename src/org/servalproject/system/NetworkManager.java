package org.servalproject.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.servalproject.ServalBatPhoneApplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
	private WifiClientNetwork connectedNetwork;
	private NetworkInfo networkInfo;

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

		WifiClientNetwork connectedNetwork = null;

		if (control.wifiManager.isWifiEnabled()) {
			WifiInfo connection = control.wifiManager.getConnectionInfo();
			if (connection != null
					&& connection.getSupplicantState() == SupplicantState.DISCONNECTED)
				connection = null;
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

			if (this.control.wifiApManager != null) {
				// make sure we have matching configuration for all of our known
				// hotspot configurations
				for (WifiApNetwork n : this.control.wifiApManager.getNetworks()) {
					if (n.config == null)
						continue;
					if (configuredMap.containsKey(n.getSSID()))
						continue;
					int id = this.control.addNetwork(n.config);
					this.control.wifiManager.enableNetwork(id, false);
				}
			}

			// get scan results, and include any known config.
			List<ScanResult> resultsList = control.wifiManager.getScanResults();

			if (resultsList != null) {
				NetworkConfiguration connectTo = null;

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

							// auto connect to adhoc networks found in scan
							// results
							if (WifiAdhocControl.isAdhocSupported())
								connectTo = n;
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
							conf.setNetworkInfo(networkInfo);
							connectedNetwork = conf;
							connection = null;
						}
					}
				}

				if (connectTo != null && this.control.canCycle())
					this.connect(connectTo);
			}
			if (connection != null) {
				Log.v(TAG, "I couldn't find a matching scan result");
				Log.v(TAG, "SSID: " + connection.getSSID());
				Log.v(TAG, "BSSID: " + connection.getBSSID());
				Log.v(TAG, "Status: " + connection.getSupplicantState());
			}
		}

		this.scannedNetworks = scannedNetworks;
		if (this.connectedNetwork != null
				&& this.connectedNetwork != connectedNetwork) {
			this.connectedNetwork.setNetworkInfo(null);
		}
		this.connectedNetwork = connectedNetwork;

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
		this.control = new WifiControl(context);
		try {
			ConnectivityManager cm = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			this.networkInfo = cm.getActiveNetworkInfo();
		} catch (Exception e) {
			// assume there might be a security exception
			Log.e(TAG, e.getMessage(), e);
		}
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
				control.startClientMode(null);
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

		@Override
		public String getStatus() {
			return stateString();
		}

		@Override
		public int getBars() {
			return -1;
		}
	}

	public void onStopService() {
		// turn off adhoc if running...
		this.control.turnOffAdhoc();
	}

	public void onWifiNetworkStateChanged(Intent intent) {
		networkInfo = intent
				.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
		String bssid = intent.getStringExtra(WifiManager.EXTRA_BSSID);
		this.getScanResults();

		if (this.changes != null)
			this.changes.onNetworkChange();
	}
}