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

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;

import java.lang.reflect.Method;

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
	private WifiConfiguration savedUserProfile;
	private final WifiConfiguration servalConfiguration;

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

		servalConfiguration = new WifiConfiguration();
		// TODO ssid from locale specific strings
		servalConfiguration.SSID = "ap.servalproject.org";
		servalConfiguration.allowedKeyManagement.set(KeyMgmt.NONE);

		WifiConfiguration config = readProfile("saved_user_ap");
		if (isUserConfig(config))
			this.savedUserProfile = config;
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

	public static NetworkState getNetworkState(int state){
		switch(state){
			case WIFI_AP_STATE_DISABLED:
				return NetworkState.Disabled;
			case WIFI_AP_STATE_ENABLED:
				return NetworkState.Enabled;
			case WIFI_AP_STATE_ENABLING:
				return NetworkState.Enabling;
			case WIFI_AP_STATE_DISABLING:
				return NetworkState.Disabling;
			case WIFI_AP_STATE_FAILED:
				return NetworkState.Error;
		}
		return null;
	}

	public NetworkState getNetworkState(){
		return getNetworkState(getWifiApState());
	}

	public WifiConfiguration getWifiApConfiguration(){
		WifiConfiguration config = null;
		try {
			config = (WifiConfiguration) getWifiApConfiguration.invoke(mgr);
		} catch (Exception e) {
			Log.v("BatPhone",e.toString(),e); // shouldn't happen
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

	public boolean isUserConfig(WifiConfiguration config){
		if (config == null || config.SSID == null)
			return false;
		if (getKeyType(config)!=KeyMgmt.NONE)
			return true;
		if (config.SSID.equals(servalConfiguration.SSID))
			return false;
		return !config.SSID.toLowerCase().contains("serval");
	}

	public boolean isUserConfig(){
		WifiConfiguration config = getWifiApConfiguration();
		boolean ret = isUserConfig(config);
		if (ret){
			saveProfile("saved_user_ap", config);
			savedUserProfile = config;
		}
		return ret;
	}

	public WifiConfiguration getServalConfig(){
		return servalConfiguration;
	}

	public boolean enable(boolean withUserConfig){
		WifiConfiguration config = getWifiApConfiguration();
		boolean hasUserConfig = isUserConfig(config);

		if (withUserConfig){
			if (hasUserConfig)
				config = null;
			else
				config = savedUserProfile;
		}else{
			if (hasUserConfig){
				saveProfile("saved_user_ap", config);
				savedUserProfile = config;
			}
			config = getServalConfig();
		}
		return setWifiApEnabled(config, true);
	}

	public boolean shouldRestoreConfig(){
		return ((!isUserConfig()) && savedUserProfile!=null);
	}
	public boolean restoreUserConfig(){
		if (shouldRestoreConfig()){
			return setWifiApEnabled(savedUserProfile, true);
		}
		return false;
	}

	public boolean disable(){
		return setWifiApEnabled(null, false);
	}

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
}
