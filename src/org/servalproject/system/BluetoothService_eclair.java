/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.servalproject.system;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.os.Looper;

public class BluetoothService_eclair extends BluetoothService {

	BluetoothAdapter btAdapter = null;

	public BluetoothService_eclair() {
		super();
		// WHY does getDefaultAdapter require this!!!!???
		if (Looper.myLooper() == null)
			Looper.prepare();
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
