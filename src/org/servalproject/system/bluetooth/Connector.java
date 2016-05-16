package org.servalproject.system.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;

import java.io.IOException;

/**
* Created by jeremy on 7/04/15.
*/
class Connector implements Runnable{
	private final BlueToothControl control;
	private final BluetoothAdapter adapter;
	private final PeerReader reader;
	private final PeerState peer;

	static boolean connecting = false;

	private static final String TAG = "Connector";
	Connector(BlueToothControl control, PeerState peer, PeerReader reader){
		this.control = control;
		this.adapter = control.adapter;
		this.peer = peer;
		this.reader = reader;

		// use a single thread to ensure connections are serialised
		// TODO start another worker thread to reduce contention with the rest of the app?
		ServalBatPhoneApplication.context.runOnBackgroundThread(this);
	}

	@Override
	public void run() {
		try{
			connecting = true;
			control.cancelDiscovery();
			Log.v(reader.name, "Connecting to " + peer.device.getAddress() +" ("+reader.secure+")");
			reader.socket.connect();

			peer.onConnected(reader);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
			try {
				reader.socket.close();
			} catch (IOException e1){}
			peer.onConnectionFailed();
		} finally{
			connecting = false;
			control.onConnectionFinished();
		}
	}
}
