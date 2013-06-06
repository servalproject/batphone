package uk.co.mmscomputing.sound;

public abstract class Compressor {
	protected abstract int compress(short sample);

	public final void compress(byte in[], int offset, int len, byte out[],
			int outOffset) {
		int i = 0;
		while (i < len) {
			int sample = (in[offset + i++] & 0x00FF);
			sample |= (in[offset + i++] << 8);
			out[outOffset++] = (byte) compress((short) sample);
		}
	}
}