package org.servalproject.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.servalproject.Control;
import org.servalproject.PreparationWizard;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.system.CommotionAdhoc;
import org.servalproject.system.NetworkManager;
import org.servalproject.system.NetworkState;
import org.servalproject.system.ScanResults;
import org.servalproject.system.WifiAdhocControl;
import org.servalproject.system.WifiAdhocNetwork;
import org.servalproject.system.WifiApControl;
import org.servalproject.ui.SimpleAdapter.ViewBinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Networks extends Activity implements CompoundButton.OnCheckedChangeListener {
	private SimpleAdapter<NetworkControl> adapter;
	private ListView listView;
	private ServalBatPhoneApplication app;
	private NetworkManager nm;
	private TextView status;
	private CheckBox enabled;
	private static final String TAG="Networks";

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
		switch(compoundButton.getId()){
			case R.id.enabled:
				setEnabled(isChecked);
				break;
		}
	}

	private void setEnabled(boolean isEnabled){
		SharedPreferences.Editor e = app.settings.edit();
		e.putBoolean("meshRunning", isEnabled);
		e.commit();

		Intent serviceIntent = new Intent(this, Control.class);
		if (isEnabled)
			startService(serviceIntent);
		else
			stopService(serviceIntent);
		if (enabled.isChecked()!=isEnabled)
			enabled.setChecked(isEnabled);
	}

	private abstract class NetworkControl implements OnClickListener {
		CheckBox enabled;
		TextView status;
		ImageView icon;
		abstract CharSequence getTitle();
		abstract NetworkState getState();
		abstract void enable();
		abstract void clicked();

		CharSequence getStatus() {
			NetworkState state = getState();
			if (state==null || state == NetworkState.Disabled)
				return null;
			return state.toString(Networks.this);
		}

		void updateStatus(){
			if (status==null)
				return;
			status.setText(getStatus());
		}
		boolean isEnabled(NetworkState state){
			return state != NetworkState.Disabling && state != NetworkState.Enabling;
		}
		boolean isChecked(NetworkState state){
			return state != null && state != NetworkState.Disabled && state != NetworkState.Error;
		}
		void updateEnabled(){
			if (enabled == null)
				return;
			NetworkState s = getState();
			boolean isEnabled = isEnabled(s);
			if (enabled.isEnabled()!=isEnabled)
				enabled.setEnabled(isEnabled);
			boolean isChecked = isChecked(s);
			if (enabled.isChecked()!=isChecked)
				enabled.setChecked(isChecked);
		}

		@Override
		public void onClick(View view){
			switch(view.getId()){
				case R.id.enabled:
				{
					boolean isChecked = enabled.isChecked();
					NetworkState state = getState();
					if (state == NetworkState.Enabled && !isChecked)
						nm.control.off(null);
					else if ((state == null || state==NetworkState.Disabled || state==NetworkState.Error) && isChecked)
						enable();
					this.updateEnabled();
				}
					break;
				default:
					clicked();
			}
		}

		protected void setIcon(Intent i){
			PackageManager packageManager = getPackageManager();
			ResolveInfo r = packageManager.resolveActivity(i, 0);
			if (r!=null) {
				icon.setVisibility(View.VISIBLE);
				icon.setImageDrawable(r.loadIcon(packageManager));
			}else
				icon.setVisibility(View.GONE);
		}
		public abstract void setIcon();
	}

	private NetworkControl WifiClient = new NetworkControl(){
		@Override
		CharSequence getTitle() {
			return getText(R.string.wifi_client);
		}

		@Override
		NetworkState getState() {
			return nm.control.getWifiClientState();
		}

		@Override
		CharSequence getStatus() {
			if (getState() != NetworkState.Enabled)
				return super.getStatus();

			NetworkInfo networkInfo = nm.control.connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

			WifiInfo connection = nm.control.wifiManager.getConnectionInfo();
			if (connection==null)
				return null;

			String ssid = "";
			if (connection.getBSSID()!=null){
				ssid = connection.getSSID();
				if (ssid==null || ssid.equals(""))
					ssid = getString(R.string.ssid_none);
			}

			if (networkInfo!=null){
				if (networkInfo.isConnected())
					return getString(R.string.connected_to, ssid);

				switch(networkInfo.getDetailedState()){
					case DISCONNECTED:
					case SCANNING:
						Collection<ScanResults> results = nm.getScanResults();
						if (results != null) {
							int servalCount = 0;
							int openCount = 0;
							int knownCount = 0;
							int adhocCount = 0;
							for (ScanResults s : results) {
								if (s.isAdhoc())
									adhocCount++;
								if (!s.isSecure())
									openCount++;
								if (s.getConfiguration() != null)
									knownCount++;
							}
							if (knownCount > 0)
								return getResources().getQuantityString(R.plurals.known_networks, knownCount, knownCount);
							if (openCount > 0)
								return getResources().getQuantityString(R.plurals.open_networks, openCount, openCount);
						}
						return super.getStatus();
				}
			}

			if (ssid.equals(""))
				return getText(R.string.connecting);
			return getString(R.string.connecting_to, ssid);
		}

		private Intent getIntentAction(){
			return new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
		}

		@Override
		public void setIcon() {
			setIcon(getIntentAction());
		}

		@Override
		public void enable(){
			setEnabled(true);
			nm.control.startClientMode(null);
		}

		@Override
		public void clicked() {
			startActivity(getIntentAction());
		}
	};

	private NetworkControl HotSpot = new NetworkControl(){
		@Override
		CharSequence getTitle() {
			return getText(R.string.hotspot);
		}

		@Override
		NetworkState getState() {
			return nm.control.wifiApManager.getNetworkState();
		}

		@Override
		CharSequence getStatus() {
			if (getState() != NetworkState.Enabled)
				return super.getStatus();
			WifiConfiguration config = nm.control.wifiApManager.getWifiApConfiguration();
			if (config==null || config.SSID==null || config.SSID.equals("")) {
				// Looks like this handset is hiding hotspot config, we probably can't set it either.
				return super.getStatus();
			}
			return config.SSID;
		}

		private Intent getIntentAction(){
			PackageManager packageManager = getPackageManager();

			Intent i = new Intent();
			i.setClassName("com.android.settings", "com.android.settings.wifi.WifiApSettings");
			ResolveInfo r = packageManager.resolveActivity(i, 0);
			if (r!=null){
				i.setClassName(r.activityInfo.packageName, r.activityInfo.name);
				return i;
			}
			i.setClassName("com.htc.WifiRouter", "com.htc.WifiRouter.WifiRouter");
			r = packageManager.resolveActivity(i, 0);
			if (r!=null){
				i.setClassName(r.activityInfo.packageName, r.activityInfo.name);
				return i;
			}
			return null;
		}

		@Override
		public void setIcon() {
			setIcon(getIntentAction());
		}

		@Override
		public void enable(){
			new AlertDialog.Builder(Networks.this)
					.setTitle(
							getText(R.string.openhotspottitle)
					)
					.setMessage(
							getText(R.string.openhotspotmessage)
					)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(
							getText(R.string.connectbutton),
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
													int button) {
									setEnabled(true);
									nm.control.connectAp(false, null);
								}
							}
					)
					.show();
		}

		@Override
		public void clicked() {
			Intent i = getIntentAction();
			if (i!=null)
				startActivity(i);
		}
	};

	private NetworkControl Adhoc = new NetworkControl() {

		@Override
		CharSequence getTitle() {
			return getText(R.string.adhoc);
		}

		@Override
		NetworkState getState() {
			return nm.control.adhocControl.getState();
		}

		private boolean testDialog(){
			if (WifiAdhocControl.isAdhocSupported())
				return true;

			new AlertDialog.Builder(Networks.this)
					.setTitle(
							getText(R.string.adhoctesttitle))
					.setIcon(R.drawable.ic_dragon)
					.setMessage(
							getText(R.string.adhoctestmessage))
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(
							getText(R.string.testbutton),
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
													int button) {
									Intent intent = new Intent(
											Networks.this,
											PreparationWizard.class);
									startActivity(intent);
								}
							})
					.show();
			return false;
		}

		@Override
		public void enable(){
			WifiAdhocNetwork network = nm.control.adhocControl.getDefaultNetwork();
			setEnabled(true);
			nm.control.connectAdhoc(network, null);
		}

		@Override
		public boolean isEnabled(NetworkState state){
			if (!WifiAdhocControl.isAdhocSupported())
				return false;
			return super.isEnabled(state);
		}

		@Override
		public void setIcon() {
			icon.setVisibility(View.GONE);
		}

		@Override
		public void clicked() {
			if (testDialog()) {
				WifiAdhocNetwork network = nm.control.adhocControl.getDefaultNetwork();
				Intent intent = new Intent(
						Networks.this,
						AdhocPreferences.class);
				intent.putExtra(
						AdhocPreferences.EXTRA_PROFILE_NAME,
						network.preferenceName);
				startActivity(intent);
			}
		}
	};

	private NetworkControl Commotion = new NetworkControl() {
		@Override
		CharSequence getTitle() {
			return CommotionAdhoc.appName;
		}

		@Override
		NetworkState getState() {
			return nm.control.commotionAdhoc.getState();
		}

		@Override
		void enable() {
			setEnabled(true);
			nm.control.connectMeshTether(null);
		}

		@Override
		void clicked() {
			Intent i = getIntentAction();
			if (i!=null)
				startActivity(i);
		}

		private Intent getIntentAction(){
			Intent i = new Intent(Intent.ACTION_MAIN);
			i.addCategory(Intent.CATEGORY_LAUNCHER);
			i.setPackage(CommotionAdhoc.PACKAGE_NAME);
			PackageManager packageManager = getPackageManager();
			ResolveInfo r = packageManager.resolveActivity(i, 0);
			if (r.activityInfo != null) {
				i.setClassName(r.activityInfo.packageName, r.activityInfo.name);
				return i;
			}
			return null;
		}

		@Override
		public void setIcon() {
			setIcon(getIntentAction());
		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.networks);
		this.listView = (ListView) this.findViewById(R.id.listView);
		this.status = (TextView) this.findViewById(R.id.serval_status);
		this.enabled = (CheckBox) this.findViewById(R.id.enabled);

		this.app = (ServalBatPhoneApplication)this.getApplication();
		this.nm = NetworkManager.getNetworkManager(app);
		adapter = new SimpleAdapter<NetworkControl>(this, binder);
		List<NetworkControl> data = new ArrayList<NetworkControl>();
		data.add(this.WifiClient);
		if (nm.control.wifiApManager != null)
			data.add(this.HotSpot);
		data.add(this.Adhoc);
		if (CommotionAdhoc.isInstalled())
			data.add(this.Commotion);
		adapter.setItems(data);
		listView.setAdapter(adapter);
	}

	private void statusChanged(String status) {
		this.status.setText(status);
	}

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(ServalBatPhoneApplication.ACTION_STATUS)) {
				statusChanged(intent
						.getStringExtra(ServalBatPhoneApplication.EXTRA_STATUS));
			}else{
				adapter.notifyDataSetChanged();
			}
		}

	};

	@Override
	protected void onResume() {
		super.onResume();
		adapter.notifyDataSetChanged();

		IntentFilter filter = new IntentFilter();
		filter.addAction(ServalBatPhoneApplication.ACTION_STATUS);
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		filter.addAction(WifiAdhocControl.ADHOC_STATE_CHANGED_ACTION);
		if (nm.control.wifiApManager!=null)
			filter.addAction(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION);
		this.registerReceiver(receiver, filter);

		this.enabled.setChecked(app.settings.getBoolean("meshRunning", false));
		this.enabled.setOnCheckedChangeListener(this);
		statusChanged(app.getStatus());
	}

	@Override
	protected void onPause() {
		super.onPause();
		this.unregisterReceiver(receiver);
	}

	private ViewBinder<NetworkControl> binder = new ViewBinder<NetworkControl>() {
		@Override
		public long getId(int position, NetworkControl t) {
			return position;
		}

		@Override
		public int[] getResourceIds() {
			return new int[]{R.layout.network};
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public boolean isEnabled(NetworkControl networkControl) {
			return true;
		}

		@Override
		public int getViewType(int position, NetworkControl t) {
			return 0;
		}

		@Override
		public void bindView(int position, NetworkControl t, View view) {
			TextView title = (TextView) view.findViewById(R.id.title);
			t.icon = (ImageView) view.findViewById(R.id.icon);
			t.status = (TextView) view.findViewById(R.id.status);
			t.enabled = (CheckBox) view.findViewById(R.id.enabled);

			title.setText(t.getTitle());
			t.enabled.setTag(t);
			t.enabled.setOnClickListener(t);
			view.setOnClickListener(t);
			t.updateStatus();
			t.updateEnabled();
			t.setIcon();
		}
	};
}
