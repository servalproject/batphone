package org.servalproject.system;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class WifiDirect {
	private static Method enableP2p;
	private static Method disableP2p;
	private static Method initialize;

	static {
		try {
			// lookup methods and fields not defined publicly in the SDK.
			Class<?> cls = WifiDirect.class.getClassLoader().loadClass(
					"android.net.wifi.p2p.WifiP2pManager");
			for (Method method : cls.getDeclaredMethods()) {
				String methodName = method.getName();
				if (methodName.equals("enableP2p")) {
					enableP2p = method;
					System.out.println("WifiP2pManager.enableP2p FOUND");
				} else if (methodName.equals("disableP2p")) {
					disableP2p = method;
					System.out.println("WifiP2pManager.disableP2p FOUND");
				} else if (methodName.equals("initialize")) {
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (enableP2p == null)
			System.out.println("WifiP2pManager.enableP2p NOT FOUND");
		if (disableP2p == null)
			System.out.println("WifiP2pManager.disableP2p NOT FOUND");
	}

	public static WifiDirect getInstance(Context context, WiFiRadio wifiRadio) {

		if (!context.getPackageManager().hasSystemFeature(FEATURE_WIFI_DIRECT)) {
			Log.w("WifiDirect",
					"Feature wifi direct not found. this might not work...");
		}

		Object manager = context.getSystemService(WIFI_P2P_SERVICE);

		if (manager == null) {
			Log.w("WifiDirect", "Wifi Direct service not found.");
			return null;
		}

		return new WifiDirect(context, wifiRadio, manager);
	}

	private Object manager;
	private Object channel;
	private int state = WIFI_P2P_STATE_DISABLED;
	private WiFiRadio wifiRadio;

	static final String WIFI_P2P_STATE_CHANGED_ACTION = "android.net.wifi.p2p.STATE_CHANGED";
	static final String EXTRA_WIFI_STATE = "wifi_p2p_state";
	static final String WIFI_P2P_SERVICE = "wifip2p";
	static final int WIFI_P2P_STATE_ENABLED = 2;
	static final int WIFI_P2P_STATE_DISABLED = 1;
	static final String FEATURE_WIFI_DIRECT = "android.hardware.wifi.direct";

	private WifiDirect(Context context, WiFiRadio wifiRadio,
			Object manager) {
		this.manager = manager;
		this.wifiRadio = wifiRadio;
		try {
			this.channel = initialize.invoke(manager, context, context.getMainLooper(), null);
		} catch (Exception e) {
			Log.e("WifiDirect",e.toString(),e);
		}
		IntentFilter filter = new IntentFilter();
		filter.addAction(WIFI_P2P_STATE_CHANGED_ACTION);
		context.registerReceiver(receiver, filter);
	}

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action
					.equals(WIFI_P2P_STATE_CHANGED_ACTION)) {
				WifiDirect.this.state = intent.getIntExtra(EXTRA_WIFI_STATE, 0);

				// tell Wifiradio of the new mode!
				if (WifiDirect.this.state == WIFI_P2P_STATE_ENABLED)
					wifiRadio.modeChanged(WifiMode.Direct, true);
				else
					wifiRadio.modeChanged(WifiMode.Off, false);
			}
		}
	};

	public void start() throws IOException {
		if (!canControl())
			throw new IllegalStateException();

		try {
			enableP2p.invoke(manager, channel);
		} catch (InvocationTargetException e) {
			IOException x = new IOException(e.getCause().getMessage());
			x.initCause(e.getCause());
			throw x;
		} catch (Exception e) {
			IOException x = new IOException(e.getMessage());
			x.initCause(e);
			throw x;
		}
	}

	public void stop() throws IOException {
		if (!canControl())
			throw new IllegalStateException();

		try {
			disableP2p.invoke(manager, channel);
		} catch (InvocationTargetException e) {
			IOException x = new IOException(e.getCause().getMessage());
			x.initCause(e.getCause());
			throw x;
		} catch (Exception e) {
			IOException x = new IOException(e.getMessage());
			x.initCause(e);
			throw x;
		}
	}

	public boolean isEnabled() {
		return state == WIFI_P2P_STATE_ENABLED;
	}

	public static boolean canControl() {
		return enableP2p != null && disableP2p != null && initialize != null;
	}
}
