package org.servalproject.system.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;
import android.util.Log;

import java.io.EOFException;
import java.io.InputStream;

/**
* Created by jeremy on 7/04/15.
*/
class PeerReader implements Runnable, Comparable<PeerReader>{
	private final BlueToothControl control;
	public final boolean secure;
	public final BluetoothSocket socket;
	private final PeerState peer;
	private final int bias;
	private Thread thread;
	public final String name;
	long lastReceived;
	long lastWritten;

	private static int __id=0;
	PeerReader(BlueToothControl control, PeerState peer, BluetoothSocket socket, boolean secure, int bias){
		name = "PeerReader" + (__id++);
		this.control = control;
		this.peer = peer;
		this.socket = socket;
		this.secure = secure;
		this.bias = bias;
		lastReceived = SystemClock.elapsedRealtime();
	}

	public void start(){
		if (thread!=null)
			return;
		thread = new Thread(this, "Reader"+peer.device.getAddress());
		thread.start();
	}

	public boolean isRunning(){
		return thread!=null;
	}

	@Override
	public void run() {
		try {
			InputStream in = socket.getInputStream();
			byte buff[] = new byte[BlueToothControl.MTU+2];
			int offset=0;
			while(thread == Thread.currentThread()){
				if (offset>=2){
					int msgLen = (buff[0]&0xFF) | ((buff[1]&0xFF) << 8);
					if (msgLen > buff.length -2 || msgLen<0)
						throw new IllegalStateException(msgLen+" is greater than the link MTU");
					if (offset >= msgLen+2) {
						control.receivedPacket(peer.addrBytes, buff, 2, msgLen);
						if (offset > msgLen+2)
							System.arraycopy(buff, msgLen+2, buff, 0, offset - (msgLen + 2));
						offset -= msgLen+2;
						continue;
					}
				}
				int len = in.read(buff, offset, buff.length - offset);
				if (len<0)
					throw new EOFException();
				lastReceived = SystemClock.elapsedRealtime();
				offset+=len;
			}
		}catch (Exception e){
			Log.e(name, e.getMessage(), e);
		}
		if (thread==Thread.currentThread())
			thread = null;

		peer.onClosed(this);
	}

	@Override
	public int compareTo(PeerReader peerReader) {
		if (peerReader == this)
			return 0;
		if (this.bias + this.lastReceived < peerReader.bias + peerReader.lastReceived)
			return -1;
		return 1;
	}
}
