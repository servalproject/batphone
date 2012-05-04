package org.servalproject.batphone;

import org.servalproject.R;
import org.servalproject.servald.Identities;
import org.servalproject.servald.ServalDMonitor;
import org.servalproject.servald.SubscriberId;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

public class UnsecuredCall extends Activity {

	// setup basic call state tracking data
	SubscriberId remote_sid = null;
	String remote_did = null;
	String remote_name = null;
	int local_id = 0;
	int remote_id = 0;
	int local_state = 0;
	int remote_state = 0;
	TextView tv_name = null;
	TextView tv_number = null;
	TextView tv_callstatus = null;

	final Handler mHandler = new Handler();

	private int layout = 0;

	// Create runnable for posting
	final Runnable updateCallStatus = new Runnable() {
		@Override
		public void run() {
			switch(local_state) {
			case VoMP.STATE_CALLPREP: case VoMP.STATE_NOCALL:
			case VoMP.STATE_RINGINGOUT:
				if (layout!=R.layout.makecall) {
					setContentView(R.layout.makecall);
					tv_name = (TextView) findViewById(R.id.caller_name);
					tv_number = (TextView) findViewById(R.id.ph_no_display);
					tv_callstatus = (TextView) findViewById(R.id.incoming_label);

					tv_name.setText(remote_name);
					tv_number.setText(remote_did);
				}
				updateCallStatusMessage("Calling (" + local_state + "."
						+ remote_state + ")...");
				break;
			case VoMP.STATE_RINGINGIN:
				if (layout!=R.layout.incomingcall) {
					setContentView(R.layout.makecall);
					tv_name = (TextView) findViewById(R.id.caller_name);
					tv_number = (TextView) findViewById(R.id.ph_no_display);
					tv_callstatus = (TextView) findViewById(R.id.incoming_label);

					tv_name.setText(remote_name);
					tv_number.setText(remote_did);
				}
				updateCallStatusMessage("In-bound call (" + local_state + "."
						+ remote_state + ")...");
				break;
			case VoMP.STATE_INCALL:
				if (layout!=R.layout.incall) {
					setContentView(R.layout.makecall);
					tv_name = (TextView) findViewById(R.id.caller_name);
					tv_number = (TextView) findViewById(R.id.ph_no_display);
					tv_callstatus = (TextView) findViewById(R.id.incoming_label);

					tv_name.setText(remote_name);
					tv_number.setText(remote_did);
				}
				updateCallStatusMessage("In call (" + local_state + "."
						+ remote_state + ")...");
				break;
			case VoMP.STATE_CALLENDED:
				if (layout!=R.layout.endedcall) {
					setContentView(R.layout.makecall);
					tv_name = (TextView) findViewById(R.id.caller_name);
					tv_number = (TextView) findViewById(R.id.ph_no_display);
					tv_callstatus = (TextView) findViewById(R.id.incoming_label);

					tv_name.setText(remote_name);
					tv_number.setText(remote_did);
				}
				updateCallStatusMessage("Call ended (" + local_state + "."
						+ remote_state + ")...");
				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = this.getIntent();
		remote_sid = new SubscriberId(intent.getStringExtra("sid"));

		remote_did = intent.getStringExtra("did");
		remote_name = intent.getStringExtra("name");
		if (remote_did == null)
			remote_did = "<no number>";
		if (remote_name == null || remote_name.equals(""))
			remote_name = remote_sid.abbreviation();

		setContentView(R.layout.makecall);
		layout = R.layout.makecall;

		tv_name = (TextView) findViewById(R.id.caller_name);
		tv_number = (TextView) findViewById(R.id.ph_no_display);
		tv_callstatus = (TextView) findViewById(R.id.incoming_label);

		tv_name.setText(remote_name);
		tv_number.setText(remote_did);
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
					mHandler.post(updateCallStatus);
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
		servaldMonitor.sendMessage("call " + remote_sid + " "
				+ Identities.getCurrentDid() + " " + remote_did);

	}

	protected void updateCallStatusMessage(String string) {
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
