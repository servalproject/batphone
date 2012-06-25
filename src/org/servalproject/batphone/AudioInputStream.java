package org.servalproject.batphone;

import java.io.IOException;
import java.io.InputStream;

import android.media.AudioRecord;

public class AudioInputStream extends InputStream {
	private final AudioRecord audioRecorder;

	public AudioInputStream(int audioSource, int sampleRateInHz,
			int channelConfig, int audioFormat, int minimumBufferSize)
			throws IOException {

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
		recorder.startRecording();

		this.audioRecorder = recorder;
	}

	@Override
	public void close() throws IOException {
		audioRecorder.stop();
		audioRecorder.release();
		skipBuffer = null;
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		return audioRecorder.read(buffer, offset, length);
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		return read(buffer, 0, buffer.length);
	}

	private byte skipBuffer[];
	@Override
	public long skip(long byteCount) throws IOException {
		if (byteCount > 1024)
			byteCount = 1024;
		if (skipBuffer == null || byteCount > skipBuffer.length)
			skipBuffer = new byte[(int) byteCount];
		return read(skipBuffer, 0, (int) byteCount);
	}

	@Override
	public int read() throws IOException {
		throw new IOException(getClass().getName()
				+ ".read() :\n\tDo not support simple read().");
	}

}
