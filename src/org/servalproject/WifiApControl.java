package org.servalproject;

import java.lang.reflect.Method;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiApControl {
	private static Method getWifiApState;
	private static Method isWifiApEnabled;
	private static Method setWifiApEnabled;
	private static Method setWifiApConfiguration;
	private static Method getWifiApConfiguration;
	
	static{
		Class<?> cls=WifiManager.class;
		for (Method method:cls.getDeclaredMethods()){
			String methodName=method.getName();
			if (methodName.equals("getWifiApState")){
				getWifiApState=method;
			}else if (methodName.equals("isWifiApEnabled")){
				isWifiApEnabled=method;
			}else if (methodName.equals("setWifiApEnabled")){
				setWifiApEnabled=method;
			}else if (methodName.equals("setWifiApConfiguration")){
				setWifiApConfiguration=method;
			}else if (methodName.equals("getWifiApConfiguration")){
				getWifiApConfiguration=method;
			}
		}
	}
	
	public static boolean isApSupported(){
		return (getWifiApState!=null && isWifiApEnabled!=null && setWifiApEnabled!=null && setWifiApConfiguration!=null && getWifiApConfiguration!=null);
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
