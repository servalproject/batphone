package org.servalproject.system;

import java.util.Stack;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

public class WifiControl {
	public final WifiManager wifiManager;
	public final WifiApControl wifiApManager;

	private final Handler handler;
	private final HandlerThread handlerThread;

	WifiControl(Context context) {
		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		wifiApManager = WifiApControl.getApControl(wifiManager);

		// Are we recovering from a crash / reinstall?
		handlerThread = new HandlerThread("WifiControl");
		handlerThread.start();
		handler = new Handler(handlerThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				transition();
				super.handleMessage(msg);
			}
		};
	}

	private Stack<State> currentState;
	Stack<State> destState;

	abstract class State {
		void enter() {

		}

		void exit() {

		}
	}

	WifiClient wifiClient = new WifiClient();
	class WifiClient extends State {
		@Override
		void enter() {
			wifiManager.setWifiEnabled(true);
			// TODO wait until wifi is actually enabled
		}

		@Override
		void exit() {
			wifiManager.setWifiEnabled(false);
			// TODO wait until wifi is actually disabled
		}
	}

	class WifiClientProfile extends State {
		final WifiClientNetwork network;

		public WifiClientProfile(WifiClientNetwork network) {
			this.network = network;
		}

		@Override
		void enter() {
		}
	}

	class HotSpot extends State {
		final WifiConfiguration config;

		HotSpot(WifiConfiguration config) {
			this.config = config;
		}

		@Override
		void enter() {
			wifiApManager.setWifiApEnabled(config, true);
		}

		@Override
		void exit() {
			wifiApManager.setWifiApEnabled(null, false);
		}

		// TODO override hashcode & equals based on the wifi configuration?
	}

	AdhocModule adhocModule;
	class AdhocModule extends State {

	}

	class IwConfig extends State {

	}

	class IpConfig extends State {

	}

	private void transition() {
		while(destState!=null){
			Stack<State> dest = destState;
			destState=null;

			int common = 0;

			for (int i = 0; i < dest.size() && i < currentState.size(); i++) {
				if (dest.get(i).equals(currentState.get(i)))
					common = i;
			}

			while (currentState.size() > common) {
				State active = currentState.peek();
				active.exit();
				currentState.pop();
			}

			for (int i = common + 1; i < dest.size(); i++) {
				State active = dest.get(i);
				currentState.push(active);
				active.enter();
			}
		}
	}

	public enum WifiState {
		Off,
		ClientEnabling,
		ClientEnabled,
		ClientDisabling,
		ApEnabling,
		ApEnabled,
		ApDisabling,
		AdhocEnabling,
		AdhocEnabled,
		AdhocDisabling,
		Unknown,
	}

	public WifiState getState() {
		int state = wifiManager.getWifiState();
		switch (state) {
		case WifiManager.WIFI_STATE_ENABLED:
			return WifiState.ClientEnabled;
		case WifiManager.WIFI_STATE_ENABLING:
			return WifiState.ClientEnabling;
		case WifiManager.WIFI_STATE_DISABLING:
			return WifiState.ClientDisabling;
		}

		if (wifiApManager!=null){
			state = wifiApManager.getWifiApState();
			switch (state) {
			case WifiApControl.WIFI_AP_STATE_ENABLED:
				return WifiState.ApEnabled;
			case WifiApControl.WIFI_AP_STATE_ENABLING:
				return WifiState.ApEnabling;
			case WifiApControl.WIFI_AP_STATE_DISABLING:
				return WifiState.ApDisabling;
			}
		}

		return WifiState.Off;
	}

	public void connect(WifiAdhocNetwork network) {
		Stack<State> dest = new Stack<State>();
		if (adhocModule != null)
			dest.push(adhocModule);
		dest.push(new IwConfig());
		dest.push(new IpConfig());
		this.destState = dest;
		handler.sendEmptyMessage(0);
	}

	public void connect(WifiApNetwork network) {
		Stack<State> dest = new Stack<State>();
		dest.push(new HotSpot(network.config));
		this.destState = dest;
		handler.sendEmptyMessage(0);
	}

	public void connect(WifiClientNetwork network) {
		Stack<State> dest = new Stack<State>();
		dest.push(wifiClient);
		dest.push(new WifiClientProfile(network));
		this.destState = dest;
		handler.sendEmptyMessage(0);
	}

	public void off() {
		Stack<State> dest = new Stack<State>();
		this.destState = dest;
		handler.sendEmptyMessage(0);
	}
}
