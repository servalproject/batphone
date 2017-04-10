package org.servalproject.audio;

import java.util.Stack;

public class BufferList {
	private Stack<AudioBuffer> reuseList = new Stack<AudioBuffer>();
	public final int mtu;
	// enough space for 16bit, 120ms @ 8KHz
	static final int DEFAULT_MTU = 2 * 120 * 8;

	public BufferList() {
		this(DEFAULT_MTU);
	}

	public BufferList(int mtu) {
		this.mtu = mtu;
	}

	public AudioBuffer getBuffer() {
		AudioBuffer buff = null;
		synchronized (reuseList) {
			if (reuseList.size() > 0)
				buff = reuseList.pop();
		}
		if (buff == null)
			buff = new AudioBuffer(this, mtu);
		else
			buff.clear();

		if (buff.inUse)
			throw new IllegalStateException();
		buff.inUse = true;
		return buff;
	}

	public void releaseBuffer(AudioBuffer buff) {
		if (!buff.inUse)
			throw new IllegalStateException();
		buff.inUse = false;
		synchronized (reuseList) {
			reuseList.push(buff);
		}
	}
}
