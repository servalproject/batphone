package org.servalproject.audio;

import java.io.IOException;

import org.servalproject.batphone.VoMP;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class AudioPlaybackStream extends AudioStream {
	private final AudioTrack audioTrack;
	private int writtenFrames = 0;
	private final int frameSize;
	public final int bufferSize;
	public final int samplesPerMs;
	private byte silence[];
	private AudioManager am;

	public AudioPlaybackStream(AudioManager am,
			int streamType, int sampleRateInHz,
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

		this.am = am;

		audioTrack = new AudioTrack(
				streamType,
				sampleRateInHz,
				channelConfig,
				audioFormat,
				bufferSize,
				AudioTrack.MODE_STREAM);

		this.bufferSize = bufferSize;
		silence = new byte[bufferSize];
		writeSilence(silence.length);
		this.audioTrack.play();
	}

	@Override
	public void close() throws IOException {
		audioTrack.stop();
		audioTrack.release();
	}

	private void writeSilence(int bytes) throws IOException {
		while (bytes > 0) {
			int len = bytes > silence.length ? silence.length
					: bytes;
			writeAll(silence, 0, len);
			bytes -= len;
		}
	}

	public int writtenAudio() {
		return this.writtenFrames;
	}

	@Override
	public int getBufferDuration() {
		return (this.writtenFrames - this.audioTrack.getPlaybackHeadPosition())
				/ samplesPerMs;
	}

	@Override
	public void missed(int duration, boolean missing) throws IOException {
		int silenceDataLength = duration * frameSize * samplesPerMs;
		writeSilence(silenceDataLength);
	}

	private void writeAll(byte buffer[], int offset, int count)
			throws IOException {
		int written = 0;
		while (written < count) {
			int ret = audioTrack.write(buffer, offset + written, count
					- written);
			if (ret < 0)
				throw new IOException("Failed to write audio; write(["
						+ buffer.length + "], " + (offset + written) + ", "
						+ (count - written) + ")=" + ret);
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
}
