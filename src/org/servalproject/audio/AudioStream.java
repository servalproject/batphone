package org.servalproject.audio;

import java.io.IOException;


public abstract class AudioStream {

	public void close() throws IOException {

	}

	public int getBufferDuration() {
		return 0;
	}

	public void missed(int duration, boolean missing) throws IOException {

	}

	public abstract int write(AudioBuffer buff)
			throws IOException;

	public int sampleDurationMs(AudioBuffer buff) {
		return -1;
	}
}