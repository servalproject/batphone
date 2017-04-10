package org.servalproject.audio;

import java.io.EOFException;
import java.io.IOException;

import org.servalproject.batphone.VoMP;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

public class AudioRecordStream implements Runnable {
	private final AudioRecord audioRecorder;
	private int samplesPerMs;
	private int frameSize;
	private final int audioBlockSize;
	private final int audioMaxAudioBlock;
	private AudioStream stream;
	private boolean stopped = false;
	private static final String TAG = "AudioRecordStream";

	public AudioRecordStream(AudioStream stream,
			int audioSource, int sampleRateInHz,
			int channelConfig, int audioFormat, int minimumBufferSize,
			int audioBlockSize, int audioMaxAudioBlock)
			throws IOException {

		this.stream = stream;
		this.audioBlockSize = audioBlockSize;
		this.audioMaxAudioBlock = audioMaxAudioBlock;
		this.samplesPerMs = sampleRateInHz / 1000;

		int frameSize = 0;
		if ((channelConfig & AudioFormat.CHANNEL_IN_MONO) != 0) {
			frameSize = 1;
			Log.v(TAG, "Mono");
		} else if ((channelConfig & AudioFormat.CHANNEL_IN_STEREO) != 0) {
			frameSize = 2;
			Log.v(TAG, "Stereo");
		}
		if (audioFormat == AudioFormat.ENCODING_PCM_16BIT)
			frameSize *= 2;
		this.frameSize = frameSize;

		int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz,
				channelConfig, audioFormat);

		// ensure minimum record buffer
		if (bufferSize < minimumBufferSize)
			bufferSize = minimumBufferSize;

		AudioRecord recorder = new AudioRecord(audioSource,
				sampleRateInHz, channelConfig, audioFormat, bufferSize);

		if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
			recorder.release();
			throw new IOException("Audio preparation failed");
		}

		this.audioRecorder = recorder;
	}

	public void setStream(AudioStream stream) {
		this.stream = stream;
	}

	public void close() {
		stopped = true;
	}


	@Override
	public void run() {
		try {
			Process.setThreadPriority(Process.THREAD_PRIORITY_LESS_FAVORABLE);
			Log.v(TAG, "Entering record thread");
			BufferList bufferList = new BufferList(audioMaxAudioBlock);

			AudioBuffer buff = null;
			int timestamp = 0;
			int sequence = 0;
			audioRecorder.startRecording();
			long readStarted = 0;

			while (!stopped) {
				if (buff == null) {
					buff = bufferList.getBuffer();
					buff.codec = VoMP.Codec.Signed16;
					buff.sampleStart = timestamp;
					buff.sequence = sequence++;
					readStarted = SystemClock.elapsedRealtime();
				}

				int readSize = audioBlockSize - (buff.dataLen % audioBlockSize);
				int read = audioRecorder
						.read(buff.buff, buff.dataLen, readSize);
				if (read < 0)
					throw new EOFException(
							"Reading from the audiorecorder, (byte["
									+ buff.buff.length + "], " + buff.dataLen
									+ ", " + readSize + ")=" + read + " ("
									+ audioBlockSize + ")");

				buff.dataLen += read;
				if (buff.dataLen % audioBlockSize == 0
						&& buff.dataLen > 0) {
					if (stream != null) {
						int readDuration = (int) (SystemClock.elapsedRealtime() - readStarted);
						if (readDuration > 20
								|| buff.dataLen >= buff.buff.length) {
							int sampleDuration = buff.dataLen
									/ (frameSize * samplesPerMs);
							timestamp += sampleDuration;
							stream.write(buff);
							buff = null;
						}
					} else {
						buff.dataLen = 0;
						readStarted = SystemClock.elapsedRealtime();
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
		Log.v(TAG, "Left record thread");
		audioRecorder.stop();
		audioRecorder.release();
	}

}
