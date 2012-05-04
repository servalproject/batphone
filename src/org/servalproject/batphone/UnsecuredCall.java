package org.servalproject.batphone;

import org.servalproject.R;
import org.servalproject.servald.Identities;
import org.servalproject.servald.ServalDMonitor;
import org.servalproject.servald.SubscriberId;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class UnsecuredCall extends Activity {

	// setup basic call state tracking data
	int local_id = 0;
	int remote_id = 0;
	int local_state = 0;
	int remote_state = 0;
	TextView tv_name = null;
	TextView tv_number = null;
	TextView tv_callstatus = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = this.getIntent();
		SubscriberId sid = new SubscriberId(intent.getStringExtra("sid"));

		String did = intent.getStringExtra("did");
		String name = intent.getStringExtra("name");
		if (did == null)
			did = "<no number>";
		if (name == null || name.equals(""))
			name = sid.abbreviation();

		setContentView(R.layout.makecall);

		tv_name = (TextView) findViewById(R.id.caller_name);
		tv_number = (TextView) findViewById(R.id.ph_no_display);
		tv_callstatus = (TextView) findViewById(R.id.incoming_label);

		tv_name.setText(name);
		tv_number.setText(did);
		tv_callstatus.setText("Preparing...");

		// Mark call as being setup
		local_id = 0;
		remote_id = 0;
		local_state = 0;
		remote_state = 0;

		ServalDMonitor servaldMonitor = new ServalDMonitor() {
			@Override
			protected void notifyCallStatus(int l_id, int r_id,
					int l_state,
					int r_state) {
				boolean update = false;
				if (r_id == 0 && local_id == 0) {
					// Keep an eye out for the call being created at our end ...
					local_id = l_id;
					remote_id = 0;
					local_state = l_state;
					remote_state = r_state;
					update = true;
				}
				else if (r_id != 0 && local_id == l_id && remote_id != 0) {
					// ... and at the other end ...
					remote_id = r_id;
					local_state = l_state;
					remote_state = r_state;
					update = true;
				}
				else if (l_id == local_id && r_id == remote_id
						&& remote_id != 0) {
					// ... and the resulting call then changing state
					local_state = l_state;
					remote_state = r_state;
					update = true;
				}
				if (update) {
					if (local_state < VoMP.STATE_INCALL)
						updateCallStatusMessage("Calling (" + local_state + "."
								+ remote_state + ")...");

				}
			}
		};
		new Thread(servaldMonitor).start();
		while (servaldMonitor.ready() == false) {
			try {
				Thread.sleep(100);
			} catch (Exception e) {
				// sleep until servald monitor is ready
			}
		}
		tv_callstatus.setText("Preparing to connect...");

		servaldMonitor.monitorVomp(true);
		// Establish call
		servaldMonitor.sendMessage("call " + sid + " "
				+ Identities.getCurrentDid() + " " + did);

	}

	protected void updateCallStatusMessage(String string) {
		// TODO Auto-generated method stub
		if (tv_callstatus != null)
			tv_callstatus.setText(string);
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
