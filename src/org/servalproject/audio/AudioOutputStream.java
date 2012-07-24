package org.servalproject.audio;

import java.io.IOException;
import java.io.OutputStream;

import android.media.AudioFormat;
import android.media.AudioTrack;

public class AudioOutputStream extends OutputStream {
	private final AudioTrack audioTrack;
	private int writtenFrames = 0;
	private final int frameSize;
	private final Oslec echoCanceller;
	public final int bufferSize;
	private byte silence[];
	private byte echoBuffer[];

	public AudioOutputStream(Oslec echoCanceller, int streamType,
			int sampleRateInHz,
			int channelConfig, int audioFormat, int minimumBufferSize)
			throws IOException {
		int bufferSize = AudioTrack.getMinBufferSize(sampleRateInHz,
				channelConfig, audioFormat);

		int frameSize = 0;
		if ((channelConfig & AudioFormat.CHANNEL_OUT_MONO) != 0)
			frameSize = 1;
		else if ((channelConfig & AudioFormat.CHANNEL_OUT_STEREO) != 0)
			frameSize = 2;
		if (audioFormat == AudioFormat.ENCODING_PCM_16BIT)
			frameSize *= 2;

		if (frameSize == 0)
			throw new IOException("Unable to determine audio frame size");

		this.frameSize = frameSize;

		// ensure 60ms minimum playback buffer
		if (bufferSize < minimumBufferSize)
			bufferSize = minimumBufferSize;

		audioTrack = new AudioTrack(
				streamType,
				sampleRateInHz,
				channelConfig,
				audioFormat,
				bufferSize,
				AudioTrack.MODE_STREAM);

		this.bufferSize = bufferSize;
		this.echoCanceller = echoCanceller;
		silence = new byte[bufferSize];
	}

	@Override
	public void close() throws IOException {
		audioTrack.stop();
		audioTrack.release();
	}

	public void play() {
		this.audioTrack.play();
	}

	public int writtenAudio() {
		return this.writtenFrames;
	}
	public int unplayedFrameCount() {
		return this.writtenFrames - this.audioTrack.getPlaybackHeadPosition();
	}

	public void fillSilence() throws IOException {
		write(silence);
	}

	public void writeSilence(int timeInFrames) throws IOException {
		int silenceDataLength = timeInFrames * frameSize;
		while (silenceDataLength > 0) {
			int len = silenceDataLength > silence.length ? silence.length
					: silenceDataLength;
			write(silence, 0, len);
			silenceDataLength -= len;
		}
	}

	@Override
	public void write(byte[] buffer, int offset, int count) throws IOException {
		int written = 0;

		if (echoBuffer==null || echoBuffer.length<count)
			echoBuffer = new byte[count];

		if (echoCanceller != null) {
			echoCanceller.process(buffer, offset, count, echoBuffer);
			buffer = echoBuffer;
			offset = 0;
		}

		while (written < count) {
			int ret = audioTrack.write(buffer, offset + written, count
					- written);
			if (ret < 0)
				break;
			written += ret;
			writtenFrames += ret / this.frameSize;
		}
	}

	@Override
	public void write(byte[] buffer) throws IOException {
		this.write(buffer, 0, buffer.length);
	}

	@Override
	public void write(int oneByte) throws IOException {
		throw new IOException(getClass().getName()
				+ ".write(int) :\n\tDo not support simple write().");
	}

}
