package org.servalproject.batphone;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.audio.AudioBuffer;
import org.servalproject.audio.AudioPlaybackStream;
import org.servalproject.audio.AudioRecordStream;
import org.servalproject.audio.AudioStream;
import org.servalproject.audio.BufferList;
import org.servalproject.audio.JitterStream;
import org.servalproject.audio.TranscodeStream;
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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
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
	String localIdString = null;
	VoMP.State local_state = State.NoSuchCall;
	VoMP.State remote_state = State.NoSuchCall;
	VoMP.Codec codec = VoMP.Codec.Signed16;
	private long lastKeepAliveTime;
	private long callStarted = SystemClock.elapsedRealtime();
	private long callEnded;
	private boolean uiStarted = false;
	private boolean initiated = false;
	private final ServalBatPhoneApplication app;
	private final ServalDMonitor monitor;
	private UnsecuredCall ui;
	private MediaPlayer mediaPlayer;
	private long ping = 0;
	private boolean sendPings = false;
	private BufferList bufferList;
	final Timer timer = new Timer();

	private Thread audioRecordThread;
	private AudioRecordStream recorder;
	public JitterStream player;
	private boolean ringing = false;
	private boolean audioRunning = false;

	private static final String TAG = "CallHandler";
	private AudioStream monitorOutput = new AudioStream() {
		@Override
		public int write(AudioBuffer buff) throws IOException {
			try {
				if (monitor.hasStopped())
					throw new EOFException();
				monitor.sendMessageAndData(buff.buff, buff.dataLen, "audio ",
						localIdString, " ",
						buff.codec.codeString, " ",
						Integer.toString(buff.sampleStart), " ",
						Integer.toString(buff.sequence));
			} finally {
				buff.release();
			}
			return 0;
		}
	};

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
		ServalDMonitor monitor = app.servaldMonitor;
		if (monitor == null)
			throw new IOException(
					"Serval is not currently running");
		app.callHandler = new CallHandler(app, monitor, peer);
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
				call.localIdString = Integer.toHexString(local_session);
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

	private CallHandler(ServalBatPhoneApplication app, ServalDMonitor monitor,
			Peer peer) {
		this.app = app;
		this.monitor = monitor;
		this.remotePeer = peer;
		this.did = peer.did;
		this.name = peer.name;
		lastKeepAliveTime = SystemClock.elapsedRealtime();

		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				long now = SystemClock.elapsedRealtime();
				if (now > (lastKeepAliveTime + 5000)) {
					// End call if no keep alive received
					Log.d(TAG,
							"Keepalive expired for call: "
									+ lastKeepAliveTime + " vs "
									+ now);
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
		Log.d(TAG, "Hanging up");

		// stop audio now, as servald will ignore it anyway
		if (audioRunning)
			this.stopAudio();

		if (monitor.hasStopped())
			endCall();
		else
			monitor.sendMessageAndLog("hangup ", Integer.toHexString(local_id));
	}

	public void pickup() {
		if (local_state != VoMP.State.RingingIn)
			return;

		Log.d(TAG, "Picking up");
		monitor.sendMessageAndLog("pickup ", Integer.toHexString(local_id));
	}

	private void startRinging() {
		if (ringing)
			return;

		Log.v(TAG, "Starting ring tone");
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
				Log.e(TAG,
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

		monitor.sendMessageAndLog("ringing ",
						Integer.toHexString(local_id));

		ringing = true;
	}

	private void stopRinging() {
		if (!ringing)
			return;

		Log.v(TAG, "Stopping ring tone");
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
			Log.v(TAG, "Starting audio");

			this.recorder.setStream(TranscodeStream.getEncoder(monitorOutput,
					codec));

			AudioManager am = (AudioManager) app
					.getSystemService(Context.AUDIO_SERVICE);

			AudioPlaybackStream playback = new AudioPlaybackStream(
					am,
					AudioManager.STREAM_VOICE_CALL,
					SAMPLE_RATE,
					AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					8 * 60 * 2);

			AudioStream output = TranscodeStream.getDecoder(playback);

			this.player = new JitterStream(output);
			this.player.startPlaying();

			callStarted = SystemClock.elapsedRealtime();
			audioRunning = true;
		} catch (Exception e) {
			Log.v(TAG, e.getMessage(), e);
		}
	}

	private void stopAudio() {
		if (this.recorder == null)
			throw new IllegalStateException(
					"Audio recorder has not been initialised");
		Log.v(TAG, "Stopping audio");
		this.recorder.close();
		try {
			this.player.close();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		audioRunning = false;
		callEnded = SystemClock.elapsedRealtime();
	}

	static final int AUDIO_BLOCK_SIZE = 20 * 8 * 2;
	static final int SAMPLE_RATE = 8000;

	private void endCall() {
		if (ui != null) {
			Log.v(TAG, "Starting completed call ui");
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
	}

	private void cleanup() {
		if (this.recorder != null) {
			this.recorder.close();
			recorder = null;
		}
		if (this.player != null)
			try {
				this.player.close();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		timer.cancel();
		NotificationManager nm = (NotificationManager) app
				.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel("Call", 0);
		app.callHandler = null;
	}

	private void callStateChanged() {

		Log.v(TAG, "Call state changed to " + local_state + ", "
				+ remote_state);

		if (remote_state == VoMP.State.RingingOut
				&& local_state.ordinal() <= VoMP.State.RingingIn.ordinal())
			startRinging();

		if (local_state.ordinal() > VoMP.State.RingingIn.ordinal())
			stopRinging();

		// TODO if remote_state == VoMP.State.RingingIn show / play indicator

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
			endCall();
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
				Log.v(TAG, "Starting in call ui");
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
			if (local_id == 0) {
				local_id = l_id;
				localIdString = Integer.toHexString(l_id);
			}

			VoMP.State newLocal = VoMP.State.getState(l_state);
			VoMP.State newRemote = VoMP.State.getState(r_state);

			boolean stateChanged = local_state != newLocal
					|| remote_state != newRemote;

			local_state = newLocal;
			remote_state = newRemote;
			lastKeepAliveTime = SystemClock.elapsedRealtime();

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
		Log.v(TAG, "Calling " + remotePeer.sid.abbreviation() + "/"
				+ did);
		initiated = true;
		monitor.sendMessageAndLog("call ",
				remotePeer.sid.toString(), " ",
				main.getDid(), " ", did);
	}

	public int receivedAudio(Iterator<String> args, InputStream in,
			int dataBytes) throws IOException {
		int local_session = ServalDMonitor.parseIntHex(args.next());
		if (local_id != local_session) {
			Log.v(TAG, "Mismatch, audio for wrong session");
			return 0;
		}
		lastKeepAliveTime = SystemClock.elapsedRealtime();

		if (bufferList == null)
			bufferList = new BufferList(VoMP.Codec.Signed16.maxBufferSize() / 2);

		if (dataBytes > bufferList.mtu) {
			Log.v(TAG, "Audio size " + dataBytes
					+ " is larger than buffer MTU " + bufferList.mtu);
			return 0;
		}
		AudioBuffer buff = bufferList.getBuffer();

		buff.received = lastKeepAliveTime;

		buff.codec = VoMP.Codec.getCodec(ServalDMonitor
				.parseInt(args.next()));
		buff.sampleStart = ServalDMonitor.parseInt(args.next());
		buff.sequence = ServalDMonitor.parseInt(args.next()); // sequence
		player.setJitterDelay(ServalDMonitor.parseInt(args.next()));
		buff.thisDelay = ServalDMonitor.parseInt(args.next());
		buff.dataLen = dataBytes;

		int read = 0;
		while (read < dataBytes) {
			int actualRead = in.read(buff.buff, read, dataBytes - read);
			if (actualRead < 0)
				throw new EOFException();
			read += actualRead;
		}
		player.write(buff);
		return read;
	}

	public void keepAlive(int l_id) {
		if (l_id == local_id) {
			lastKeepAliveTime = SystemClock.elapsedRealtime();
			if (sendPings && ping == 0) {
				Log.v(TAG, "Sending PING");
				this.ping = System.nanoTime();
				monitor.sendMessageAndLog("PING");
			}
		}
	}

	public void monitor(int flags) {
		if (ping != 0) {
			long pong = System.nanoTime();
			Log.v(TAG,
					"Serval monitor latency: "
							+ Double.toString((pong - ping) / 1000000000.0));
			ping = 0;
		}
	}

	public void codecs(int l_id, Iterator<String> args) {
		if (l_id != local_id)
			return;

		try {
			VoMP.Codec best = null;

			while (args.hasNext()) {
				int c = ServalDMonitor.parseInt(args.next());
				VoMP.Codec codec = VoMP.Codec.getCodec(c);
				if (!codec.isSupported())
					continue;

				if (best == null || codec.preference > best.preference) {
					best = codec;
				}
			}

			if (best == null)
				throw new IOException("Unable to find a common codec");

			this.codec = best;

			recorder = new AudioRecordStream(
					null,
					MediaRecorder.AudioSource.MIC,
					codec.sampleRate,
					AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					8 * 100 * 2,
					codec.audioBufferSize(),
					codec.maxBufferSize());

			audioRecordThread = new Thread(recorder, "Recording");
			audioRecordThread.start();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
			this.hangup();
		}
	}

	public long getCallStarted() {
		return callStarted;
	}

}
