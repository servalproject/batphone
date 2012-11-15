package uk.co.mmscomputing.sound;

import java.io.IOException;

public interface CodecOutputStream {

	public void close() throws IOException;

	public void write(byte[] buffer, int offset, int count)
			throws IOException;

	public void write(byte[] buffer) throws IOException;

	public int sampleDurationFrames(byte buffer[], int offset, int count);
}