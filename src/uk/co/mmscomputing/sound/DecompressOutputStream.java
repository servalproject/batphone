package uk.co.mmscomputing.sound;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DecompressOutputStream extends FilterOutputStream {
	private final Decompressor decompressor;
	private byte workBuff[];

	public DecompressOutputStream(OutputStream out, boolean useALaw) {
		super(out);
		this.decompressor = (useALaw) ? new ALawDecompressor()
				: new ULawDecompressor();
	}

	@Override
	public void close() throws IOException {
		super.close();
		workBuff = null;
	}

	@Override
	public void write(byte[] buffer, int offset, int count)
			throws IOException {
		if (count <= 0)
			return;

		int buffSize = count << 1;
		if (workBuff == null || workBuff.length < buffSize)
			workBuff = new byte[buffSize];

		decompressor.decompress(buffer, offset, count, workBuff, 0);
		out.write(workBuff, 0, buffSize);
	}

	@Override
	public void write(byte[] buffer) throws IOException {
		this.write(buffer, 0, buffer.length);
	}

	@Override
	public void write(int arg0) throws IOException {
		throw new IOException(getClass().getName()
				+ ".write(int) :\n\tDo not support simple write().");
	}

}
