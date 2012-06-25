package uk.co.mmscomputing.sound;

public abstract class Decompressor {
	private final int[] table;

	Decompressor(int[] table) {
		this.table = table;
	}

	protected int decompress(byte sample) {
		return table[sample & 0x00FF];
	}
}
