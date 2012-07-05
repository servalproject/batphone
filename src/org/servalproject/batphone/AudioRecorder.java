package org.servalproject.batphone;

import java.io.IOException;
import java.io.InputStream;

import org.servalproject.servald.ServalDMonitor;

import uk.co.mmscomputing.sound.CompressInputStream;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioRecorder implements Runnable {
	static final String TAG = "AudioRecorder";

	boolean stopMe = false;
	final String call_session_token;
	Thread audioThread;
	ServalDMonitor monitor;
	// AudioRecord audioRecorder;

	InputStream audioInput;
	InputStream codecInput;
	VoMP.Codec codec = null;

	boolean discard = false;

	public AudioRecorder(String token, ServalDMonitor monitor) {
		call_session_token = token;
		this.monitor = monitor;
	}

	public synchronized void startRecording(VoMP.Codec codec)
			throws IOException {
		Log.v(TAG, "Start recording " + codec);

		if (this.codec != null)
			throw new IOException("Recording already started");

		if (audioThread == null)
			throw new IOException("Audio recording has not been prepared");

		switch (codec) {
		case Pcm:
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
		audioInput = new AudioInputStream(MediaRecorder.AudioSource.MIC,
				8000,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				8 * 60 * 2);
	}

	private void cleanup() {
		if (audioInput == null)
			return;

		try {
			audioInput.close();
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

		// Note we aren't boosting the priority of this thread. If we run out of
		// CPU power, the first thing that should suffer is the recording so we
		// don't make the problem worse
		while (!stopMe) {
			try {
				if (discard || codec == null) {
					// skip 20ms of audio at a time until we know the codec
					// we are going to use
					audioInput.skip(320);
					continue;
				}

				if (block == null) {
					Log.v(TAG, "Starting to read audio in " + codec.blockSize
							+ " byte blocks");
					block = new byte[codec.blockSize];
				}

				if (bytesRead < block.length) {
					int bytes = codecInput.read(block, bytesRead,
							block.length - bytesRead);
					if (bytes > 0)
						// process audio block
						bytesRead += bytes;
				}

				if (bytesRead >= block.length) {
					monitor.sendMessageAndData(block, block.length, "AUDIO:",
							call_session_token, ":",
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
