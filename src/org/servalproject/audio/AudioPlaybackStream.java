package org.servalproject.audio;

import java.io.IOException;

import org.servalproject.batphone.VoMP;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

public class AudioPlaybackStream implements AudioStream {
	private final AudioTrack audioTrack;
	private int writtenFrames = 0;
	private final int frameSize;
	public final int bufferSize;
	public final int samplesPerMs;
	private byte silence[];

	public AudioPlaybackStream(int streamType,
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
		this.samplesPerMs = sampleRateInHz / 1000;
		Log.v("AudioPlayback", "Framesize " + frameSize + " Samples per ms "
				+ samplesPerMs);
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

	public void writeSilence(int timeInMs) throws IOException {
		int silenceDataLength = timeInMs * frameSize * samplesPerMs;
		while (silenceDataLength > 0) {
			int len = silenceDataLength > silence.length ? silence.length
					: silenceDataLength;
			writeAll(silence, 0, len);
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
	public int write(AudioBuffer buff) throws IOException {
		try {
			if (buff.codec != VoMP.Codec.Signed16)
				throw new IOException("Unsupported codec " + buff.codec);
			writeAll(buff.buff, 0, buff.dataLen);
			int ret = buff.dataLen / (this.frameSize * this.samplesPerMs);
			return ret;
		} finally {
			buff.release();
		}
	}

	@Override
	public int sampleDurationFrames(AudioBuffer buff) {
		return buff.dataLen / this.frameSize;
	}

}
