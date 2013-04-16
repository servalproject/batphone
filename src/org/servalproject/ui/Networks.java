package org.servalproject.ui;

import java.util.ArrayList;
import java.util.List;

import org.servalproject.Control;
import org.servalproject.PreparationWizard;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.system.NetworkConfiguration;
import org.servalproject.system.NetworkManager;
import org.servalproject.system.NetworkManager.OnNetworkChange;
import org.servalproject.system.WifiAdhocControl;
import org.servalproject.system.WifiAdhocNetwork;
import org.servalproject.system.WifiApControl;
import org.servalproject.system.WifiApNetwork;
import org.servalproject.ui.SimpleAdapter.ViewBinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class Networks extends Activity implements OnNetworkChange,
		OnItemClickListener, OnClickListener {
	private SimpleAdapter<NetworkConfiguration> adapter;
	private List<NetworkConfiguration> data = new ArrayList<NetworkConfiguration>();
	private ListView listView;
	private ServalBatPhoneApplication app;
	private NetworkManager nm;
	private CheckBox enabled;
	private CheckBox autoCycle;
	private TextView status;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.networks);
		this.listView = (ListView) this.findViewById(R.id.listView);
		this.enabled = (CheckBox) this.findViewById(R.id.enabled);
		this.autoCycle = (CheckBox) this.findViewById(R.id.auto_cycle);
		this.status = (TextView) this.findViewById(R.id.serval_status);

		this.app = (ServalBatPhoneApplication)this.getApplication();
		state = app.getState();
		this.nm = NetworkManager.getNetworkManager(app);

		listView.setOnItemClickListener(this);
		enabled.setOnClickListener(this);
		autoCycle.setOnClickListener(this);
	}

	private State state;

	private void stateChanged() {
		enabled.setEnabled(state == State.On || state == State.Off);
		enabled.setChecked(state == State.On);
	}

	private void statusChanged(String status) {
		this.status.setText(status);
	}

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(ServalBatPhoneApplication.ACTION_STATE)) {
				int stateOrd = intent.getIntExtra(
						ServalBatPhoneApplication.EXTRA_STATE, 0);
				state = State.values()[stateOrd];
				stateChanged();
			} else if (action.equals(ServalBatPhoneApplication.ACTION_STATUS)) {
				statusChanged(intent
						.getStringExtra(ServalBatPhoneApplication.EXTRA_STATUS));
			}
		}

	};

	@Override
	protected void onResume() {
		super.onResume();
		nm.setNetworkChangeListener(this);
		this.onNetworkChange();

		IntentFilter filter = new IntentFilter();
		filter.addAction(ServalBatPhoneApplication.ACTION_STATE);
		filter.addAction(ServalBatPhoneApplication.ACTION_STATUS);
		this.registerReceiver(receiver, filter);
		state = app.getState();
		stateChanged();
		statusChanged(app.getStatus());

		this.autoCycle.setChecked(nm.control.isAutoCycling());
	}

	@Override
	protected void onPause() {
		super.onPause();
		this.unregisterReceiver(receiver);
	}

	private ViewBinder<NetworkConfiguration> binder = new ViewBinder<NetworkConfiguration>() {
		@Override
		public long getId(NetworkConfiguration t) {
			return -1;
		}

		@Override
		public int getViewType(NetworkConfiguration t) {
			return 0;
		}

		@Override
		public void bindView(NetworkConfiguration t, View view) {
			TextView ssid = (TextView) view.findViewById(R.id.ssid);
			ssid.setText(t.getSSID());
			TextView status = (TextView) view.findViewById(R.id.status);
			String statusText = t.getStatus();
			status.setText(statusText);
			status.setVisibility(statusText == null ? View.GONE : View.VISIBLE);
			ImageView strength = (ImageView) view.findViewById(R.id.bars);
			if (t.getBars() < 0)
				strength.setVisibility(View.INVISIBLE);
			else
				strength.setVisibility(View.VISIBLE);

		}
	};

	@Override
	public void onNetworkChange() {
		if (!app.isMainThread()) {
			this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					onNetworkChange();
				}
			});
			return;
		}

		List<NetworkConfiguration> networks = nm.getNetworks();
		data.clear();
		data.addAll(networks);

		if (adapter==null){
			adapter = new SimpleAdapter<NetworkConfiguration>(this,
					R.layout.network, binder);
			adapter.setItems(data);
			listView.setAdapter(adapter);
		}else{
			adapter.setItems(data);
		}
	}

	private String getNetworkString(int resource, NetworkConfiguration config) {
		return this.getString(resource, config.getSSID());
	}

	private void testAdhocDialog(final NetworkConfiguration config) {
		new AlertDialog.Builder(this)
				.setTitle(
						getNetworkString(R.string.adhoctesttitle,
								config))
				.setMessage(
						getNetworkString(R.string.adhoctestmessage,
								config))
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(
						getNetworkString(R.string.testbutton, config),
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
	}

	private void connectAdhocDialog(final WifiAdhocNetwork network) {
		new AlertDialog.Builder(this)
				.setTitle(
						getNetworkString(R.string.adhocconnecttitle,
								network))
				.setMessage(network.getDetails(this))
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(
						getNetworkString(R.string.connectbutton, network),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int button) {
								connect(network);
							}
						})
				.setNeutralButton(
						getNetworkString(R.string.settingsbutton,
								network),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int button) {
								Intent intent = new Intent(
										Networks.this,
										AdhocPreferences.class);
								intent.putExtra(
										AdhocPreferences.EXTRA_PROFILE_NAME,
										network.preferenceName);
								startActivity(intent);
							}
						})
				.show();

	}

	private void openAccessPointDialog(final WifiApNetwork network) {
		new AlertDialog.Builder(this)
				.setTitle(
						getNetworkString(R.string.openhotspottitle,
								network))
				.setMessage(
						getNetworkString(R.string.openhotspotmessage,
								network))
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(
						getNetworkString(R.string.connectbutton, network),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int button) {
								connect(network);
							}
						})
				.show();
	}

	private boolean warnIfNotRunning() {
		if (state != State.On) {
			app.displayToastMessage("You must turn on Serval first");
			return true;
		}
		return false;
	}

	private void connect(NetworkConfiguration config) {
		if (warnIfNotRunning())
			return;

		nm.connect(config);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {

		NetworkConfiguration config = adapter.getItem(position);

		if (config instanceof WifiAdhocNetwork) {
			if (!WifiAdhocControl.isAdhocSupported()) {
				testAdhocDialog(config);
			} else {
				connectAdhocDialog((WifiAdhocNetwork) config);
			}
			return;
		} else if (config instanceof WifiApNetwork) {
			if (warnIfNotRunning())
				return;

			WifiApNetwork network = (WifiApNetwork) config;
			if (WifiApControl.getKeyType(network.getConfig()) == KeyMgmt.NONE) {
				openAccessPointDialog(network);
				return;
			}
		}
		connect(config);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.enabled:
			// toggle enabled
			Intent serviceIntent = new Intent(Networks.this, Control.class);

			Editor ed = app.settings.edit();
			switch (app.getState()) {
			case Off:
				startService(serviceIntent);
				ed.putBoolean("meshRunning", true);
				break;
			case On:
				this.stopService(serviceIntent);
				ed.putBoolean("meshRunning", false);
				break;
			}
			ed.commit();
			break;

		case R.id.auto_cycle:
			// toggle cycling
			if (!nm.control.autoCycle(!nm.control.isAutoCycling())) {
				// TODO toast?
			}
			autoCycle.setChecked(nm.control.isAutoCycling());
			break;
		}
	}
}
