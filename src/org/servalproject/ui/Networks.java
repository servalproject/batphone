package org.servalproject.ui;

import java.io.File;
import java.util.List;

import org.servalproject.PreparationWizard;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.system.NetworkConfiguration;
import org.servalproject.system.NetworkManager;
import org.servalproject.system.NetworkManager.OnNetworkChange;
import org.servalproject.system.WifiAdhocNetwork;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

public class Networks extends Activity implements OnNetworkChange,
		OnItemClickListener {
	ArrayAdapter<NetworkConfiguration> adapter;
	ListView listView;
	Button scan;
	ServalBatPhoneApplication app;
	NetworkManager nm;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.networks);
		this.listView = (ListView) this.findViewById(R.id.listView);
		this.scan = (Button) this.findViewById(R.id.scan);
		this.app = (ServalBatPhoneApplication)this.getApplication();
		this.nm = NetworkManager.getNetworkManager(app);

		listView.setOnItemClickListener(this);
		scan.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				nm.startScan();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		nm.setNetworkChangeListener(this);
		this.onNetworkChange();
		nm.startScan();
	}

	@Override
	public void onNetworkChange() {
		List<NetworkConfiguration> networks = nm.getNetworks();
		adapter = new ArrayAdapter<NetworkConfiguration>(this,
				android.R.layout.simple_list_item_1, networks);
		listView.setAdapter(adapter);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		NetworkConfiguration config = adapter.getItem(position);
		if (config instanceof WifiAdhocNetwork) {
			if (!nm.doesAdhocWork()) {
				// Clear out old attempt_ files
				File varDir = new File("/data/data/org.servalproject/var/");
				if (varDir.isDirectory())
					for (File f : varDir.listFiles()) {
						if (!f.getName().startsWith("attempt_"))
							continue;
						f.delete();
					}
				// Re-run wizard
				Intent prepintent = new Intent(this, PreparationWizard.class);
				prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(prepintent);
				return;
			}
		}
		nm.connect(config);
	}
}
