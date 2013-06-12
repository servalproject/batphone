package org.servalproject.audio;

import org.servalproject.batphone.VoMP;

public class Codec2 extends Codec {
	private BufferList encodeBuffers;
	private BufferList decodeBuffers;
	private final VoMP.Codec codec;

	private static final String TAG = "Codec2";
	private long ptr;

	private native long init(int mode);

	private native void release(long ptr);

	private native int encode(long ptr, byte in[], byte out[]);

	private native int decode(long ptr, byte in[], byte out[]);

	static {
		System.loadLibrary("codec2");
	}

	public Codec2(VoMP.Codec codec) {

		int mode = 0;

		switch (codec) {
		case Codec2_1200:
			mode = 1200;
			break;
		case Codec2_3200:
			mode = 3200;
			break;
		}

		this.ptr = init(mode);
		this.codec = codec;
		encodeBuffers = new BufferList(10);
		decodeBuffers = new BufferList(codec.audioBufferSize());
	}

	@Override
	public void close() {
		if (ptr != 0) {
			release(ptr);
			ptr = 0;
		}
	}

	@Override
	public AudioBuffer encode(AudioBuffer source) {
		AudioBuffer ret = encodeBuffers.getBuffer();
		ret.copyFrom(source);
		ret.codec = this.codec;
		ret.dataLen = encode(ptr, source.buff, ret.buff);
		if (ret.dataLen < 0)
			throw new IllegalStateException("Failed to encode audio");
		return ret;
	}

	@Override
	public AudioBuffer decode(AudioBuffer source) {
		AudioBuffer ret = decodeBuffers.getBuffer();
		ret.copyFrom(source);
		ret.codec = VoMP.Codec.Signed16;
		ret.dataLen = decode(ptr, source.buff, ret.buff);
		if (ret.dataLen < 0)
			throw new IllegalStateException("Failed to decode audio");
		return ret;
	}

	@Override
	public int sampleLength(AudioBuffer buff) {
		switch (codec) {
		case Codec2_1200:
			return 320;
		}
		return super.sampleLength(buff);
	}
}
