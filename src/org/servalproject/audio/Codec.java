package org.servalproject.audio;

public abstract class Codec {
	public abstract void close();

	public abstract AudioBuffer encode(AudioBuffer source);

	public abstract AudioBuffer decode(AudioBuffer source);

	public int sampleLength(AudioBuffer buff) {
		AudioBuffer out = decode(buff);
		try {
			return out.dataLen / 2;
		} finally {
			out.release();
		}
	}
}
