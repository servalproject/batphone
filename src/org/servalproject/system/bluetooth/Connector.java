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
	private final BluetoothAdapter adapter;
	private final BluetoothSocket socket;
	private final PeerState peer;

	static boolean connecting = false;
	// make sure we don't scan during a connection attempt as this can kill the connection!
	static boolean continuousScan = true;

	private static final String TAG = "Connector";
	Connector(BluetoothAdapter adapter, PeerState peer){
		this.adapter = adapter;
		this.peer=peer;
		this.socket=peer.socket;

		// use a single thread to ensure connections are serialised
		// TODO start another worker thread to reduce contention with the rest of the app?
		ServalBatPhoneApplication.context.runOnBackgroundThread(this);
	}

	@Override
	public void run() {
		try{
			connecting = true;
			// TODO block other connections and scans while we are connecting.
			BlueToothControl.cancelDiscovery(adapter);
			Log.v(TAG, "Connecting to " + peer.device.getAddress());
			socket.connect();

			new Thread(peer.reader, "Reader"+peer.device.getAddress()).start();
			new Thread(peer.writer, "Writer"+peer.device.getAddress()).start();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
			peer.disconnect(socket);
		} finally{
			connecting = false;
			// TODO delay the next scan using an alarm
			if (continuousScan)
				BlueToothControl.startDiscovery(adapter);
		}
	}
}
