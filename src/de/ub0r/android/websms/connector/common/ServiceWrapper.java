/*
 * Copyright (C) 2010 Felix Bechstein, The Android Open Source Project
 * 
 * This file is part of ub0rlib.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */

package de.ub0r.android.websms.connector.common;

import android.app.Notification;
import android.app.Service;
import android.os.Build;

/**
 * Wrap around contacts API.
 * 
 * @author flx
 */
abstract class ServiceWrapper {
	/** Tag for output. */
	private static final String TAG = "sw";

	/**
	 * Static singleton instance of {@link ServiceWrapper} holding the
	 * SDK-specific implementation of the class.
	 */
	private static ServiceWrapper sInstance;

	/**
	 * Get instance.
	 * 
	 * @return {@link ServiceWrapper}
	 */
	public static final ServiceWrapper getInstance() {
		if (sInstance == null) {
			int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
			if (sdkVersion < Build.VERSION_CODES.DONUT) {
				sInstance = new ServiceWrapper3();
			} else {
				sInstance = new ServiceWrapper4();
			}
			Log.d(TAG, "getInstance(): " + sInstance.getClass().getName());
		}
		return sInstance;
	};

	/**
	 * Wrapper around setForground / startForground.
	 * 
	 * @param service
	 *            {@link Service}
	 * @param id
	 *            id of {@link Notification}
	 * @param notification
	 *            {@link Notification}
	 * @param forceNotification
	 *            force display of {@link Notification}
	 */
	abstract void startForeground(final Service service, int id,
			Notification notification, final boolean forceNotification);

	/**
	 * Wrapper around setForground / stopForground.
	 * 
	 * @param service
	 *            {@link Service}
	 * @param id
	 *            id of {@link Notification} to remove
	 */
	abstract void stopForeground(final Service service, int id);
}
