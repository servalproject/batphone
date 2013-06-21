/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.system;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.servalproject.ServalBatPhoneApplication;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiApControl {
	private static Method getWifiApState;
	private static Method isWifiApEnabled;
	private static Method setWifiApEnabled;
	private static Method getWifiApConfiguration;

	public static final String WIFI_AP_STATE_CHANGED_ACTION="android.net.wifi.WIFI_AP_STATE_CHANGED";

	public static final int WIFI_AP_STATE_DISABLED = WifiManager.WIFI_STATE_DISABLED;
	public static final int WIFI_AP_STATE_DISABLING = WifiManager.WIFI_STATE_DISABLING;
	public static final int WIFI_AP_STATE_ENABLED = WifiManager.WIFI_STATE_ENABLED;
	public static final int WIFI_AP_STATE_ENABLING = WifiManager.WIFI_STATE_ENABLING;
	public static final int WIFI_AP_STATE_FAILED = WifiManager.WIFI_STATE_UNKNOWN;

	public static final String EXTRA_PREVIOUS_WIFI_AP_STATE = WifiManager.EXTRA_PREVIOUS_WIFI_STATE;
	public static final String EXTRA_WIFI_AP_STATE = WifiManager.EXTRA_WIFI_STATE;

	private final WifiManager mgr;
	private final ServalBatPhoneApplication app;

	static{
		// lookup methods and fields not defined publicly in the SDK.
		Class<?> cls=WifiManager.class;
		for (Method method:cls.getDeclaredMethods()){
			String methodName=method.getName();
			if (methodName.equals("getWifiApState")){
				getWifiApState=method;
			}else if (methodName.equals("isWifiApEnabled")){
				isWifiApEnabled=method;
			}else if (methodName.equals("setWifiApEnabled")){
				setWifiApEnabled=method;
			}else if (methodName.equals("getWifiApConfiguration")){
				getWifiApConfiguration=method;
			}
		}
	}

	public static boolean isApSupported(){
		return (getWifiApState != null && isWifiApEnabled != null
				&& setWifiApEnabled != null && getWifiApConfiguration != null);
	}

	private WifiApControl(WifiManager mgr){
		this.mgr=mgr;
		app = ServalBatPhoneApplication.context;
	}

	public static WifiApControl getApControl(WifiManager mgr){
		if (!isApSupported())
			return null;
		return new WifiApControl(mgr);
	}

	public boolean isWifiApEnabled(){
		try {
			return (Boolean) isWifiApEnabled.invoke(mgr);
		} catch (Exception e) {
			Log.v("WifiApControl", e.toString(), e); // shouldn't happen
			return false;
		}
	}

	public static int fixStateNumber(int state) {
		// Android's internal state constants were changed some time before
		// version 4.0
		if (state >= 10)
			state -= 10;
		return state;
	}

	public int getWifiApState(){
		try {
			return fixStateNumber((Integer) getWifiApState.invoke(mgr));
		} catch (Exception e) {
			Log.v("BatPhone",e.toString(),e); // shouldn't happen
			return -1;
		}
	}

	public WifiConfiguration getWifiApConfiguration(){
		WifiConfiguration config = null;
		try {
			config = (WifiConfiguration) getWifiApConfiguration.invoke(mgr);
		} catch (Exception e) {
			Log.v("BatPhone",e.toString(),e); // shouldn't happen
		}
		if (config == null) {
			// Always return a valid configuration object
			// android may return null if the user has *never* modified anything
			config = new WifiConfiguration();
		}
		return config;
	}

	public boolean setWifiApEnabled(WifiConfiguration config, boolean enabled){
		try {
			return (Boolean) setWifiApEnabled.invoke(mgr, config, enabled);
		} catch (Exception e) {
			Log.v("BatPhone",e.toString(),e); // shouldn't happen
			return false;
		}
	}

	private List<WifiApNetwork> apNetworks = new ArrayList<WifiApNetwork>();
	private WifiConfiguration savedUserProfile;
	public final WifiApNetwork userNetwork = new WifiApNetwork(null) {
		@Override
		public WifiConfiguration getConfig() {
			if (savedUserProfile != null)
				return savedUserProfile;
			return getWifiApConfiguration();
		}
	};
	private WifiApNetwork currentApNetwork;

	private WifiConfiguration readProfile(String name) {
		SharedPreferences prefs = ServalBatPhoneApplication.context
				.getSharedPreferences(name, 0);
		WifiConfiguration newConfig = new WifiConfiguration();

		// android's WifiApConfigStore.java only uses these three fields.
		newConfig.SSID = prefs.getString("ssid", null);
		if (newConfig.SSID == null) {
			Log.e("WifiApControl", "Profile " + name + " has no SSID!");
			return null;
		}
		newConfig.allowedKeyManagement
				.set(prefs.getInt("key_type", KeyMgmt.NONE));
		newConfig.preSharedKey = prefs.getString("key", null);
		return newConfig;
	}

	public static int getKeyType(WifiConfiguration config) {
		// Up to android-17 only values 0-4 are in use.
		// assume that in future, new auth types will have similar low
		// numbers.
		for (int i = 10; i >= 0; i--) {
			if (config.allowedKeyManagement.get(i))
				return i;
		}
		return KeyMgmt.NONE;
	}

	private void saveProfile(String name, WifiConfiguration config) {
		SharedPreferences prefs = app.getSharedPreferences(name,
				0);
		int keyType = getKeyType(config);
		Log.v("WifiApControl", "Saving profile " + config.SSID + ", "
						+ keyType
						+ ", "
						+ (config.preSharedKey == null ? -1
								: config.preSharedKey.length()));
		Editor ed = prefs.edit();
		ed.putString("ssid", config.SSID);
		ed.putInt("key_type", keyType);
		ed.putString("key", config.preSharedKey);

		ed.commit();
	}

	public boolean enableOurProfile(WifiConfiguration config) {
		WifiConfiguration currentConfig = getWifiApConfiguration();
		boolean saveConfig = getMatchingNetwork(currentConfig) == this.userNetwork;
		boolean ret = setWifiApEnabled(config, true);
		if (ret && saveConfig) {
			saveProfile("saved_user_ap", currentConfig);
			savedUserProfile = currentConfig;
		}
		return ret;
	}

	public boolean restoreUserProfile() {
		WifiConfiguration currentConfig = getWifiApConfiguration();
		if (getMatchingNetwork(currentConfig) == this.userNetwork) {
			savedUserProfile = null;
			Log.v("WifiApControl", "User profile already in use");
			return true;
		}

		File saved = new File(app.coretask.DATA_FILE_PATH
				+ "/shared_prefs/saved_user_ap.xml");
		if (!saved.exists()) {
			Log.v("WifiApControl", "No saved profile");
			return false;
		}

		WifiConfiguration userConfig = readProfile("saved_user_ap");
		boolean ret = setWifiApEnabled(userConfig, true);

		if (ret)
			savedUserProfile = null;

		return ret;
	}

	private void readProfiles() {
		File prefFolder = new File(app.coretask.DATA_FILE_PATH
				+ "/shared_prefs");
		File apPrefs[] = prefFolder.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.getName().startsWith("ap_");
			}
		});

		if (apPrefs != null) {
			for (int i = 0; i < apPrefs.length; i++) {
				String name = apPrefs[i].getName();
				if (name.endsWith(".xml"))
					name = name.substring(0, name.indexOf(".xml"));
				WifiConfiguration config = readProfile(name);
				if (config != null)
					apNetworks.add(new WifiApNetwork(config));
			}
		}
		if (apNetworks.isEmpty()) {
			String name = "ap_default";
			SharedPreferences prefs = app.getSharedPreferences(name, 0);
			Editor ed = prefs.edit();
			ed.putString("ssid", "ap.servalproject.org");
			ed.commit();
			apNetworks.add(new WifiApNetwork(readProfile(name)));
		}
		onApStateChanged(this.getWifiApState());
		if (currentApNetwork != userNetwork) {
			File saved = new File(app.coretask.DATA_FILE_PATH
					+ "/shared_prefs/saved_user_ap.xml");
			if (saved.exists()) {
				savedUserProfile = readProfile("saved_user_ap");
			}
		}
	}

	public WifiApNetwork getMatchingNetwork(WifiConfiguration config) {
		if (apNetworks.isEmpty())
			readProfiles();

		for (int i = 0; i < apNetworks.size(); i++) {
			WifiApNetwork n = apNetworks.get(i);
			if (n.config != null && WifiControl.compareAp(n.config, config))
				return n;
		}

		return userNetwork;
	}

	public WifiApNetwork getMatchingNetwork() {
		return getMatchingNetwork(this.getWifiApConfiguration());
	}

	public boolean isOurNetwork() {
		return getMatchingNetwork(this.getWifiApConfiguration()) != userNetwork;
	}

	public Collection<WifiApNetwork> getNetworks() {
		if (apNetworks.isEmpty())
			readProfiles();
		return apNetworks;
	}

	public WifiApNetwork getDefaultNetwork() {
		if (apNetworks.isEmpty())
			readProfiles();
		if (apNetworks.isEmpty())
			return null;
		return apNetworks.get(0);
	}

	public void onApStateChanged(int state) {
		WifiApNetwork network = getMatchingNetwork();
		WifiApNetwork oldNetwork = null;
		synchronized (this) {
			oldNetwork = currentApNetwork;
			currentApNetwork = network;
		}
		boolean dirty = false;

		if (network != null && network.networkState != state) {
			network.setNetworkState(state);
			dirty = true;
		}
		if (oldNetwork != null && oldNetwork != network
				&& oldNetwork.networkState != WIFI_AP_STATE_DISABLED) {
			oldNetwork.setNetworkState(WIFI_AP_STATE_DISABLED);
			dirty = true;
		}

		if (dirty && app.nm != null)
			app.nm.onAdhocStateChanged();
	}

	public NetworkConfiguration getActiveNetwork() {
		WifiApNetwork n = currentApNetwork;
		if (n != null)
			return n;
		return null;
	}
}
