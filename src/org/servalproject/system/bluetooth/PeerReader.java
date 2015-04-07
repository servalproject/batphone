package org.servalproject.system.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.EOFException;
import java.io.InputStream;

/**
* Created by jeremy on 7/04/15.
*/
class PeerReader implements Runnable{
	private final BlueToothControl control;
	private final BluetoothSocket socket;
	private final PeerState peer;
	private boolean running = true;
	private Thread thread;
	private static final String TAG="PeerReader";

	PeerReader(BlueToothControl control, PeerState peer){
		this.control = control;
		this.socket = peer.socket;
		this.peer = peer;
	}

	public void close(){
		if (!running)
			return;
		running = false;
		Thread t = thread;
		if (t!=null)
			t.interrupt();
	}

	@Override
	public void run() {
		thread = Thread.currentThread();
		try {
			InputStream in = socket.getInputStream();
			byte buff[] = new byte[BlueToothControl.MTU+2];
			int offset=0;
			while(running){
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
				offset+=len;
			}
		}catch (Exception e){
			Log.e(TAG, e.getMessage(), e);
		}

		peer.disconnect(socket);
		running = false;
		thread = null;
	}
}
