package uk.co.mmscomputing.sound;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DecompressInputStream extends FilterInputStream {

	/*
	 * Convert A-Law or u-Law byte stream into mono PCM byte stream
	 *
	 * static AudioFormat alawformat= new
	 * AudioFormat(AudioFormat.Encoding.ALAW,8000,8,1,1,8000,false); static
	 * AudioFormat ulawformat= new
	 * AudioFormat(AudioFormat.Encoding.ULAW,8000,8,1,1,8000,false);
	 *
	 * PCM 8000.0 Hz, 16 bit, mono, SIGNED, little-endian static AudioFormat
	 * pcmformat = new AudioFormat(8000,16,1,true,false);
	 */

	/*
	 * Mathematical Tools in Signal Processing with C++ and Java Simulations by
	 * Willi-Hans Steeb International School for Scientific Computing
	 */

	private final Decompressor decompressor;
	public DecompressInputStream(InputStream in, boolean useALaw)
			throws IOException {
		super(in);
		decompressor = (useALaw) ? new ALawDecompressor()
				: new ULawDecompressor();
	}

	@Override
	public int read() throws IOException {
		throw new IOException(getClass().getName()
				+ ".read() :\n\tDo not support simple read().");
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		byte[] inb;

		inb = new byte[len >> 1]; // get A-Law or u-Law bytes
		len = in.read(inb);
		if (len == -1) {
			return -1;
		}

		decompressor.decompress(inb, 0, len, b, off);
		return len << 1;
	}
}