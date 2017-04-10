package uk.co.mmscomputing.sound;

public class ALawCompressor extends Compressor {

	static final int cClip = 32635;

	static final int[] ALawCompressTable = {
			1, 1, 2, 2, 3, 3, 3, 3,
			4, 4, 4, 4, 4, 4, 4, 4,
			5, 5, 5, 5, 5, 5, 5, 5,
			5, 5, 5, 5, 5, 5, 5, 5,
			6, 6, 6, 6, 6, 6, 6, 6,
			6, 6, 6, 6, 6, 6, 6, 6,
			6, 6, 6, 6, 6, 6, 6, 6,
			6, 6, 6, 6, 6, 6, 6, 6,
			7, 7, 7, 7, 7, 7, 7, 7,
			7, 7, 7, 7, 7, 7, 7, 7,
			7, 7, 7, 7, 7, 7, 7, 7,
			7, 7, 7, 7, 7, 7, 7, 7,
			7, 7, 7, 7, 7, 7, 7, 7,
			7, 7, 7, 7, 7, 7, 7, 7,
			7, 7, 7, 7, 7, 7, 7, 7,
			7, 7, 7, 7, 7, 7, 7, 7
	};

	@Override
	protected int compress(short sample) {
		int sign;
		int exponent;
		int mantissa;
		int compressedByte;

		sign = ((~sample) >> 8) & 0x80;
		if (sign == 0) {
			sample *= -1;
		}
		if (sample > cClip) {
			sample = cClip;
		}
		if (sample >= 256) {
			exponent = ALawCompressTable[(sample >> 8) & 0x007F];
			mantissa = (sample >> (exponent + 3)) & 0x0F;
			compressedByte = 0x007F & ((exponent << 4) | mantissa);
		} else {
			compressedByte = 0x007F & (sample >> 4);
		}
		compressedByte ^= (sign ^ 0x55);
		return compressedByte;
	}
}