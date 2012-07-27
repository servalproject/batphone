package org.servalproject.audio;

import java.io.IOException;
import java.io.InputStream;

import android.media.AudioRecord;
import android.os.SystemClock;

public class AudioInputStream extends InputStream {
	private final AudioRecord audioRecorder;
	private final Oslec echoCanceller;
	private long lastReadTime;
	private long burstOffset;
	private long bytesRead;
	private int buffSize = 2048;


	public AudioInputStream(Oslec echoCanceller, int audioSource,
			int sampleRateInHz,
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
		this.echoCanceller = echoCanceller;
	}

	@Override
	public void close() throws IOException {
		audioRecorder.stop();
		audioRecorder.release();
		skipBuffer = null;
	}

	private void bytesRead(int read) {
		if (read < 0)
			return;
		long now = SystemClock.elapsedRealtime();
		if (lastReadTime > 0) {
			long dt = now - lastReadTime;
			if (dt > 30) {
				// assume that any read that takes >=30ms represents a hardware
				// buffer flush.
				// lets try to work out how big that buffer is so we can
				// estimate the record latency
				buffSize = (int) (bytesRead - burstOffset);

				burstOffset = bytesRead;
			}

		}

		bytesRead += read;
		lastReadTime = now;
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		int read = 0;
		while (read < length) {
			int block = (length - read) > Oslec.BLOCK_SIZE ? Oslec.BLOCK_SIZE
					: (length - read);
			int ret = audioRecorder.read(buffer, offset + read, block);
			if (ret < 0) {
				if (read == 0)
					read = ret;
				break;
			}

			bytesRead(ret);
			if (echoCanceller != null){
				// calculate the lag we know about, based on our current
				// position reading this burst.
				int posInBurst = (int)(bytesRead - ret - burstOffset);
				int lagInMs = (buffSize - posInBurst) / 16;
				echoCanceller.rxAudio(buffer, offset + read, ret, lagInMs);
			}
			read += ret;
		}
		return read;
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
