package org.servalproject.batphone;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.Identities;
import org.servalproject.servald.SubscriberId;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
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

	private TextView remote_name_1;
	private TextView remote_number_1;
	private TextView callstatus_1;
	private TextView action_1;
	private TextView remote_name_2;
	private TextView remote_number_2;
	private TextView callstatus_2;
	private TextView action_2;

	final Handler mHandler = new Handler();

	// Create runnable for posting
	final Runnable updateCallStatus = new Runnable() {
		@Override
		public void run() {
			updateUI();
		}
	};
	private Button endButton;
	private Button incomingEndButton;
	private Button incomingAnswerButton;

	private void updateUI() {
		Log.d("ServalDMonitor", "Updating UI for state " + local_state
					+ "." + remote_state);
		switch (local_state) {
			case VoMP.STATE_CALLPREP: case VoMP.STATE_NOCALL:
			case VoMP.STATE_RINGINGOUT:
				showSubLayout(VoMP.STATE_RINGINGOUT);

				remote_name_1.setText(remote_name);
				remote_number_1.setText(remote_did);

				callstatus_1.setText("Calling (" + local_state + "."
						+ remote_state + ")...");
				break;
			case VoMP.STATE_RINGINGIN:
				showSubLayout(VoMP.STATE_RINGINGIN);

				remote_name_2.setText(remote_name);
				remote_name_2.setText(remote_did);

				callstatus_2.setText("In-bound call (" + local_state + "."
						+ remote_state + ")...");
				break;
			case VoMP.STATE_INCALL:
				showSubLayout(VoMP.STATE_INCALL);
				remote_name_1.setText(remote_name);
				remote_number_1.setText(remote_did);
				callstatus_1.setText("In call (" + local_state + "."
						+ remote_state + ")...");
				break;
			case VoMP.STATE_CALLENDED:
				showSubLayout(VoMP.STATE_CALLENDED);
				remote_name_1.setText(remote_name);
				remote_number_1.setText(remote_did);
				callstatus_1.setText("Call ended (" + local_state + "."
						+ remote_state + ")...");
				break;
		}
	}


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		app.vompCall = this;

		// Mark call as being setup
		local_id = 0;
		remote_id = 0;
		local_state = 0;
		remote_state = 0;

		Intent intent = this.getIntent();
		remote_sid = new SubscriberId(intent.getStringExtra("sid"));
		String local_session_token_str =
				intent.getStringExtra("incoming_call_session");
		if (local_session_token_str != null) {
			// This is an incoming call, so note the local
			// session id.
			try {
				local_id = Integer.parseInt(local_session_token_str);
			} catch (Exception e) {
				// invalid call, so hang up
				app.vompCall = null;
				finish();
			}
		}

		remote_did = intent.getStringExtra("did");
		remote_name = intent.getStringExtra("name");
		if (remote_did == null)
			remote_did = "<no number>";
		if (remote_name == null || remote_name.equals(""))
			remote_name = remote_sid.abbreviation();

		setContentView(R.layout.call_layered);

		remote_name_1 = (TextView) findViewById(R.id.caller_name);
		remote_number_1 = (TextView) findViewById(R.id.ph_no_display);
		callstatus_1 = (TextView) findViewById(R.id.call_status);
		action_1 = (TextView) findViewById(R.id.call_action_type);
		action_2 = (TextView) findViewById(R.id.call_action_type_incoming);
		remote_name_2 = (TextView) findViewById(R.id.caller_name_incoming);
		remote_number_2 = (TextView) findViewById(R.id.ph_no_display_incoming);
		callstatus_2 = (TextView) findViewById(R.id.call_status_incoming);

		updateUI();

		ServalBatPhoneApplication.context.servaldMonitor.monitorVomp(true);
		// Establish call
		ServalBatPhoneApplication.context.servaldMonitor.sendMessage("call "
				+ remote_sid + " "
				+ Identities.getCurrentDid() + " " + remote_did);

		endButton = (Button) this.findViewById(R.id.cancel_call_button);
		endButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (local_state == VoMP.STATE_CALLENDED
						&& remote_state == VoMP.STATE_CALLENDED) {
					ServalBatPhoneApplication.context.vompCall = null;
					finish();
				} else {
					// Tell call to hang up
					ServalBatPhoneApplication.context.servaldMonitor
							.sendMessage("hangup "
							+ Integer.toHexString(local_id));
				}
			}
		});
		incomingEndButton = (Button) this.findViewById(R.id.incoming_decline);
		incomingEndButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Tell call to hang up
				ServalBatPhoneApplication.context.servaldMonitor
						.sendMessage("hangup "
						+ Integer.toHexString(local_id));
			}
		});
		incomingAnswerButton = (Button) this
				.findViewById(R.id.answer_button_incoming);
		incomingAnswerButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Tell call to hang up
				ServalBatPhoneApplication.context.servaldMonitor
						.sendMessage("pickup "
						+ Integer.toHexString(local_id));
			}
		});
	}

	private void showSubLayout(int state) {
		View incoming = findViewById(R.id.incoming);
		View incall = findViewById(R.id.incall);
		switch (state) {
		case VoMP.STATE_NOCALL:
		case VoMP.STATE_NOSUCHCALL:
		case VoMP.STATE_CALLPREP:
			action_1.setText("Preparing to Call");
			incall.setVisibility(View.VISIBLE);
			incoming.setVisibility(View.GONE);
			break;
		case VoMP.STATE_RINGINGOUT:
			action_1.setText("Calling");
			incall.setVisibility(View.VISIBLE);
			incoming.setVisibility(View.GONE);
			break;
		case VoMP.STATE_RINGINGIN:
			incall.setVisibility(View.GONE);
			incoming.setVisibility(View.VISIBLE);
			break;
		case VoMP.STATE_INCALL:
			action_1.setText("In Call");
			incoming.setVisibility(View.GONE);
			incall.setVisibility(View.VISIBLE);
			break;
		case VoMP.STATE_CALLENDED:
			action_1.setText("Call Ended");
			incoming.setVisibility(View.GONE);
			incall.setVisibility(View.VISIBLE);
			break;
		}
	}

	public void notifyCallStatus(int l_id, int r_id, int l_state, int r_state) {
		boolean update = false;
		Log.d("ServalDMonitor", "Considering update (before): lid="
				+ l_id
				+ ", local_id=" + local_id + ", rid=" + r_id
				+ ", remote_id=" + remote_id);

		if (r_id == 0 && local_id == 0) {
			// Keep an eye out for the call being created at our end ...
			local_id = l_id;
			remote_id = 0;
			local_state = l_state;
			remote_state = r_state;
			update = true;
		}
		else if (r_id != 0 && local_id == l_id && remote_id == 0) {
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
			Log.d("ServalDMonitor", "Poke UI");
			mHandler.post(updateCallStatus);
		} else {
			Log.d("ServalDMonitor", "Don't poke UI");
		}
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
