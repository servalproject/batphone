package org.servalproject.servald;

import java.io.IOException;
import java.io.InputStream;

import android.util.Log;

public class DumpInputStream extends InputStream {
	private final InputStream in;

	public DumpInputStream(InputStream in) {
		this.in = in;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	private int pos = 0;
	private StringBuilder hex = new StringBuilder();
	private StringBuilder printable = new StringBuilder();
	private String line = "0000";

	private void log(byte value) {
		hex.append(Integer.toHexString(value & 0xFF)).append(' ');
		if (value >= 32 && value <= 128)
			printable.append((char) value);
		else
			printable.append('.');
		pos++;
		if ((pos % 16) == 0 || value == '\n') {
			Log.v("StreamDump", line +
					" [" + printable.toString() + "] [" + hex.toString() + "]");
			hex.setLength(0);
			printable.setLength(0);
			if (value == '\n')
				pos = 0;
			line = Integer.toHexString(pos);
		}
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		int ret = in.read(buffer, offset, length);
		for (int i = 0; i < ret; i++)
			log(buffer[offset + i]);
		return ret;
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		return this.read(buffer, 0, buffer.length);
	}

	@Override
	public int read() throws IOException {
		int ret = in.read();
		if (ret >= 0)
			log((byte) ret);
		return ret;
	}

}
