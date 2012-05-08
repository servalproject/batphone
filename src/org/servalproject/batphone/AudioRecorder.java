package org.servalproject.batphone;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.ServalDMonitor;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioRecorder implements Runnable {

	private AudioRecord audioRecorder;
	private short[] recordBuffer;
	boolean recordingP = true;
	boolean stopMe = false;
	String call_session_token = null;

	int codec = VoMP.VOMP_CODEC_PCM;
	int codecTimespan = VoMP.vompCodecTimespan(codec);
	int codecBlockBytes = VoMP.vompCodecBlockSize(codec);

	public AudioRecorder(String token) {
		call_session_token = token;
	}

	@Override
	public synchronized void run() {
		ServalBatPhoneApplication.context.audioRecorder = this;

		if (audioRecorder == null) {
			int sampleRate = 8000;
			int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
					AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT);
			audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
					8000,
					AudioFormat.CHANNEL_CONFIGURATION_MONO,
					AudioFormat.ENCODING_PCM_16BIT, bufferSize);
			Log.d("VoMPRecorder", "Minimum record buffer is " + bufferSize
					+ " = "
					+ (bufferSize * 1.0 / sampleRate) + " seconds.");
			// But make our buffer much larger than the minimum
			// Actually, don't, because using an "unsupported buffer size" can
			// make
			// it bomb
			// (gadzooks, android audio is bad!)
			recordBuffer = new short[bufferSize];
		}

		// get one block of audio at a time.
		// get in byte[], even though samples are natively short[],
		// as it ends up being more efficient when we hand them to
		// a codec or just send them as raw PCM
		int bytesRead = 0;
		int blockSamples=codecTimespan*8; // 8khz sample rate, and timespan in ms
		int blockBytes = blockSamples * 2;
		byte[] block = new byte[blockSamples * 2];
		Log.d("VoMPRecorder", "Starting loop");
		while (!stopMe) {
			if (recordingP) {
				if (audioRecorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
					Log.d("VoMPRecorder",
							"Asking audioRecorder to start recording");
					audioRecorder.startRecording();
				}
				int bytes = audioRecorder.read(block, bytesRead,
						block.length
								- bytesRead);
				if (bytes > 0) {
					// process audio block
					bytesRead += bytes;
					if (bytesRead >= blockBytes)
					{
						processBlock(block);
						bytesRead = 0;
					}

				} else
					Log.d("VoMPRecorder", "Read returned emtpy");
			} else {
				if (audioRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
					audioRecorder.stop();
				Log.d("VoMPRecorder", "Not recording");
				try {
					Thread.sleep(100);
				} catch (Exception e) { // doesn't matter if we get interrupted
										// while sleeping
				}
			}
		}
		Log.d("VoMPRecorder", "Releasing recorder and terminating");
		audioRecorder.release();
		audioRecorder = null;
		ServalBatPhoneApplication.context.audioRecorder = null;
	}

	int counter = 0;
	private void processBlock(byte[] block) {
		// send block to servald via monitor interface
		ServalDMonitor m = ServalBatPhoneApplication.context.servaldMonitor;
		if (m == null)
			return;
		// only send the occassional packet to help aid debugging
		// if ((counter & 0x7) == 0)
			m.sendMessageAndData("AUDIO:" + call_session_token + ":" + codec,
					block);
		counter++;
	}

	public void done() {
		stopMe = true;
		return;
	}

}
