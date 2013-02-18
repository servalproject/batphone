package org.servalproject.audio;

import java.io.IOException;
import java.io.InputStream;

import org.servalproject.batphone.VoMP;
import org.servalproject.servald.ServalDMonitor;

import uk.co.mmscomputing.sound.CompressInputStream;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

public class AudioRecorder implements Runnable {
	private static final String TAG = "AudioRecorder";

	private boolean stopMe = false;
	private final String call_session_token;
	private Thread audioThread;
	private ServalDMonitor monitor;

	private InputStream audioInput;
	private Oslec echoCanceler;
	private InputStream codecInput;
	private VoMP.Codec codec = null;

	private boolean discard = false;

	public AudioRecorder(Oslec echoCanceler, String token,
			ServalDMonitor monitor) {
		call_session_token = token;
		this.monitor = monitor;
		this.echoCanceler = echoCanceler;
	}

	public synchronized void startRecording(VoMP.Codec codec)
			throws IOException {
		Log.v(TAG, "Start recording " + codec);

		if (this.codec != null)
			throw new IOException("Recording already started");

		if (audioThread == null)
			throw new IOException("Audio recording has not been prepared");

		switch (codec) {
		case Signed16:
			codecInput = audioInput;
			break;
		case Alaw8:
			codecInput = new CompressInputStream(audioInput, true);
			break;
		case Ulaw8:
			codecInput = new CompressInputStream(audioInput, false);
			break;
		default:
			throw new IOException(codec + " is not yet supported");
		}

		this.codec = codec;
		this.discard = false;
	}

	public synchronized void stopRecording() {
		if (audioThread != null) {
			stopMe = true;
			audioThread.interrupt();
		}
	}

	public void prepareAudio() throws IOException {
		this.discard = true;

		if (audioThread == null) {
			audioThread = new Thread(this, "Recording");
			audioThread.start();
		}
	}

	private void prepare() throws IOException {
		if (audioInput != null)
			return;

		// ensure 60ms minimum record buffer
		audioInput = new AudioInputStream(echoCanceler,
				MediaRecorder.AudioSource.MIC,
				8000,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				8 * 60 * 2);
		codecInput = audioInput;
	}

	private void cleanup() {
		if (audioInput == null)
			return;

		try {
			codecInput.close();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		audioInput = null;
		codecInput = null;
	}

	@Override
	public void run() {
		try {
			prepare();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			return;
		}

		// get one block of audio at a time.
		// get in byte[], even though samples are natively short[],
		// as it ends up being more efficient when we hand them to
		// a codec or just send them as raw PCM
		int bytesRead = 0;
		byte[] block = null;

		Log.d(TAG, "Starting loop");

		// We need to be careful that we don't buffer more audio than we can
		// process, send and play.
		// The rest of the audio and network processing seems to be well enough
		// behaved. So we can probably afford to boost this thread so that we
		// don't miss anything

		Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

		while (!stopMe) {
			try {
				if (discard || codec == null) {
					// skip 20ms of audio at a time until we know the codec
					// we are going to use
					audioInput.skip(20 * 8 * 2);
					continue;
				}

				if (block == null) {
					switch (codec) {
					case Signed16:
						block = new byte[20 * 8 * 2];
						break;
					case Ulaw8:
					case Alaw8:
						block = new byte[20 * 8];
						break;
					}
					Log.v(TAG, "Starting to read audio in " + block.length
							+ " byte blocks");
				}

				if (bytesRead < block.length) {
					int bytes = codecInput.read(block, bytesRead,
							block.length - bytesRead);
					if (bytes > 0)
						// process audio block
						bytesRead += bytes;
				}

				if (bytesRead >= block.length) {
					monitor.sendMessageAndData(block, block.length, "audio ",
							call_session_token, " ",
							codec.codeString);
					bytesRead = 0;
				}

			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
		Log.d(TAG, "Releasing recorder and terminating");
		cleanup();
		audioThread = null;
	}

}
