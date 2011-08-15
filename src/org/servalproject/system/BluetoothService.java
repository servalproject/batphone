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
import android.os.Build;

public abstract class BluetoothService {

	public abstract boolean startBluetooth();
	public abstract boolean stopBluetooth();
	public abstract boolean isBluetoothEnabled();
	public abstract void setApplication(Application application);

	private static BluetoothService bluetoothService;

	public static BluetoothService getInstance() {
	    if (bluetoothService == null) {
	        String className;

	        int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
	        if (sdkVersion < Build.VERSION_CODES.ECLAIR) {
	            className = "org.servalproject.system.BluetoothService_cupcake";
	        } else {
	            className = "org.servalproject.system.BluetoothService_eclair";
	        }

	        try {
	            Class<? extends BluetoothService> clazz = Class.forName(className).asSubclass(BluetoothService.class);
	            bluetoothService = clazz.newInstance();
	        } catch (Exception e) {
	            throw new IllegalStateException(e);
	        }
	    }
	    return bluetoothService;
	}
}
