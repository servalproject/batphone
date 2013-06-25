package org.servalproject.audio;

import org.servalproject.batphone.VoMP;

public class AudioBuffer implements Comparable<AudioBuffer> {
	public VoMP.Codec codec;
	public final byte buff[];
	public int dataLen;
	public int sampleStart;
	public int sequence;
	public long received;
	public int thisDelay;
	public final BufferList bufferList;
	public boolean inUse = false;

	AudioBuffer(BufferList list, int mtu) {
		this.bufferList = list;
		this.buff = new byte[mtu];
	}

	public void copyFrom(AudioBuffer other) {
		this.sampleStart = other.sampleStart;
		this.sequence = other.sequence;
		this.received = other.received;
		this.thisDelay = other.thisDelay;
	}

	public void release() {
		this.bufferList.releaseBuffer(this);
	}

	@Override
	public int compareTo(AudioBuffer arg0) {
		if (0 < arg0.sampleStart - this.sampleStart)
			return -1;
		else if (this.sampleStart == arg0.sampleStart)
			return 0;
		return 1;
	}

	public void clear() {
		this.dataLen = 0;
		this.codec = null;
	}
}