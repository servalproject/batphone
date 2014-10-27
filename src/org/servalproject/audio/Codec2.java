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

	private native int encode(long ptr, int dataSize, byte in[], byte out[]);

	private native int decode(long ptr, int dataSize, byte in[], byte out[]);

	static {
		System.loadLibrary("servalcodec2");
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
		// maximum size of 120ms of encoded audio is 64 bits per frame for 6
		// frames
		encodeBuffers = new BufferList(8 * 6);
		decodeBuffers = new BufferList(codec.maxBufferSize());
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
		ret.dataLen = encode(ptr, source.dataLen, source.buff, ret.buff);
		if (ret.dataLen < 0)
			throw new IllegalStateException("Failed to encode audio ("
					+ source.dataLen + ", " + source.buff.length + ", "
					+ ret.buff.length + ") = " + ret.dataLen);
		return ret;
	}

	@Override
	public AudioBuffer decode(AudioBuffer source) {
		AudioBuffer ret = decodeBuffers.getBuffer();
		ret.copyFrom(source);
		ret.codec = VoMP.Codec.Signed16;
		ret.dataLen = decode(ptr, source.dataLen, source.buff, ret.buff);
		if (ret.dataLen < 0)
			throw new IllegalStateException("Failed to decode audio ("
					+ source.dataLen + ", " + source.buff.length + ", "
					+ ret.buff.length + ") = " + ret.dataLen);
		return ret;
	}

	@Override
	public int sampleLength(AudioBuffer buff) {
		switch (codec) {
		case Codec2_1200:
			return (buff.dataLen / 6) * 320;
		case Codec2_3200:
			return (buff.dataLen / 8) * 160;
		}
		return super.sampleLength(buff);
	}
}
