package uk.co.mmscomputing.sound;

public abstract class Decompressor {
	private final int[] table;

	Decompressor(int[] table) {
		this.table = table;
	}

	public final void decompress(byte in[], int offset, int count,
			byte out[], int outOffset) {
		for (int i = 0; i < count; i++) {
			int value = table[in[i + offset] & 0x00FF];
			out[outOffset++] = (byte) ((value >> 8) & 0x00FF); // little-endian
			out[outOffset++] = (byte) (value & 0x00FF);
		}
	}
}
