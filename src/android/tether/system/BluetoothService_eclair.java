package android.tether.system;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;

public class BluetoothService_eclair extends BluetoothService {

	BluetoothAdapter btAdapter = null;
	
	public BluetoothService_eclair() {
		super();
        btAdapter = BluetoothAdapter.getDefaultAdapter();     
	}
	
	@Override
	public boolean startBluetooth() {
		boolean connected = false;
		this.btAdapter.enable();
		/**
		 * TODO: Not sure if that loop is needed anymore. 
		 * Looks like that bt is coming-up more reliable with Android 2.0
		 */
		int checkcounter = 0;
		while (connected == false && checkcounter <= 60) {
			// Wait up to 60s for bluetooth to come up.
			// does not behave unless started after BT is enabled.
			connected = this.btAdapter.isEnabled();
			if (connected == false) {
				checkcounter++;
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// Nothing
				}
			} else {
				break;
			}
		}
		return connected;
	}

	@Override
	public boolean stopBluetooth() {
		return this.btAdapter.disable();
	}

	@Override
	public boolean isBluetoothEnabled() {
		return this.btAdapter.isEnabled();
	}

	@Override
	public void setApplication(Application application) {
		// unneeded - not implemented
	}
}
