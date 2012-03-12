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

import java.lang.reflect.Method;

import android.net.wifi.WifiConfiguration;
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
		return (getWifiApState!=null && isWifiApEnabled!=null && setWifiApEnabled!=null && getWifiApConfiguration!=null);
	}

	private WifiManager mgr;
	private WifiApControl(WifiManager mgr){
		this.mgr=mgr;
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
			Log.v("BatPhone",e.toString(),e); // shouldn't happen
			return false;
		}
	}

	public int getWifiApState(){
		try {
			return (Integer) getWifiApState.invoke(mgr);
		} catch (Exception e) {
			Log.v("BatPhone",e.toString(),e); // shouldn't happen
			return -1;
		}
	}

	public WifiConfiguration getWifiApConfiguration(){
		try {
			return (WifiConfiguration) getWifiApConfiguration.invoke(mgr);
		} catch (Exception e) {
			Log.v("BatPhone",e.toString(),e); // shouldn't happen
			return null;
		}
	}

	public boolean setWifiApEnabled(WifiConfiguration config, boolean enabled){
		try {
			return (Boolean) setWifiApEnabled.invoke(mgr, config, enabled);
		} catch (Exception e) {
			Log.v("BatPhone",e.toString(),e); // shouldn't happen
			return false;
		}
	}
}
