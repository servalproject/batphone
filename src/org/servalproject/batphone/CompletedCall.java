package org.servalproject.batphone;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.SubscriberId;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
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
		if (intent.getStringExtra("sid") == null) {
			Log.e("VoMPCall", "Missing argument sid");
			app
					.displayToastMessage("Missing argument sid");
			finish();
			return;
		}

		String sidString = intent.getStringExtra("sid");
		try {
			sid = new SubscriberId(sidString);
		}
		catch (SubscriberId.InvalidHexException e) {
			Log.e("CompletedCall", "Intent contains invalid SID: " + sidString, e);
			finish();
			return;
		}
		Peer p = PeerListService.getPeer(getContentResolver(), sid);

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
		remote_name.setText(p.getContactName());
		TextView remote_number = (TextView) findViewById(R.id.ph_no_display);
		remote_number.setText(p.did);

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
		// Changed Help System to local HTML files.

		Button endButton = (Button) this.findViewById(R.id.cancel_call_button);
		endButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

	}

	@Override
	protected void onPause() {
		super.onPause();
		// quit if the user manages to leave this screen in any way.
		finish();
	}

}
