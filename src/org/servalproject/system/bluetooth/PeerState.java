package org.servalproject.system.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;

import java.io.IOException;
import java.util.Date;

/**
* Created by jeremy on 7/04/15.
*/
public class PeerState {
	private final BlueToothControl control;
	public final BluetoothDevice device;
	public Date lastScan;
	public int runningServal=-1;
	BluetoothSocket socket;
	PeerReader reader;
	PeerWriter writer;
	public final byte[] addrBytes;
	private static final String TAG="PeerState";
	private ServalBatPhoneApplication app;

	PeerState(BlueToothControl control, BluetoothDevice device, byte[] addrBytes){
		this.control = control;
		this.device = device;
		this.addrBytes = addrBytes;
		this.app = ServalBatPhoneApplication.context;
	}

	synchronized void setSocket(BluetoothSocket socket, boolean isConnected) throws IOException {
		if (this.socket != null) {
			// pick the winning socket deterministically

			if (socket!=null && isConnected && device.getAddress().compareTo(control.adapter.getAddress())>0) {
				Log.v(TAG, "Killing incoming connection as we already have one");
				socket.close();
				return;
			}

			Log.v(TAG, "Closing existing connection");
			this.reader.close();
			this.writer.close();
			this.socket.close();
			this.socket = null;
			this.reader = null;
			this.writer = null;
		}
		if (socket != null) {
			Log.v(TAG, "Starting threads for new connection");
			this.socket = socket;
			reader = new PeerReader(control, this);
			writer = new PeerWriter(this);

			if (isConnected){
				new Thread(reader, "Reader" + device.getAddress()).start();
				new Thread(writer, "Writer" + device.getAddress()).start();
			}else{
				new Connector(control.adapter, this);
			}
		}
	}

	public void connect() throws IOException {
		if (socket!=null)
			return;
		if (!control.adapter.isEnabled())
			return;
		int bondState = device.getBondState();
		if (bondState == BluetoothDevice.BOND_BONDING)
			return;
		boolean paired = (bondState == BluetoothDevice.BOND_BONDED);
		if (!paired && Build.VERSION.SDK_INT <10)
			return;

		synchronized (this) {
			if (socket!=null)
				return;

			if (paired) {
				setSocket(device.createRfcommSocketToServiceRecord(BlueToothControl.SECURE_UUID), false);
			} else {
				setSocket(device.createInsecureRfcommSocketToServiceRecord(BlueToothControl.INSECURE_UUID), false);
			}
		}
	}

	public void queuePacket(byte payload[]){
		if (!control.adapter.isEnabled())
			return;
		try {
			connect();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
			return;
		}
		if (writer==null)
			return;
		writer.queuePacket(payload);
	}

	public synchronized void disconnect(BluetoothSocket socket){
		try {
			if (socket == this.socket)
				setSocket(null, false);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
}
