package org.servalproject.system;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;

//Note that we have to rely on the user to change modes to wifi-direct

public class WifiDirect {
	private static Method enableP2p;
	private static Method disableP2p;

	static {
		try {
			// lookup methods and fields not defined publicly in the SDK.
			Class<?> cls = WifiP2pManager.class;
			for (Method method : cls.getDeclaredMethods()) {
				String methodName = method.getName();
				if (methodName.equals("enableP2p")) {
					enableP2p = method;
					System.out.println("WifiP2pManager.enableP2p FOUND");
				} else if (methodName.equals("disableP2p")) {
					disableP2p = method;
					System.out.println("WifiP2pManager.disableP2p FOUND");
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
		if (!context.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_WIFI_DIRECT)) {
			System.out.println("FEATURE_WIFI_DIRECT NOT FOUND");
			return null;
		}

		WifiP2pManager manager = (WifiP2pManager) context
				.getSystemService(Context.WIFI_P2P_SERVICE);
		if (manager == null) {
			System.out.println("WifiP2pManager was NULL???");
			return null;
		}

		return new WifiDirect(context, wifiRadio, manager);
	}

	private WifiP2pManager manager;
	private int state = WifiP2pManager.WIFI_P2P_STATE_DISABLED;
	private Channel channel;
	private WiFiRadio wifiRadio;

	private WifiDirect(Context context, WiFiRadio wifiRadio,
			WifiP2pManager manager) {
		this.manager = manager;
		this.wifiRadio = wifiRadio;

		channel = manager
				.initialize(context, context.getMainLooper(), listener);

		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
		context.registerReceiver(receiver, filter);
	}

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action
					.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {

			} else if (action
					.equals(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)) {

			} else if (action
					.equals(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)) {
				WifiDirect.this.state = intent.getIntExtra(
						WifiP2pManager.EXTRA_WIFI_STATE, 0);

				if (WifiDirect.this.state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
					wifiRadio.modeChanged(WifiMode.Direct, true);
				else
					wifiRadio.modeChanged(WifiMode.Off, false);
				// TODO tell Wifiradio of the new mode!
			} else if (action
					.equals(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)) {

			}
		}
	};

	ChannelListener listener = new ChannelListener() {
		@Override
		public void onChannelDisconnected() {
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
		return state == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
	}

	public static boolean canControl() {
		return enableP2p != null && disableP2p != null;
	}
}
