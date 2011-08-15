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

import java.lang.reflect.Method;

import android.app.Application;

public class BluetoothService_cupcake extends BluetoothService {

	Application application = null;

	/*
	 * Bluetooth API is not exposed publicly, so we need to use reflection
	 * to query and set the configuration.
	 */
	private Object callBluetoothMethod(String methodName) {
    	Object manager = this.application.getSystemService("bluetooth");
    	Class<?> c = manager.getClass();
    	Object returnValue = null;
    	if (c == null) {
    		// Nothing
    	} else {
        	try {
	        	Method enable = c.getMethod(methodName);
	        	enable.setAccessible(true);
	        	returnValue = enable.invoke(manager);
	        } catch (Exception e){
	        	e.printStackTrace();
		    }
	    }
    	return returnValue;
	}

	@Override
	public boolean isBluetoothEnabled() {
		return (Boolean) callBluetoothMethod("isEnabled");
	}

	@Override
	public boolean startBluetooth() {
		boolean connected = false;
		callBluetoothMethod("enable");
		int checkcounter = 0;
		while (connected == false && checkcounter <= 60) {
			// Wait up to 60s for bluetooth to come up.
			// pand does not behave unless started after BT is enabled.
			connected = (Boolean) callBluetoothMethod("isEnabled");
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
		callBluetoothMethod("disable");
		return true;
	}

	@Override
	public void setApplication(Application application) {
		this.application = application;
	}
}
