package uk.co.mmscomputing.sound;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CompressInputStream extends FilterInputStream {

	/*
	 * Convert mono PCM byte stream into A-Law u-Law byte stream
	 *
	 * static AudioFormat alawformat= new
	 * AudioFormat(AudioFormat.Encoding.ALAW,8000,8,1,1,8000,false); static
	 * AudioFormat ulawformat= new
	 * AudioFormat(AudioFormat.Encoding.ULAW,8000,8,1,1,8000,false);
	 *
	 * PCM 8000.0 Hz, 16 bit, mono, SIGNED, little-endian static AudioFormat
	 * pcmformat = new AudioFormat(8000,16,1,true,false);
	 */

	static private Compressor alawcompressor = new ALawCompressor();
	static private Compressor ulawcompressor = new uLawCompressor();
	private byte workBuff[];

	private Compressor compressor = null;

	public CompressInputStream(InputStream in, boolean useALaw)
			throws IOException {
		super(in);
		compressor = (useALaw) ? alawcompressor : ulawcompressor;
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
		int i, sample;

		int buffSize = len << 1;
		if (workBuff == null || workBuff.length < buffSize)
			workBuff = new byte[buffSize];
		len = in.read(workBuff);
		if (len == -1)
			return -1;

		i = 0;
		while (i < len) {
			sample = (workBuff[i++] & 0x00FF);
			sample |= (workBuff[i++] << 8);
			b[off++] = (byte) compressor.compress((short) sample);
		}
		return len >> 1;
	}

	@Override
	public void close() throws IOException {
		workBuff = null;
		super.close();
	}
}