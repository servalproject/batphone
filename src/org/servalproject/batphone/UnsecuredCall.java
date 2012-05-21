package org.servalproject.batphone;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.Identities;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
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

public class UnsecuredCall extends Activity implements Runnable{

	// setup basic call state tracking data
	Peer remotePeer;
	int local_id = 0;
	int remote_id = 0;
	int local_state = 0;
	int remote_state = 0;
	ServalBatPhoneApplication app;

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

	private boolean playing = false;
	private int bufferSize;
	private int audioFrameSize;
	private int writtenAudioFrames;
	private int playbackLatency;

	private Thread audioPlayback;
	private int lastSampleEnd;

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

			remote_name_1.setText(remotePeer.getContactName());
			remote_number_1.setText(remotePeer.did);

			callstatus_1.setText("Calling (" + stateSummary() + ")...");
			win.clearFlags(incomingCallFlags);
			break;
		case VoMP.STATE_RINGINGIN:
			startRinging();
			showSubLayout(VoMP.STATE_RINGINGIN);
			callstatus_2.setText("Incoming call (" + stateSummary() + ")...");
			win.addFlags(incomingCallFlags);
			break;
		case VoMP.STATE_INCALL:
			stopRinging();
			startRecording();
			startPlaying();
			showSubLayout(VoMP.STATE_INCALL);
			callstatus_1.setText("In call (" + stateSummary() + ")...");

			win.clearFlags(incomingCallFlags);
			break;
		case VoMP.STATE_CALLENDED:
		case VoMP.STATE_ERROR:
			stopRinging();
			stopRecording();
			stopPlaying();
			showSubLayout(VoMP.STATE_CALLENDED);
			callstatus_1.setText("Call ended (" + stateSummary() + ")...");
			win.clearFlags(incomingCallFlags);
			break;
		}
	}

	private synchronized void startRecording() {
		if (audioSEPField)
			return;

		if (app.audioRecorder == null) {
			app.audioRecorder = new AudioRecorder(
					Integer.toHexString(local_id));
			new Thread(app.audioRecorder,
					"Recorder").start();
		}
	}

	private synchronized void stopRecording() {
		if (app.audioRecorder != null) {
			app.audioRecorder.done();
			app.audioRecorder = null;
		}
	}

	static final int SAMPLE_RATE = 8000;

	private synchronized void startPlaying() {
		if (audioSEPField)
			return;

		if (audioPlayback == null) {
			audioPlayback = new Thread(this, "Playback");
			audioPlayback.start();
		}
	}

	private synchronized void stopPlaying() {
		playing = false;
		if (audioPlayback != null)
			audioPlayback.interrupt();
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

		app = (ServalBatPhoneApplication) this.getApplication();
		app.vompCall = this;

		// Mark call as being setup
		local_id = 0;
		remote_id = 0;
		local_state = 0;
		remote_state = 0;

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
			SubscriberId sid = new SubscriberId(sidString);
			this.remotePeer = PeerListService
					.getPeer(getContentResolver(), sid);

		} catch (SubscriberId.InvalidHexException e) {
			Log.e("VoMPCall", "Intent contains invalid SID: " + sidString, e);
			finish();
			return;
		}

		// SID has been provided, so mark the call as starting
		local_state = VoMP.STATE_NOCALL;

		local_id = intent.getIntExtra("incoming_call_session", 0);
		remote_state = intent.getIntExtra("remote_state", 0);
		audioSEPField = (intent.getIntExtra("fast_audio", 0) > 0 && remote_state > 0);
		local_state = intent.getIntExtra("local_state", -1);

		if (local_state == -1) {
			local_state = 0;
			// Establish call
			app.servaldMonitor
					.sendMessageAndLog("call "
							+ remotePeer.sid + " "
							+ Identities.getCurrentDid() + " " + remotePeer.did);

		} else if ((local_state == 0 && remote_state == 0)
				|| local_state >= VoMP.STATE_CALLENDED) {
			// Refuse to start call in silly states
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
				if (SystemClock.elapsedRealtime() > (lastKeepAliveTime + 5000)) {
					// End call if no keep alive received
					Log.d("VoMPCall",
							"Keepalive expired for call: "
									+ lastKeepAliveTime + " vs "
									+ SystemClock.elapsedRealtime());
					local_state = VoMP.STATE_ERROR;
					remote_state = VoMP.STATE_ERROR;

					timer.cancel();
					try {
						app.servaldMonitor
								.sendMessage("hangup "
										+ Integer.toHexString(local_id));
					}
					catch (Exception e) {
						// catch null pointer if required
					}
					handler.post(new Runnable() {
						@Override
						public void run() {
							updateUI();
						}
					});
				}
			}
		}, 0, 3000);

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

		updatePeerDisplay();
		if (this.remotePeer.cacheUntil < SystemClock.elapsedRealtime()) {
			new AsyncTask<Void, Void, Void>() {

				@Override
				protected void onPostExecute(Void result) {
					updatePeerDisplay();
				}

				@Override
				protected Void doInBackground(Void... params) {
					PeerListService.resolve(remotePeer);
					return null;
				}
			}.execute();
		}
		updateUI();

		endButton = (Button) this.findViewById(R.id.cancel_call_button);
		endButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (local_state >= VoMP.STATE_CALLENDED) {
					// should never happen, as we replace this activity with
					// a purpose-built call-ended activity
					Log.d("VoMPCall", "Calling finish() due to cancel button");
					app.vompCall = null;
					finish();
				} else {
					// Tell call to hang up
					Log.d("VoMPCall", "Hanging up");
					app.servaldMonitor
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
				app.servaldMonitor
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
				app.servaldMonitor
						.sendMessageAndLog("pickup "
						+ Integer.toHexString(local_id));
			}
		});
	}

	private void updatePeerDisplay() {
		remote_name_1.setText(remotePeer.getContactName());
		remote_number_1.setText(remotePeer.did);
		remote_name_2.setText(remotePeer.getContactName());
		remote_number_2.setText(remotePeer.did);
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
				Intent myIntent = new Intent(app,
					CompletedCall.class);

				myIntent.putExtra("sid", remotePeer.sid.toString());
				myIntent.putExtra("duration",
						"" + (SystemClock.elapsedRealtime() - chron.getBase()));
				// Create call as a standalone activity stack
				myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				app.startActivity(myIntent);
			}

			app.vompCall = null;
			Log.d("VoMPCall", "Calling finish()");

			finish();
			break;
		}
	}

	public void notifyCallStatus(int l_id, int r_id, int l_state, int r_state,
			int fast_audio, SubscriberId l_sid, SubscriberId r_sid,
			String l_did, String r_did) {
		Log.d("ServalDMonitor", "Considering update (before): lid="
				+ l_id
				+ ", local_id=" + local_id + ", rid=" + r_id
				+ ", remote_id=" + remote_id + ", fast_audio=" + fast_audio
				+ ", l_sid=" + l_sid.abbreviation()
				+ ", r_sid=" + r_sid.abbreviation());

		audioSEPField = fast_audio != 0;

		if (r_sid.equals(remotePeer.sid) && (local_id == 0 || local_id == l_id)) {
			// make sure we only listen to events for the same remote sid & id

			local_id = l_id;
			remote_id = r_id;

			if (local_state != l_state || remote_state != r_state) {
				local_state = l_state;
				remote_state = r_state;

				Log.d("ServalDMonitor", "Poke UI");
				mHandler.post(updateCallStatus);
			}
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
		UnsecuredCall call = app.vompCall;
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

	class AudioBuffer implements Comparable<AudioBuffer> {
		byte buff[] = new byte[VoMP.MAX_AUDIO_BYTES];
		int dataLen;
		int sampleStart;
		int sampleEnd;

		@Override
		public int compareTo(AudioBuffer arg0) {
			if (0 < arg0.sampleStart - this.sampleStart)
				return -1;
			else if (this.sampleStart == arg0.sampleStart)
				return 0;
			return 1;
		}
	}

	// Add packets (primarily) to the the start of the list, play them from the
	// end
	// assuming that packet re-ordering is rare we shouldn't have to traverse
	// the list very much to add a packet.
	LinkedList<AudioBuffer> playList = new LinkedList<AudioBuffer>();
	Stack<AudioBuffer> reuseList = new Stack<AudioBuffer>();

	int lastQueuedSampleEnd = 0;

	public int receivedAudio(int local_session, int start_time,
			int end_time,
			int codec, DataInputStream in, int byteCount) throws IOException {

		int ret=0;

		if (!playing) {
			Log.v("VoMPCall", "Dropping audio as we are not currently playing");
			return 0;
		}

		switch (codec) {
		case VoMP.VOMP_CODEC_PCM: {

			if (end_time == lastQueuedSampleEnd || end_time <= lastSampleEnd) {
				// Log.v("VoMPCall", "Ignoring buffer");
				return 0;
			}
			AudioBuffer buff;
			if (reuseList.size() > 0)
				buff = reuseList.pop();
			else
				buff = new AudioBuffer();
			in.readFully(buff.buff, 0, byteCount);
			ret = byteCount;
			buff.dataLen = byteCount;
			buff.sampleStart = start_time;
			buff.sampleEnd = end_time;

			synchronized (playList) {
				if (playList.isEmpty()
						|| buff.compareTo(playList.getFirst()) < 0) {

					// add this buffer to play *now*
					if (playList.isEmpty())
						lastQueuedSampleEnd = end_time;

					playList.addFirst(buff);
					if (audioPlayback != null)
						audioPlayback.interrupt();
				} else if (buff.compareTo(playList.getLast()) > 0) {
					// yay, packets arrived in order
					lastQueuedSampleEnd = end_time;
					playList.addLast(buff);
				} else {
					// find where to insert this item
					ListIterator<AudioBuffer> i = playList.listIterator();
					while (i.hasNext()) {
						AudioBuffer compare = i.next();
						switch (buff.compareTo(compare)) {
						case -1:
							i.previous();
							i.add(buff);
							return ret;
						case 0:
							reuseList.push(buff);
							return ret;
						}
					}
					reuseList.push(buff);
				}
			}

			break;
		}
		}
		return ret;
	}

	int jitter = 16000;

	private void writeAudio(AudioTrack a, byte buff[], int len) {
		int offset = 0;
		while (offset < len) {
			int ret = a.write(buff, offset, len - offset);
			if (ret < 0)
				break;
			offset += ret;
			writtenAudioFrames += ret / this.audioFrameSize;
		}
	}

	static final int MIN_BUFFER = 20000000;

	@Override
	public void run() {

		AudioTrack a;
		byte silence[];

		synchronized (this) {
			if (playing) {
				Log.v("VoMPCall", "Already playing?");
				return;
			}

			Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

			bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
					AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT);

			audioFrameSize = 2; // 16 bits per sample, with one channel
			writtenAudioFrames = 0;

			Log.v("VoMPCall",
					"Minimum reported playback buffer size is "
							+ bufferSize
							+ " = "
							+ (bufferSize / (double) (audioFrameSize * SAMPLE_RATE))
							+ " seconds");

			// ensure 60ms minimum playback buffer
			if (bufferSize < 8 * 60 * audioFrameSize)
				bufferSize = 8 * 60 * audioFrameSize;

			Log.v("VoMPCall",
					"Setting playback buffer size to "
							+ bufferSize
							+ " = "
							+ (bufferSize / (double) (audioFrameSize * SAMPLE_RATE))
							+ " seconds");

			a = new AudioTrack(
					AudioManager.STREAM_VOICE_CALL,
					SAMPLE_RATE,
					AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					bufferSize, AudioTrack.MODE_STREAM);

			AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			am.setMode(AudioManager.MODE_IN_CALL);
			am.setSpeakerphoneOn(false);
			am.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
					am.getStreamMaxVolume
							(AudioManager.STREAM_VOICE_CALL), 0);

			a.play();
			playing = true;
			silence = new byte[bufferSize];
			// fill the audio buffer once.
			writeAudio(a, silence, bufferSize);
		}
		lastSampleEnd = 0;
		StringBuilder sb = new StringBuilder();

		while (playing) {

			if (sb.length() >= 128) {
				Log.v("VoMPCall", sb.toString());
				sb.setLength(0);
			}

			AudioBuffer buff = null;
			long now = 0;
			int generateSilence = 0;
			long audioRunsOutAt;

			synchronized (playList) {
				if (!playList.isEmpty())
					buff = playList.getFirst();

				now = System.nanoTime();
				playbackLatency = writtenAudioFrames
						- a.getPlaybackHeadPosition();

				// work out when we must make a decision about playing some
				// extra silence
				audioRunsOutAt = now - MIN_BUFFER
						+ (long) (playbackLatency * 1000000.0 / SAMPLE_RATE);

				// calculate an absolute maximum delay based on our maximum
				// extra latency
				int queuedLengthInMs = lastQueuedSampleEnd - lastSampleEnd;

				if (buff != null) {
					int silenceGap = buff.sampleStart - (lastSampleEnd + 1);

					if (silenceGap > 0) {

						// try to wait until the last possible moment before
						// giving up and playing the buffer we have
						if (audioRunsOutAt <= now) {
							sb.append("M");
							generateSilence = silenceGap;
							lastSampleEnd = buff.sampleStart - 1;
						}
						buff = null;
					} else {
						// we either need to play it or skip it, so remove it
						// from the queue
						playList.removeFirst();

						if (silenceGap < 0) {
							// sample arrived too late, we might get better
							// audio if we add a little extra latency
							reuseList.push(buff);
							sb.append("L");
							continue;
						}

						if (queuedLengthInMs * 100 > jitter) {
							// our queue is getting too long
							// drop some audio, but count it as played so we
							// don't immediately play silence or try to wait for
							// this "missing" audio packet to arrive

							sb.append("F");
							lastSampleEnd = buff.sampleEnd;
							reuseList.push(buff);
							jitter += 50;
							continue;
						}
						if (jitter > 10000)
							jitter -= 10;
					}
				} else {
					// this thread can sleep for a while to wait for more audio

					// But if we've got nothing else to play, we should play
					// some silence to increase our latency buffer
					if (audioRunsOutAt <= now) {
						sb.append("X");
						generateSilence = 20;
						jitter += 2000;
					}

				}
			}

			if (generateSilence > 0) {
				// write some audio silence, then check the packet queue again
				// (8 samples per millisecond, 2 bytes per sample)
				int silenceDataLength = generateSilence * 16;
				sb.append("{" + generateSilence + "}");
				while (silenceDataLength > 0) {
					int len = silenceDataLength > silence.length ? silence.length
							: silenceDataLength;
					writeAudio(a, silence, len);
					silenceDataLength -= len;
				}
				continue;
			}

			if (buff != null) {
				// write the audio sample, then check the packet queue again
				lastSampleEnd = buff.sampleEnd;
				writeAudio(a, buff.buff, buff.dataLen);
				sb.append(".");
				synchronized (playList) {
					reuseList.push(buff);
				}
				continue;
			}

			// check the clock again, then wait only until our audio buffer is
			// getting close to empty
			now = System.nanoTime();
			long waitFor = audioRunsOutAt - now;
			if (waitFor <= 0)
				continue;
			sb.append(" ");
			long waitMs = waitFor / 1000000;
			int waitNs = (int) (waitFor - waitMs * 1000000);

			try {
				Thread.sleep(waitMs, waitNs);
			} catch (InterruptedException e) {
			}
		}
		a.stop();
		a.release();
		playList.clear();
		reuseList.clear();
		audioPlayback = null;
	}

}
