package org.servalproject.system.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
* Created by jeremy on 7/04/15.
*/
class PeerWriter implements Runnable{
	private final BluetoothSocket socket;
	private final PeerState peer;
	private boolean running = true;
	private Thread thread;
	// TODO simpler queue?
	private BlockingQueue<byte[]> queue;
	private static final String TAG="PeerWriter";

	PeerWriter(PeerState peer){
		this.socket = peer.socket;
		this.peer = peer;
		queue = new LinkedBlockingQueue<byte[]>();
	}

	public void close(){
		if (!running)
			return;
		running = false;
		Thread t = thread;
		if (t!=null)
			t.interrupt();

	}

	public void queuePacket(byte[] payload){
		queue.add(payload);
	}

	@Override
	public void run() {
		thread = Thread.currentThread();
		try {
			OutputStream out = socket.getOutputStream();
			byte buff[] = new byte[BlueToothControl.MTU + 2];
			while (running) {
				byte payload[] = queue.take();
				if (payload.length + 2 > buff.length)
					throw new IllegalStateException(payload.length + " is greater than the link MTU");
				buff[0] = (byte) (payload.length);
				buff[1] = (byte) (payload.length >> 8);
				System.arraycopy(payload, 0, buff, 2, payload.length);
				// try to write in one go or the blutooth layer will waste bandwidth sending fragments
				out.write(buff, 0, payload.length + 2);
			}
		}catch (InterruptedException e){
			//ignore
		}catch (Exception e){
			Log.e(TAG, e.getMessage(), e);
		}

		peer.disconnect(socket);
		running = false;
		thread = null;
	}
}
