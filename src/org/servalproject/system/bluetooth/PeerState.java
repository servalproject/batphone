package org.servalproject.system.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.SortedSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
* Created by jeremy on 7/04/15.
*/
public class PeerState implements Runnable{
	private final BlueToothControl control;
	public final BluetoothDevice device;
	private Connector connector;
	public Date lastScan;
	public int runningServal=-1;

	LinkedList<PeerReader> readers = new LinkedList<PeerReader>();
	Thread writerThread;

	private LinkedList<byte[]> queue = new LinkedList<byte[]>();
	public final byte[] addrBytes;
	private static final String TAG="PeerState";
	private ServalBatPhoneApplication app;

	private Runnable expireConnections = new Runnable() {
		@Override
		public void run() {
			synchronized (readers){
				ListIterator<PeerReader> i = readers.listIterator();
				while(i.hasNext()){
					PeerReader r = i.next();
					if (SystemClock.elapsedRealtime() - r.lastReceived > 10000) {
						Log.v(TAG, "Closing expired connection to "+device.getAddress());
						i.remove();
						onClosed(r);
					}
				}
				Collections.sort(readers);
				if (readers.isEmpty())
					return;
			}
			app.runOnBackgroundThread(expireConnections, 5000);
		}
	};

	PeerState(BlueToothControl control, BluetoothDevice device, byte[] addrBytes){
		this.control = control;
		this.device = device;
		this.addrBytes = addrBytes;
		this.app = ServalBatPhoneApplication.context;
	}

	public void connect() throws IOException {
		if (connector!=null || (!readers.isEmpty()) || (!control.adapter.isEnabled()))
			return;
		int bondState = device.getBondState();
		if (bondState == BluetoothDevice.BOND_BONDING)
			return;
		boolean paired = (bondState == BluetoothDevice.BOND_BONDED);
		if (!paired && Build.VERSION.SDK_INT <10)
			return;

		synchronized (this) {
			if (connector!=null)
				return;

			BluetoothSocket socket;
			if (paired) {
				socket = device.createRfcommSocketToServiceRecord(BlueToothControl.SECURE_UUID);
			} else {
				socket = device.createInsecureRfcommSocketToServiceRecord(BlueToothControl.INSECURE_UUID);
			}

			int bias = device.getAddress().toLowerCase().compareTo(control.adapter.getAddress().toLowerCase())*500;

			PeerReader r = new PeerReader(control, this, socket, paired, bias);
			connector = new Connector(control, this, r);
		}
	}

	public void onConnected(BluetoothSocket socket, boolean secure){
		onConnected(new PeerReader(control, this, socket, secure, 0));
	}
	public synchronized void onConnected(PeerReader reader){
		connector = null;
		synchronized (readers) {
			boolean notify = readers.isEmpty() && writerThread!=null;
			readers.addFirst(reader);
			Collections.sort(readers);
			if (notify)
				readers.notify();
		}
		reader.start();

		if (writerThread==null) {
			writerThread = new Thread(this, "Writer" + device.getAddress());
			writerThread.start();
			app.runOnBackgroundThread(expireConnections,5000);
		}
	}

	public void queuePacket(byte payload[]){
		if (!control.adapter.isEnabled())
			return;

		synchronized (queue) {
			boolean notify = queue.isEmpty() && writerThread!=null;
			queue.addLast(payload);
			if (notify)
				queue.notify();
		}

		try {
			connect();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
			return;
		}
	}

	public synchronized void disconnect(){
		closeWriter();
		synchronized (readers) {
			for (PeerReader r : readers) {
				try {
					r.socket.close();
				} catch (IOException e) {
					Log.e(r.name, e.getMessage());
				}
			}
			readers.clear();
		}
	}

	private void closeWriter(){
		Thread t = writerThread;
		writerThread = null;
		if (t!=null)
			t.interrupt();
	}

	public void onClosed(PeerReader peerReader) {
		synchronized (readers) {
			readers.remove(peerReader);
			if (readers.isEmpty())
				closeWriter();
			try{
				peerReader.socket.close();
			}catch (IOException e){
				Log.e(peerReader.name, e.getMessage(), e);
			}
		}
	}

	@Override
	public void run() {
		try {
			byte buff[] = new byte[BlueToothControl.MTU + 2];
			while (writerThread == Thread.currentThread()) {
				byte payload[] = null;

				synchronized (queue) {
					if (queue.isEmpty())
						queue.wait();
					payload = queue.removeFirst();
				}
				if (payload==null)
					continue;

				if (payload.length + 2 > buff.length)
					throw new IllegalStateException(payload.length + " is greater than the link MTU");
				buff[0] = (byte) (payload.length);
				buff[1] = (byte) (payload.length >> 8);
				System.arraycopy(payload, 0, buff, 2, payload.length);

				PeerReader reader = null;
				synchronized (readers){
					if (readers.isEmpty())
						readers.wait();
					reader = readers.peekFirst();
				}
				if (reader==null)
					continue;

				try {
					// try to write in one go or the blutooth layer will waste bandwidth sending fragments
					reader.socket.getOutputStream().write(buff, 0, payload.length + 2);
					reader.lastWritten = SystemClock.elapsedRealtime();
				}catch (IOException e){
					Log.e(reader.name, e.getMessage(), e);
					onClosed(reader);
				}
			}
		}catch (InterruptedException e){
			Log.e(TAG, e.getMessage(), e);
		}catch (Exception e){
			Log.e(TAG, e.getMessage(), e);
		}

		if (writerThread == Thread.currentThread())
			writerThread = null;

		Log.v(TAG, "Writer exited");
	}

	public void onConnectionFailed() {
		this.connector = null;
	}
}
