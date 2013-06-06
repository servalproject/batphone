package org.servalproject.audio;

import java.io.IOException;


public interface AudioStream {

	public void close() throws IOException;

	public int write(AudioBuffer buff)
			throws IOException;

	public int sampleDurationFrames(AudioBuffer buff);
}