package org.servalproject.audio;

import java.io.IOException;
import java.io.OutputStream;

import uk.co.mmscomputing.sound.CodecOutputStream;
import android.media.AudioFormat;
import android.media.AudioTrack;

public class AudioOutputStream extends OutputStream implements
		CodecOutputStream {
	private final AudioTrack audioTrack;
	private int writtenFrames = 0;
	private final int frameSize;
	private final Oslec echoCanceller;
	public final int bufferSize;
	private byte silence[];

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

		// don't seed the echo canceler until we've forced the buffer to fill
		// and start playing once, it can throw off our timing.
		int written = 0;
		while (written < silence.length) {
			int ret = audioTrack.write(silence, written, silence.length
					- written);
			if (ret < 0)
				break;
			written += ret;
			writtenFrames += ret / this.frameSize;
		}
	}

	public int writtenAudio() {
		return this.writtenFrames;
	}
	public int unplayedFrameCount() {
		return this.writtenFrames - this.audioTrack.getPlaybackHeadPosition();
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

	private void writeAll(byte buffer[], int offset, int count)
			throws IOException {
		int written = 0;
		while (written < count) {
			int ret = audioTrack.write(buffer, offset + written, count
					- written);
			if (ret < 0)
				throw new IOException();
			written += ret;
			writtenFrames += ret / this.frameSize;
		}
	}

	@Override
	public void write(byte[] buffer, int offset, int count) throws IOException {
		int written = 0;

		while (written < count) {
			int blockSize = count - written;
			if (blockSize > Oslec.BLOCK_SIZE)
				blockSize = Oslec.BLOCK_SIZE;

			if (echoCanceller != null) {
				echoCanceller.txAudio(
						buffer, offset + written, blockSize);
			}
			writeAll(buffer, offset + written, blockSize);
			written += blockSize;
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

	@Override
	public int sampleDurationFrames(byte[] buffer, int offset, int count) {
		return count / this.frameSize;
	}

}
