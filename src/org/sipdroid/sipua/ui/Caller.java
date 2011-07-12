package org.sipdroid.sipua.ui;

/*
 * Copyright (C) 2009 The Sipdroid Open Source Project
 *
 * This file is part of Sipdroid (http://www.sipdroid.org)
 *
 * Sipdroid is free software; you can redistribute it and/or modify
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

import org.servalproject.ServalBatPhoneApplication;
import org.sipdroid.sipua.SipdroidEngine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class Caller extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String intentAction = intent.getAction();
		final String number = getResultData();

		if (intentAction.equals(Intent.ACTION_NEW_OUTGOING_CALL)
				&& number != null) {
			if (number.endsWith("+")) {
				setResultData(number.substring(0, number.length() - 1));
				return;
			}

			if (!SipdroidEngine.isRegistered())
				return;
			Log.i("BatPhone", "intercepted outgoing call");

			setResultData(null);

			new Thread() {
				@Override
				public void run() {
					Intent intent = new Intent(
							Intent.ACTION_CALL,
							Uri.fromParts("servaldna", Uri.decode(number), null));
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					ServalBatPhoneApplication.context.startActivity(intent);
				}
			}.start();

		}
	}

}
