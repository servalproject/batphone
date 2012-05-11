/*
 * Copyright (C) 2012 The Serval Project
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

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

/**
 * an intent service that can be used to check on the state of the Serval Mesh
 * software
 *
 */
public class StateCheckIntentService extends IntentService {

	/*
	 * private constants
	 */
	private final boolean V_LOG = true;
	private final String TAG = "StatusIntentService";

	/*
	 * call the super constructor with a name for the worker thread
	 */
	public StateCheckIntentService() {
		super("StatusIntentService");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.IntentService#onHandleIntent(android.content.Intent)
	 */
	@Override
	protected void onHandleIntent(Intent intent) {

		if (V_LOG) {
			Log.v(TAG, "Intent Received");
		}

		// check on the intent
		if (intent.getAction().equals("org.servalproject.ACTION_STATE_CHECK") == false) {
			Log.w(TAG, "service called with the wrong intent");
			return;
		}

		// get the current status
		ServalBatPhoneApplication mApplication = (ServalBatPhoneApplication) getApplicationContext();
		State mState = mApplication.getState();

		// prepare a new intent
		Intent mIntent = new Intent(
				"org.servalproject.ACTION_STATE_CHECK_UPDATE");
		mIntent.putExtra(
				ServalBatPhoneApplication.EXTRA_STATE,
				mState.ordinal());
		this.sendBroadcast(mIntent);
	}
}
