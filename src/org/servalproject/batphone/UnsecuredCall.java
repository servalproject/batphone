package org.servalproject.batphone;

import java.util.Timer;
import java.util.TimerTask;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.Identities;
import org.servalproject.servald.SubscriberId;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
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
	private Chronometer chron;
	private MediaPlayer mediaPlayer;
	private long lastKeepAliveTime;

	private String stateSummary()
	{
		return local_state + "."
				+ remote_state;
	}

	private void updateUI()
	{
		final Window win = getWindow();
		int incomingCallFlags =
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
				| WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
				| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
						| WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
		Log.d("VoMPCall", "Updating UI for state " + local_state
					+ "." + remote_state);
		switch (local_state) {
		case VoMP.STATE_CALLPREP: case VoMP.STATE_NOCALL:
		case VoMP.STATE_RINGINGOUT:
			stopRinging();
			showSubLayout(VoMP.STATE_RINGINGOUT);

			remote_name_1.setText(remote_name);
			remote_number_1.setText(remote_did);

			callstatus_1.setText("Calling (" + stateSummary() + ")...");
			win.clearFlags(incomingCallFlags);
			break;
		case VoMP.STATE_RINGINGIN:
			startRinging();
			showSubLayout(VoMP.STATE_RINGINGIN);
			remote_name_2.setText(remote_name);
			remote_number_2.setText(remote_did);
			callstatus_2.setText("Incoming call (" + stateSummary() + ")...");
			win.addFlags(incomingCallFlags);
			break;
		case VoMP.STATE_INCALL:
			stopRinging();
			startRecording();
			showSubLayout(VoMP.STATE_INCALL);
			remote_name_1.setText(remote_name);
			remote_number_1.setText(remote_did);
			callstatus_1.setText("In call (" + stateSummary() + ")...");

			win.clearFlags(incomingCallFlags);
			break;
		case VoMP.STATE_CALLENDED:
		case VoMP.STATE_ERROR:
			stopRinging();
			stopRecording();
			showSubLayout(VoMP.STATE_CALLENDED);
			remote_name_1.setText(remote_name);
			remote_number_1.setText(remote_did);
			callstatus_1.setText("Call ended (" + stateSummary() + ")...");
			win.clearFlags(incomingCallFlags);
			break;
		}
	}

	private synchronized void startRecording() {
		if (ServalBatPhoneApplication.context.audioRecorder == null) {
			ServalBatPhoneApplication.context.audioRecorder = new AudioRecorder(
					Integer.toHexString(local_id));
			new Thread(ServalBatPhoneApplication.context.audioRecorder).start();
		}
	}

	private synchronized void stopRecording() {
		if (ServalBatPhoneApplication.context.audioRecorder != null) {
			ServalBatPhoneApplication.context.audioRecorder.done();
		}
	}

	private void startRinging() {
		final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		if (audioManager.getStreamVolume(AudioManager.STREAM_RING) != 0) {
			Uri alert = RingtoneManager
				.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
			if (mediaPlayer == null)
				mediaPlayer = new MediaPlayer();
			try {
				mediaPlayer.setDataSource(this, alert);
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
				mediaPlayer.setLooping(true);
				mediaPlayer.prepare();
				mediaPlayer.start();
			} catch (Exception e) {
				Log.e("VoMPCall",
						"Could not get ring tone: " + e.toString(), e);
			}
		} else {
			// volume off, so vibrate instead
			Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
			// bzzt-bzzt ...... bzzt,bzzt ......
			long[] pattern = {
					0, 300, 200, 300, 2000
			};
			v.vibrate(pattern, 0);
		}
	}

	private void stopRinging() {
		if (mediaPlayer != null)
			mediaPlayer.stop();
		Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		v.cancel();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d("VoMPCall", "Activity started");
		lastKeepAliveTime = SystemClock.elapsedRealtime();

		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		app.vompCall = this;

		// Mark call as being setup
		local_id = 0;
		remote_id = 0;
		local_state = 0;
		remote_state = 0;

		Intent intent = this.getIntent();
		if (intent.getStringExtra("sid") != null) {
			remote_sid = new SubscriberId(intent.getStringExtra("sid"));
			// SID has been provided, so mark the call as starting
			local_state = VoMP.STATE_NOCALL;
		}
		else
			remote_sid = null;
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
				return;
			}
		}

		if (intent.getStringExtra("remote_state") != null)
			try {
				remote_state = Integer.parseInt(intent
						.getStringExtra("remote_state"));
			} catch (Exception e) {
				// integer parse exception
			}
		if (intent.getStringExtra("local_state") != null)
			try {
				local_state = Integer.parseInt(intent
						.getStringExtra("local_state"));
			} catch (Exception e) {
				// integer parse exception
			}

		// Refuse to start call in silly states
		if ((local_state == 0 && remote_state == 0)
				|| local_state >= VoMP.STATE_CALLENDED)
		{
			Log.d("VoMPCall", "We are finished before we began");
			app.vompCall = null;
			finish();
			return;
		}

		final Handler handler = new Handler();
		final Timer timer = new Timer();
		Log.d("VoMPCall", "Setup keepalive timer");
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				handler.post(new Runnable() {
					@Override
					public void run() {
						if (SystemClock.elapsedRealtime() > (lastKeepAliveTime + 5000)) {
							// End call if no keep alive received
							Log.d("VoMPCall", "Keepalive expired for call");
							local_state = VoMP.STATE_ERROR;
							remote_state = VoMP.STATE_ERROR;
							updateUI();
							timer.cancel();
							try {
								ServalBatPhoneApplication.context.servaldMonitor
										.sendMessage("hangup "
												+ Integer.toHexString(local_id));
							}
							catch (Exception e) {
								// catch null pointer if required
							}
						}
					}
				});
			}
		}, 0, 3000);

		remote_did = intent.getStringExtra("did");
		remote_name = intent.getStringExtra("name");
		if (remote_did == null)
			remote_did = "<no number>";
		if (remote_name == null || remote_name.equals(""))
			remote_name = remote_sid.abbreviation();

		setContentView(R.layout.call_layered);

		chron = (Chronometer) findViewById(R.id.call_time);
		remote_name_1 = (TextView) findViewById(R.id.caller_name);
		remote_number_1 = (TextView) findViewById(R.id.ph_no_display);
		callstatus_1 = (TextView) findViewById(R.id.call_status);
		action_1 = (TextView) findViewById(R.id.call_action_type);
		action_2 = (TextView) findViewById(R.id.call_action_type_incoming);
		remote_name_2 = (TextView) findViewById(R.id.caller_name_incoming);
		remote_number_2 = (TextView) findViewById(R.id.ph_no_display_incoming);
		callstatus_2 = (TextView) findViewById(R.id.call_status_incoming);

		updateUI();

		if (local_session_token_str == null)
			// Establish call
			ServalBatPhoneApplication.context.servaldMonitor
					.sendMessage("call "
							+ remote_sid + " "
							+ Identities.getCurrentDid() + " " + remote_did);

		endButton = (Button) this.findViewById(R.id.cancel_call_button);
		endButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (local_state >= VoMP.STATE_CALLENDED) {
					// should never happen, as we replace this activity with
					// a purpose-built call-ended activity
					ServalBatPhoneApplication.context.vompCall = null;
					finish();
				} else {
					// Tell call to hang up
					Log.d("VoMPCall", "Hanging up");
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
				Log.d("VoMPCall", "Hanging up");
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
				// Tell call to pickup
				Log.d("VoMPCall", "Picking up");
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
			chron.setBase(SystemClock.elapsedRealtime());
			chron.start();
			action_1.setText("In Call");
			incoming.setVisibility(View.GONE);
			incall.setVisibility(View.VISIBLE);
			break;
		case VoMP.STATE_CALLENDED:
		case VoMP.STATE_ERROR:
			// The animation when switching to the call ended
			// activity is annoying, but I don't know how to fix it.
			incoming.setVisibility(View.GONE);
			incall.setVisibility(View.GONE);

			// Now pass over to call-ended activity
			Intent myIntent = new Intent(ServalBatPhoneApplication.context,
					CompletedCall.class);
			myIntent.putExtra("sid", remote_sid.toString());
			myIntent.putExtra("did", remote_did);
			myIntent.putExtra("name", remote_name);
			myIntent.putExtra("duration",
					"" + (SystemClock.elapsedRealtime() - chron.getBase()));
			// Create call as a standalone activity stack
			myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			ServalBatPhoneApplication.context.startActivity(myIntent);

			ServalBatPhoneApplication.context.vompCall = null;
			Log.d("VoMPCall", "Calling finish()");
			finish();
			break;
		}
	}

	public void notifyCallStatus(int l_id, int r_id, int l_state, int r_state,
			SubscriberId l_sid, SubscriberId r_sid, String l_did, String r_did) {
		boolean update = false;
		Log.d("ServalDMonitor", "Considering update (before): lid="
				+ l_id
				+ ", local_id=" + local_id + ", rid=" + r_id
				+ ", remote_id=" + remote_id
				+ ", l_sid=" + l_sid.abbreviation()
				+ ", r_sid=" + r_sid.abbreviation());

		if (r_id == 0 && local_id == 0) {
			// Keep an eye out for the call being created at our end ...
			local_id = l_id;
			remote_id = 0;
			local_state = l_state;
			remote_state = r_state;
			if (remote_sid == null & r_sid != null)
				remote_sid = r_sid;
			update = true;
		}
		else if (r_id != 0 && local_id == l_id && remote_id == 0) {
			// ... and at the other end ...
			remote_id = r_id;
			local_state = l_state;
			remote_state = r_state;
			if (remote_sid == null & r_sid != null)
				remote_sid = r_sid;
			update = true;
		}
		else if (l_id == local_id && r_id == remote_id
				&& remote_id != 0) {
			// ... and the resulting call then changing state
			local_state = l_state;
			remote_state = r_state;
			if (remote_sid == null & r_sid != null)
				remote_sid = r_sid;
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
		stopRinging();
		stopRecording();
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

	public void keepAlive(int l_id) {
		if (l_id == local_id) {
			lastKeepAliveTime = SystemClock.elapsedRealtime();
		}

	}

}
