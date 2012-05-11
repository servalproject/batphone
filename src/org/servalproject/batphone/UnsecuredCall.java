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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
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
	final Timer timer = new Timer();

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
	private boolean completed = false;
	private boolean audioSEPField;

	private int bufferSize;
	private int audioFrameSize;
	private int writtenAudioFrames;
	private int playbackLatency;

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
			startPlaying();
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
			stopPlaying();
			showSubLayout(VoMP.STATE_CALLENDED);
			remote_name_1.setText(remote_name);
			remote_number_1.setText(remote_did);
			callstatus_1.setText("Call ended (" + stateSummary() + ")...");
			win.clearFlags(incomingCallFlags);
			break;
		}
	}

	private synchronized void startRecording() {
		if (audioSEPField)
			return;

		if (ServalBatPhoneApplication.context.audioRecorder == null) {
			ServalBatPhoneApplication.context.audioRecorder = new AudioRecorder(
					Integer.toHexString(local_id));
			new Thread(ServalBatPhoneApplication.context.audioRecorder).start();
		}
	}

	private synchronized void stopRecording() {
		if (ServalBatPhoneApplication.context.audioRecorder != null) {
			ServalBatPhoneApplication.context.audioRecorder.done();
			ServalBatPhoneApplication.context.audioRecorder = null;
		}
	}

	static final int SAMPLE_RATE = 8000;

	private synchronized void startPlaying() {
		if (audioSEPField)
			return;

		if (ServalBatPhoneApplication.context.audioTrack == null) {
			bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
					AudioFormat.CHANNEL_CONFIGURATION_MONO,
					AudioFormat.ENCODING_PCM_16BIT);

			audioFrameSize = 2; // 16 bits per sample, with one channel
			writtenAudioFrames = 0;

			Log.v("VoMPCall", "Minimum reported playback buffer size is "
					+ bufferSize
					+ " = "
					+ (bufferSize / (double) (audioFrameSize * SAMPLE_RATE))
					+ " seconds");

			// ensure 60ms minimum playback buffer
			if (bufferSize < 8 * 60 * audioFrameSize)
				bufferSize = 8 * 60 * audioFrameSize;

			Log.v("VoMPCall", "Setting playback buffer size to " + bufferSize
					+ " = "
					+ (bufferSize / (double) (audioFrameSize * SAMPLE_RATE))
					+ " seconds");

			ServalBatPhoneApplication.context.audioTrack = new AudioTrack(
					AudioManager.STREAM_VOICE_CALL,
					SAMPLE_RATE,
					AudioFormat.CHANNEL_CONFIGURATION_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					bufferSize, AudioTrack.MODE_STREAM);

			AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			am.setMode(AudioManager.MODE_IN_CALL);
			am.setSpeakerphoneOn(false);
			am.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
					am.getStreamMaxVolume
							(AudioManager.STREAM_VOICE_CALL), 0);

			ServalBatPhoneApplication.context.audioTrack.play();
		}
	}

	private synchronized void stopPlaying() {
		if (ServalBatPhoneApplication.context.audioTrack != null) {
			ServalBatPhoneApplication.context.audioTrack.release();
			ServalBatPhoneApplication.context.audioTrack = null;
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
				if (app.vompCall.timer != null)
					app.vompCall.timer.cancel();
				app.vompCall = null;
				finish();
				return;
			}
		}

		audioSEPField = false;
		if (intent.getStringExtra("fast_audio") != null)
			try {
				if (Integer.parseInt(intent
						.getStringExtra("remote_state")) != 0)
					audioSEPField = true;
			} catch (Exception e) {
				// integer parse exception
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
			if (app.vompCall.timer != null) {
				app.vompCall.timer.cancel();
			}
			app.vompCall = null;
			finish();
			return;
		}

		final Handler handler = new Handler();
		Log.d("VoMPCall", "Setup keepalive timer");
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				handler.post(new Runnable() {
					@Override
					public void run() {
						if (SystemClock.elapsedRealtime() > (lastKeepAliveTime + 5000)) {
							// End call if no keep alive received
							Log.d("VoMPCall",
									"Keepalive expired for call: "
											+ lastKeepAliveTime + " vs "
											+ SystemClock.elapsedRealtime());
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
					.sendMessageAndLog("call "
							+ remote_sid + " "
							+ Identities.getCurrentDid() + " " + remote_did);

		endButton = (Button) this.findViewById(R.id.cancel_call_button);
		endButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (local_state >= VoMP.STATE_CALLENDED) {
					// should never happen, as we replace this activity with
					// a purpose-built call-ended activity
					Log.d("VoMPCall", "Calling finish() due to cancel button");
					ServalBatPhoneApplication.context.vompCall = null;
					finish();
				} else {
					// Tell call to hang up
					Log.d("VoMPCall", "Hanging up");
					ServalBatPhoneApplication.context.servaldMonitor
							.sendMessageAndLog("hangup "
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
						.sendMessageAndLog("hangup "
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
						.sendMessageAndLog("pickup "
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
			if (!completed) {
				completed = true;
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
			}

			ServalBatPhoneApplication.context.vompCall = null;
			Log.d("VoMPCall", "Calling finish()");

			finish();
			break;
		}
	}

	public void notifyCallStatus(int l_id, int r_id, int l_state, int r_state,
			int fast_audio, SubscriberId l_sid, SubscriberId r_sid,
			String l_did, String r_did) {
		boolean update = false;
		Log.d("ServalDMonitor", "Considering update (before): lid="
				+ l_id
				+ ", local_id=" + local_id + ", rid=" + r_id
				+ ", remote_id=" + remote_id + ", fast_audio=" + fast_audio
				+ ", l_sid=" + l_sid.abbreviation()
				+ ", r_sid=" + r_sid.abbreviation());

		audioSEPField = fast_audio != 0;

		if (local_id == 0 && remote_id == 0 && l_id != 0 && r_id != 0) {
			// outgoing call has been created and acknowledged in one go
			local_id = l_id;
			remote_id = r_id;
			local_state = l_state;
			remote_state = r_state;
			if (remote_sid == null & r_sid != null)
				remote_sid = r_sid;
			update = true;
		}
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
		stopPlaying();
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
		UnsecuredCall call = ServalBatPhoneApplication.context.vompCall;
		if (call != this) {
			Log.d("VompCall", "er, I am " + this + ", but vompCall=" + call
					+ ".  killing myself.");
			this.timer.cancel();
			this.finish();
			return;
		}
		// Log.d("VoMPCall", "Keep alive for " + Integer.toHexString(l_id)
		// + " (I am " + Integer.toHexString(local_id) + ")");
		if (l_id == call.local_id) {
			call.lastKeepAliveTime = SystemClock.elapsedRealtime();
			// Log.d("VoMPCall", "Keepalive time now " + lastKeepAliveTime);
		}

	}

	private void checkPlaybackRate(AudioTrack a, boolean before) {
		int headFramePosition = a.getPlaybackHeadPosition();
		playbackLatency = writtenAudioFrames - headFramePosition;

		if (headFramePosition == writtenAudioFrames)
			Log.v("VoMPCall", "Playback buffer empty!!");
		else {
			// Log.v("VoMPCall", "Playback buffer latency; " + playbackLatency
			// + " ("
			// + (playbackLatency / (double) SAMPLE_RATE) + ")");
		}
	}

	public boolean receivedAudio(int local_session, int start_time,
			int end_time,
			int codec, byte[] block, int byteCount) {
		// XXX for now just stuff audio into buffer as it is received
		AudioTrack a = ServalBatPhoneApplication.context.audioTrack;
		if (a != null)
			switch (codec) {
			case VoMP.VOMP_CODEC_PCM: {
				// Log.d("VoMPCall", "Sending " + byteCount
				// + " of data to audio device");
				checkPlaybackRate(a, true);

				if (playbackLatency * audioFrameSize + byteCount > bufferSize) {
					Log.v("VoMPCall",
							"Warning buffer overrun! ("
									+ (playbackLatency * audioFrameSize)
									+ " + " + byteCount + " > " + bufferSize
									+ ")");
				}

				int offset = 0;
				while (byteCount > 0) {
					int ret = a.write(block, offset, byteCount);
					if (ret < 0)
						break;
					writtenAudioFrames += ret / this.audioFrameSize;

					offset += ret;
					byteCount -= ret;
				}

				checkPlaybackRate(a, false);
				break;
			}
			}
		return true;
	}

}
