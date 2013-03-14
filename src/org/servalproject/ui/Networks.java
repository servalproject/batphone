package org.servalproject.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class Networks extends Activity implements OnNetworkChange,
		OnItemClickListener {
	ArrayAdapter<NetworkConfiguration> adapter;
	List<NetworkConfiguration> data = new ArrayList<NetworkConfiguration>();
	ListView listView;
	ServalBatPhoneApplication app;
	NetworkManager nm;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.networks);
		this.listView = (ListView) this.findViewById(R.id.listView);
		this.app = (ServalBatPhoneApplication)this.getApplication();
		this.nm = NetworkManager.getNetworkManager(app);

		listView.setOnItemClickListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		nm.setNetworkChangeListener(this);
		this.onNetworkChange();
	}

	@Override
	public void onNetworkChange() {
		List<NetworkConfiguration> networks = nm.getNetworks();
		data.clear();
		data.addAll(networks);

		if (adapter==null){
			adapter = new ArrayAdapter<NetworkConfiguration>(this,
					android.R.layout.simple_list_item_1, data);
			listView.setAdapter(adapter);
		}else{
			adapter.notifyDataSetChanged();
		}
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
		try {
			nm.connect(config);
		} catch (IOException e) {
			Log.e("Networks", e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		}
	}
}
