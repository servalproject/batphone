package org.servalproject.audio;

public abstract class Codec {
	public abstract void close();

	public abstract AudioBuffer encode(AudioBuffer source);

	public abstract AudioBuffer decode(AudioBuffer source);

	// how many samples are in this compressed buffer?
	public int sampleLength(AudioBuffer buff) {
		AudioBuffer out = decode(buff);
		try {
			return out.dataLen / 2;
		} finally {
			out.release();
		}
	}

	// if this codec can mask missing audio, generate some
	public AudioBuffer decode_missing(int duration) {
		return null;
	}
}
