package org.servalproject.batphone;

import java.util.Timer;
import java.util.TimerTask;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.batphone.VoMP.State;
import org.servalproject.servald.Identities;
import org.servalproject.servald.Peer;
import org.servalproject.servald.SubscriberId;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;

// This class maintains the state of a call
// handles the lifecycle of recording and playback
// and the triggers the display of any activities.
public class CallHandler {
	final Peer remotePeer;
	int local_id = 0;
	int remote_id = 0;
	VoMP.State local_state = State.NoSuchCall;
	VoMP.State remote_state = State.NoSuchCall;
	private long lastKeepAliveTime;
	private long callStarted;
	private long callEnded;
	private boolean uiStarted = false;
	ServalBatPhoneApplication app;
	private UnsecuredCall ui;
	private MediaPlayer mediaPlayer;
	final Timer timer = new Timer();

	public AudioRecorder recorder;
	public final AudioPlayer player;
	private boolean ringing = false;
	private boolean audioRunning = false;

	public CallHandler(Peer peer) {
		app = ServalBatPhoneApplication.context;
		this.player = new AudioPlayer(app);
		this.remotePeer = peer;
		lastKeepAliveTime = SystemClock.elapsedRealtime();

		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (SystemClock.elapsedRealtime() > (lastKeepAliveTime + 5000)) {
					// End call if no keep alive received
					Log.d("VoMPCall",
							"Keepalive expired for call: "
									+ lastKeepAliveTime + " vs "
									+ SystemClock.elapsedRealtime());
					hangup();
				}
			}
		}, 0, 3000);
	}

	public void hangup() {
		if (local_state == VoMP.State.CallEnded
				|| local_state == VoMP.State.Error)
			return;

		Log.d("VoMPCall", "Hanging up");

		timer.cancel();

		// stop audio now, as servald will ignore it anyway
		if (audioRunning)
			this.stopAudio();

		app.servaldMonitor
				.sendMessageAndLog("hangup "
						+ Integer.toHexString(local_id));
	}

	public void pickup() {
		if (local_state != VoMP.State.RingingIn)
			return;

		Log.d("VoMPCall", "Picking up");
		app.servaldMonitor
				.sendMessageAndLog("pickup "
						+ Integer.toHexString(local_id));
	}

	private void startRinging() {
		Log.v("CallHandler", "Starting ring tone");
		final AudioManager audioManager = (AudioManager) app
				.getSystemService(Context.AUDIO_SERVICE);
		if (audioManager.getStreamVolume(AudioManager.STREAM_RING) != 0) {
			Uri alert = RingtoneManager
					.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
			if (mediaPlayer == null)
				mediaPlayer = new MediaPlayer();
			try {
				mediaPlayer.setDataSource(app, alert);
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
			Vibrator v = (Vibrator) app
					.getSystemService(Context.VIBRATOR_SERVICE);
			if (v != null) {
				// bzzt-bzzt ...... bzzt,bzzt ......
				long[] pattern = {
						0, 300, 200, 300, 2000
				};
				v.vibrate(pattern, 0);
			}
		}
		ringing = true;
	}

	private void stopRinging() {
		Log.v("CallHandler", "Stopping ring tone");
		if (mediaPlayer != null) {
			mediaPlayer.stop();
			mediaPlayer.release();
			mediaPlayer = null;
		}
		Vibrator v = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
		if (v != null)
			v.cancel();
		ringing = false;
	}

	private void startAudio() {
		if (this.recorder == null)
			throw new IllegalStateException(
					"Audio recorder has not been initialised");
		Log.v("CallHandler", "Starting audio");
		this.recorder.startRecording();
		this.player.startPlaying();
		audioRunning = true;
		callStarted = SystemClock.elapsedRealtime();
	}

	private void stopAudio() {
		if (this.recorder == null)
			throw new IllegalStateException(
					"Audio recorder has not been initialised");
		Log.v("CallHandler", "Stopping audio");
		this.recorder.stopRecording();
		this.player.stopPlaying();
		audioRunning = false;
		callEnded = SystemClock.elapsedRealtime();
	}

	private void callStateChanged() {

		Log.v("CallHandler", "Call state changed to " + local_state + ", "
				+ remote_state);

		if (ringing != (local_state == VoMP.State.RingingIn)) {
			if (ringing) {
				stopRinging();
			} else {
				startRinging();
			}
		}

		if (audioRunning != (local_state == VoMP.State.InCall)) {
			if (audioRunning) {
				stopAudio();
			} else {
				startAudio();
			}
		}

		// make sure invalid states don't open the UI

		switch (local_state) {
		case CallPrep:
		case NoCall:
		case NoSuchCall:
			break;

		case CallEnded:
		case Error:

			if (ui != null) {
				Log.v("CallHandler", "Starting completed call ui");
				Intent myIntent = new Intent(app,
						CompletedCall.class);

				myIntent.putExtra("sid", remotePeer.sid.toString());
				myIntent.putExtra("duration",
						Long.toString(callEnded - callStarted));
				// Create call as a standalone activity stack
				myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				app.startActivity(myIntent);

				ui.finish();
				setCallUI(null);
			}

			// and we're done here.
			timer.cancel();
			app.callHandler = null;

			break;
		default:
			if (ui == null && !uiStarted) {
				Log.v("CallHandler", "Starting in call ui");
				uiStarted = true;

				Intent myIntent = new Intent(
						ServalBatPhoneApplication.context,
						UnsecuredCall.class);

				// Create call as a standalone activity
				// stack
				myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				ServalBatPhoneApplication.context.startActivity(myIntent);
			}
		}

		if (ui != null)
			ui.runOnUiThread(ui.updateCallStatus);
	}

	public void setCallUI(UnsecuredCall ui) {
		this.ui = ui;
		uiStarted = ui != null;
	}

	public synchronized void notifyCallStatus(int l_id, int r_id, int l_state,
			int r_state,
			int fast_audio, SubscriberId l_sid, SubscriberId r_sid,
			String l_did, String r_did) {

		// audioSEPField = fast_audio != 0;

		if (r_sid.equals(remotePeer.sid) && (local_id == 0 || local_id == l_id)) {
			// make sure we only listen to events for the same remote sid & id

			local_id = l_id;
			remote_id = r_id;

			if (this.recorder == null && l_id != 0) {
				this.recorder = new AudioRecorder(Integer.toHexString(local_id));
			}

			VoMP.State newLocal = VoMP.State.getState(l_state);
			VoMP.State newRemote = VoMP.State.getState(r_state);
			if (local_state != newLocal || remote_state != newRemote) {
				local_state = newLocal;
				remote_state = newRemote;

				callStateChanged();
			}
		}
	}

	public void dial() {
		app.servaldMonitor.sendMessageAndLog("call "
				+ remotePeer.sid + " "
				+ Identities.getCurrentDid() + " " + remotePeer.did);
	}

	public void keepAlive(int l_id) {
		if (l_id == local_id) {
			lastKeepAliveTime = SystemClock.elapsedRealtime();
		}
	}
}
