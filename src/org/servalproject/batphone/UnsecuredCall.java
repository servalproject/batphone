package org.servalproject.batphone;

import org.servalproject.R;
import org.servalproject.servald.MDPMonitor;
import org.servalproject.servald.SubscriberId;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class UnsecuredCall extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = this.getIntent();
		SubscriberId sid = new SubscriberId(intent.getStringExtra("sid"));

		String did = intent.getStringExtra("did");
		String name = intent.getStringExtra("name");
		if (sid == null) {
			Log.e("BatPhone", "UnsecuredCall Activity launched without SID");
			finish();
		}
		if (did == null)
			did = "<no number>";
		if (name == null || name.equals(""))
			name = sid.abbreviation();

		setContentView(R.layout.makecall);

		TextView tv_name = (TextView) findViewById(R.id.caller_name);
		TextView tv_number = (TextView) findViewById(R.id.ph_no_display);
		TextView tv_callstatus = (TextView) findViewById(R.id.incoming_label);

		tv_name.setText(name);
		tv_number.setText(did);
		tv_callstatus.setText("Preparing to connect...");

		MDPMonitor mdpMonitor = new MDPMonitor();
		new Thread(mdpMonitor).start();
		mdpMonitor.monitorVomp(true);
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	protected void onRestart() {
		// TODO Auto-generated method stub
		super.onRestart();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

}
