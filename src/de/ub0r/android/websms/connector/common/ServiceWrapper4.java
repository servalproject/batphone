/*
 * Copyright (C) 2010 Felix Bechstein
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
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;

/**
 * Helper class to set/unset background for api5 systems.
 * 
 * @author flx
 */
public final class ServiceWrapper4 extends ServiceWrapper {
	/** Tag for output. */
	private static final String TAG = "sw4";

	/**
	 * {@inheritDoc}
	 */
	@Override
	void startForeground(final Service service, final int id,
			final Notification notification, final boolean forceNotification) {
		Log.d(TAG, "startForground(srv, " + id + "," + forceNotification + ")");
		service.startForeground(id, notification);
		if (forceNotification) {
			final NotificationManager nm = (NotificationManager) service
					.getSystemService(Context.NOTIFICATION_SERVICE);
			nm.notify(id, notification);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void stopForeground(final Service service, final int id) {
		Log.d(TAG, "stopForground(srv, " + id + ")");
		if (id >= 0) {
			final NotificationManager nm = (NotificationManager) service
					.getSystemService(Context.NOTIFICATION_SERVICE);
			nm.cancel(id);
		}
		service.stopForeground(true);
	}
}
