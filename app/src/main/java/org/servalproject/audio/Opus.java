package org.servalproject.audio;

import org.servalproject.batphone.VoMP;

public class Opus extends Codec {

	private long encoderState = 0;
	private long decoderState = 0;
	private BufferList encoderBuffers;
	private BufferList decoderBuffers;

	private native long encodercreate(int sampleRate);

	private native void encoderbitrate(long ptr, int bitRate);

	private native void encodercomplexity(long ptr, int complexity);

	private native int encode(long ptr, int data_size, byte in[], byte out[]);

	private native void encoderdestroy(long ptr);

	private native long decodercreate(int sampleRate);

	private native int decode(long ptr, int data_size, byte in[],
			int output_size, byte out[]);

	private native void decoderdestroy(long ptr);

	static {
		System.loadLibrary("servalopus");
	}

	@Override
	public void close() {
		if (encoderState != 0)
			encoderdestroy(encoderState);
		if (decoderState != 0)
			decoderdestroy(decoderState);
		encoderBuffers = null;
		decoderBuffers = null;
	}

	@Override
	public AudioBuffer encode(AudioBuffer source) {
		if (encoderState == 0) {
			encoderState = encodercreate(VoMP.Codec.Opus.sampleRate);
			encoderBuffers = new BufferList(360);
			// TODO choose bitrate & complexity
		}
		AudioBuffer out = encoderBuffers.getBuffer();
		out.copyFrom(source);
		out.codec = VoMP.Codec.Opus;
		out.dataLen = encode(encoderState, source.dataLen, source.buff,
				out.buff);
		if (out.dataLen < 0)
			throw new IllegalStateException("opus encode (@0x"
					+ Long.toHexString(encoderState) + ", " + source.dataLen
					+ ", byte[" + source.buff.length + "], byte["
					+ out.buff.length + "])=" + out.dataLen);
		if (out.dataLen == 1) {
			out.release();
			return null;
		}
		return out;
	}

	@Override
	public AudioBuffer decode(AudioBuffer source) {
		if (decoderState == 0) {
			decoderState = decodercreate(VoMP.Codec.Opus.sampleRate);
			decoderBuffers = new BufferList(VoMP.Codec.Opus.maxBufferSize());
		}
		AudioBuffer out = decoderBuffers.getBuffer();
		out.copyFrom(source);
		out.codec = VoMP.Codec.Signed16;
		out.dataLen = decode(decoderState, source.dataLen, source.buff, 0,
				out.buff);
		if (out.dataLen < 0)
			throw new IllegalStateException("opus decode (@0x"
					+ Long.toHexString(decoderState) + ", " + source.dataLen
					+ ", byte[" + source.buff.length + "], 0, byte["
					+ out.buff.length + "])=" + out.dataLen);
		return out;
	}

	@Override
	public AudioBuffer decode_missing(int duration) {
		AudioBuffer out = decoderBuffers.getBuffer();
		out.codec = VoMP.Codec.Signed16;
		int bufferSize = duration * 2 * (VoMP.Codec.Opus.sampleRate / 1000);
		out.dataLen = decode(decoderState, 0, null, bufferSize,
				out.buff);
		if (out.dataLen < 0)
			throw new IllegalStateException("opus decode (@0x"
					+ Long.toHexString(decoderState) + ", 0, null, "
					+ bufferSize + ", byte["
					+ out.buff.length + "])=" + out.dataLen);
		return out;
	}
}
