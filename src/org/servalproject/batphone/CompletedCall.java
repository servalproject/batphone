package org.servalproject.batphone;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.SubscriberId;

import android.util.Log;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

public class CompletedCall extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;

		SubscriberId sid=null;

		Intent intent = this.getIntent();
		String sidString = intent.getStringExtra("sid");
		if (sidString != null) {
			try {
				sid = new SubscriberId(sidString);
			}
			catch (SubscriberId.InvalidHexException e) {
				Log.e("CompletedCall", "Intent contains invalid SID: " + sidString, e);
			}
		}
		String did = intent.getStringExtra("did");
		String name = intent.getStringExtra("name");
		String duration = intent.getStringExtra("duration");
		int duration_ms = 0;
		try {
			duration_ms = Integer.parseInt(duration);
		} catch (Exception e) {
			// catch integer parse exceptions;
		}

		setContentView(R.layout.call_layered);

		Chronometer chron = (Chronometer) findViewById(R.id.call_time);
		if (chron != null)
			chron.setBase(SystemClock.elapsedRealtime() - duration_ms);
		TextView remote_name = (TextView) findViewById(R.id.caller_name);
		if (remote_name != null)
			remote_name.setText(name);
		TextView remote_number = (TextView) findViewById(R.id.ph_no_display);
		if (remote_number != null)
			remote_number.setText(did);
		TextView callstatus = (TextView) findViewById(R.id.call_status);
		if (callstatus != null)
			callstatus.setText("Call ended");
		TextView action = (TextView) findViewById(R.id.call_action_type);
		if (action != null)
			action.setText("Call Ended");

		View incoming = findViewById(R.id.incoming);
		if (incoming != null)
			incoming.setVisibility(View.INVISIBLE);
		View incall = findViewById(R.id.incall);
		if (incall != null)
			incall.setVisibility(View.VISIBLE);
		Button speaker = (Button) findViewById(R.id.speaker_button);
		if (speaker != null)
			speaker.setVisibility(View.INVISIBLE);
		Button dtmf = (Button) findViewById(R.id.dialpad_button);
		if (dtmf != null)
			dtmf.setVisibility(View.INVISIBLE);

		Button endButton = (Button) this.findViewById(R.id.cancel_call_button);
		endButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

	}

}
