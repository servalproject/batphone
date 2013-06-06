package org.servalproject.audio;

import java.util.Stack;

public class BufferList {
	private Stack<AudioBuffer> reuseList = new Stack<AudioBuffer>();
	private final int mtu;
	static final int DEFAULT_MTU = 1200;

	public BufferList() {
		this(DEFAULT_MTU);
	}

	public BufferList(int mtu) {
		this.mtu = mtu;
	}

	public synchronized AudioBuffer getBuffer() {
		AudioBuffer buff;
		if (reuseList.size() > 0) {
			buff = reuseList.pop();
			buff.clear();
		} else
			buff = new AudioBuffer(this, mtu);
		if (buff.inUse)
			throw new IllegalStateException();
		buff.inUse = true;
		return buff;
	}

	public synchronized void releaseBuffer(AudioBuffer buff) {
		if (!buff.inUse)
			throw new IllegalStateException();
		buff.inUse = false;
		reuseList.push(buff);
	}
}
