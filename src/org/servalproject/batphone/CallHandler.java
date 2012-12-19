package org.servalproject.batphone;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.audio.AudioPlayer;
import org.servalproject.audio.AudioRecorder;
import org.servalproject.audio.Oslec;
import org.servalproject.batphone.VoMP.State;
import org.servalproject.servald.DnaResult;
import org.servalproject.servald.Identity;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalDMonitor;
import org.servalproject.servald.SubscriberId;

import android.app.NotificationManager;
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
	String did;
	String name;

	int local_id = 0;
	VoMP.State local_state = State.NoSuchCall;
	VoMP.State remote_state = State.NoSuchCall;
	VoMP.Codec codec = VoMP.Codec.Signed16;
	private long lastKeepAliveTime;
	private long callStarted = SystemClock.elapsedRealtime();
	private long callEnded;
	private boolean uiStarted = false;
	private boolean initiated = false;
	ServalBatPhoneApplication app;
	private UnsecuredCall ui;
	private MediaPlayer mediaPlayer;
	private long ping = 0;
	private boolean sendPings = false;
	final Timer timer = new Timer();

	public AudioRecorder recorder;
	public final AudioPlayer player;
	private boolean ringing = false;
	private boolean audioRunning = false;

	public static void dial(DnaResult result) throws IOException {
		CallHandler call = createCall(result.peer);
		call.did = result.did;
		call.name = result.name;
		call.dial();
	}

	public static void dial(Peer peer) throws IOException {
		dial(null, peer);
	}

	public static void dial(UnsecuredCall ui, Peer peer) throws IOException {
		CallHandler call = createCall(peer);
		call.ui = ui;
		call.dial();
	}

	private static synchronized CallHandler createCall(Peer peer)
			throws IOException {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		if (app.callHandler != null)
			throw new IOException(
					"Only one call is allowed at a time");
		app.callHandler = new CallHandler(peer);
		return app.callHandler;
	}

	private static class IncomingCall implements ServalDMonitor.Message {
		@Override
		public int message(String cmd, Iterator<String> args, InputStream in,
				int dataLength) throws IOException {
			try {
				int local_session = ServalDMonitor.parseIntHex(args.next());
				args.next(); // local_sid
				args.next(); // local_did
				SubscriberId remote_sid = new SubscriberId(args.next());
				String remote_did = args.next();

				Peer peer = PeerListService.getPeer(
						ServalBatPhoneApplication.context
								.getContentResolver(),
						remote_sid);

				CallHandler call = createCall(peer);
				call.local_id = local_session;
				call.did = remote_did;
				call.local_state = State.CallPrep;
				call.remote_state = State.RingingOut;
				call.callStateChanged();

				return 0;
			} catch (SubscriberId.InvalidHexException e) {
				throw new IOException("invalid SubscriberId token: " + e);
			}
		}
	}

	public static void registerMessageHandlers(ServalDMonitor monitor) {
		monitor.handlers.put("CALLFROM", new IncomingCall());
	}

	private CallHandler(Peer peer) {
		app = ServalBatPhoneApplication.context;
		Oslec echoCanceler = null;
		// TODO make sure echo canceler is beneficial.
		if (false)
			echoCanceler = new Oslec();
		this.player = new AudioPlayer(echoCanceler, app);
		this.remotePeer = peer;
		this.did = peer.did;
		this.name = peer.name;
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

	public void remoteHangUp(int local_id) {
		if (local_id != this.local_id)
			return;
		local_state = VoMP.State.CallEnded;
		this.callStateChanged();
	}

	public void hangup() {
		Log.d("VoMPCall", "Hanging up");

		// stop audio now, as servald will ignore it anyway
		if (audioRunning)
			this.stopAudio();

		app.servaldMonitor
				.sendMessageAndLog("hangup ", Integer.toHexString(local_id));
	}

	public void pickup() {
		if (local_state != VoMP.State.RingingIn)
			return;

		Log.d("VoMPCall", "Picking up");
		app.servaldMonitor
				.sendMessageAndLog("pickup ", Integer.toHexString(local_id));
	}

	private void startRinging() {
		if (ringing)
			return;

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

		app.servaldMonitor
				.sendMessageAndLog("ringing ",
						Integer.toHexString(local_id));

		ringing = true;
	}

	private void stopRinging() {
		if (!ringing)
			return;

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
		try {
			if (this.recorder == null)
				throw new IllegalStateException(
						"Audio recorder has not been initialised");
			Log.v("CallHandler", "Starting audio");
			this.recorder.startRecording(codec);
			this.player.startPlaying();
			callStarted = SystemClock.elapsedRealtime();
			audioRunning = true;
		} catch (Exception e) {
			Log.v("CallHandler", e.getMessage(), e);
		}
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

	private void prepareAudio() {
		try {
			this.player.prepareAudio();
			this.recorder.prepareAudio();
		} catch (IOException e) {
			Log.e("CallHandler", e.getMessage(), e);
		}
	}

	private void cleanup() {
		if (this.recorder != null)
			this.recorder.stopRecording();
		if (this.player != null)
			this.player.cleanup();
		timer.cancel();
		NotificationManager nm = (NotificationManager) app
				.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel("Call", 0);
		app.callHandler = null;
	}

	private void callStateChanged() {

		Log.v("CallHandler", "Call state changed to " + local_state + ", "
				+ remote_state);

		if (this.recorder == null && this.local_id != 0) {
			this.recorder = new AudioRecorder(player.echoCanceler,
					Integer.toHexString(local_id),
					ServalBatPhoneApplication.context.servaldMonitor);
		}

		if (remote_state == VoMP.State.RingingOut
				&& local_state.ordinal() <= VoMP.State.RingingIn.ordinal())
			startRinging();

		if (local_state.ordinal() > VoMP.State.RingingIn.ordinal())
			stopRinging();

		// TODO if remote_state == VoMP.State.RingingIn show / play indicator

		if (local_state == VoMP.State.RingingIn
				|| local_state == VoMP.State.RingingOut)
			prepareAudio();

		if (audioRunning != (local_state == VoMP.State.InCall)) {
			if (audioRunning) {
				stopAudio();
			} else {
				startAudio();
			}
		}

		// make sure invalid states don't open the UI

		switch (local_state) {
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
				myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
						Intent.FLAG_ACTIVITY_CLEAR_TOP |
						Intent.FLAG_ACTIVITY_SINGLE_TOP);
				app.startActivity(myIntent);

				ui.finish();
				setCallUI(null);

				// TODO play call ended sound?
			}
			// and we're done here.
			cleanup();

			return;

		case CallPrep:
		case NoCall:
		case NoSuchCall:

			// open the UI if we initiated the call, or we reached ringing
			// state.
			if (!initiated)
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
				myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
						Intent.FLAG_ACTIVITY_CLEAR_TOP |
						Intent.FLAG_ACTIVITY_SINGLE_TOP);
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

	public synchronized boolean notifyCallStatus(int l_id,
			int l_state, int r_state,
			SubscriberId r_sid) {

		if (r_sid.equals(remotePeer.sid) && (local_id == 0 || local_id == l_id)) {
			// make sure we only listen to events for the same remote sid & id

			local_id = l_id;

			VoMP.State newLocal = VoMP.State.getState(l_state);
			VoMP.State newRemote = VoMP.State.getState(r_state);

			boolean stateChanged = local_state != newLocal
					|| remote_state != newRemote;

			local_state = newLocal;
			remote_state = newRemote;

			if (stateChanged)
				callStateChanged();

			return true;
		}
		return false;
	}

	public void dial() {

		Identity main = Identity.getMainIdentity();
		if (main == null) {
			app.displayToastMessage("Unable to place call as I don't know who I am");
			return;
		}
		Log.v("CallHandler", "Calling " + remotePeer.sid.abbreviation() + "/"
				+ did);
		initiated = true;
		app.servaldMonitor.sendMessageAndLog("call ",
				remotePeer.sid.toString(), " ",
				main.getDid(), " ", did);
	}

	public int receivedAudio(int local_session, int start_time,
			int jitter_delay, int this_delay, VoMP.Codec codec, InputStream in,
			int dataBytes) throws IOException {
		lastKeepAliveTime = SystemClock.elapsedRealtime();
		return player.receivedAudio(
				local_session, start_time, jitter_delay, this_delay,
				codec, in, dataBytes);
	}

	public void keepAlive(int l_id) {
		if (l_id == local_id) {
			lastKeepAliveTime = SystemClock.elapsedRealtime();
			if (sendPings && ping == 0 && app.servaldMonitor != null) {
				Log.v("CallHandler", "Sending PING");
				this.ping = System.nanoTime();
				app.servaldMonitor.sendMessageAndLog("PING");
			}
		}
	}

	public void monitor(int flags) {
		if (ping != 0) {
			long pong = System.nanoTime();
			Log.v("CallHandler",
					"Serval monitor latency: "
							+ Double.toString((pong - ping) / 1000000000.0));
			ping = 0;
		}
	}

	private boolean isSupported(VoMP.Codec codec) {
		switch (codec) {
		case Signed16:
		case Ulaw8:
		case Alaw8:
			return true;
		}
		return false;
	}

	public void codecs(int l_id, Iterator<String> args) {
		if (l_id != local_id)
			return;

		VoMP.Codec best = null;

		while (args.hasNext()) {
			int c = ServalDMonitor.parseInt(args.next());
			VoMP.Codec codec = VoMP.Codec.getCodec(c);
			if (!isSupported(codec))
				continue;

			if (best == null || codec.preference > best.preference) {
				best = codec;
			}
		}
		this.codec = best;
	}

	public long getCallStarted() {
		return callStarted;
	}

}
