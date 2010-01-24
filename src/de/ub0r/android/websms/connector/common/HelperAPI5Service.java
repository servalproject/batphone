/*
 * Copyright (C) 2010 Felix Bechstein
 * 
 * This file is part of WebSMS.
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

import java.lang.reflect.Method;

import android.app.Notification;
import android.app.Service;
import android.util.Log;

/**
 * Helper class to set/unset background for api5 systems.
 * 
 * @author flx
 */
class HelperAPI5Service {
	/** Tag for output. */
	private static final String TAG = "WebSMS.api5s";

	/** Error message if API5 is not available. */
	private static final String ERRORMESG = "no API5s available";

	/**
	 * Check whether API5 is available.
	 * 
	 * @return true if API5 is available
	 */
	public final boolean isAvailable() {
		try {
			Method mDebugMethod = Service.class.getMethod("startForeground",
					new Class[] { Integer.TYPE, Notification.class });
			/* success, this is a newer device */
			if (mDebugMethod != null) {
				return true;
			}
		} catch (Throwable e) {
			Log.d(TAG, ERRORMESG, e);
			throw new VerifyError(ERRORMESG);
		}
		Log.d(TAG, ERRORMESG);
		throw new VerifyError(ERRORMESG);
	}

	/**
	 * Run Service in foreground.
	 * 
	 * @see Service.startForeground()
	 * @param service
	 *            the Service
	 * @param id
	 *            notification id
	 * @param notification
	 *            notification
	 */
	public final void startForeground(final Service service, final int id,
			final Notification notification) {
		service.startForeground(id, notification);
	}

	/**
	 * Run Service in background.
	 * 
	 * @see Service.stopForeground()
	 * @param service
	 *            Service
	 * @param removeNotification
	 *            remove notification?
	 */
	public final void stopForeground(final Service service,
			final boolean removeNotification) {
		service.stopForeground(removeNotification);
	}
}
