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
 */
package org.servalproject.system;

import org.servalproject.Control;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

/**
 * an Intent Service that third party apps can use to start / stop the Serval
 * Mesh software
 */
public class ControlIntentService extends IntentService {

	/*
	 * private constants
	 */
	private final boolean V_LOG = true;
	private final String TAG = "StatusIntentService";

	/*
	 * call the super constructor with a name for the worker thread
	 */
	public ControlIntentService() {
		super("ControlIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		final ServalBatPhoneApplication mApplication = (ServalBatPhoneApplication) getApplicationContext();
		State mState = mApplication.getState();

		if (V_LOG) {
			Log.v(TAG, "Intent Received");
		}

		// check on the intent
		if (intent.getAction().equals("org.servalproject.ACTION_STOP_SERVAL") == true) {

			// start the control service if it isn't started already
			if(mState == State.On) {
				// serval is running so it should stop
				Intent mIntent = new Intent(this, Control.class);
				stopService(mIntent);

				// shutdown wifi by default
				if(mApplication.wifiRadio.getCurrentMode() == WifiMode.Client) {
					Thread mWifiOffThread = new Thread() {
						@Override
						public void run() {
							try {
								mApplication.wifiRadio
										.setWiFiMode(WifiMode.Off);
							} catch (Exception e) {
								Log.e("TAG", e.toString(), e);

							}
						}
					};

					mWifiOffThread.start();
				}

				Log.i(TAG,
						"serval was shutdown in response to third party request");
			} else {
				Log.i(TAG,
						"third party requested serval shutdown when it was in a state that prohibited shutdown");
			}

		} else if (intent.getAction().equals(
				"org.servalproject.ACTION_START_SERVAL") == true) {

			// stop the control service if it isn't started already
			if (mState == State.Off) {
				// serval is running so it should stop
				Intent mIntent = new Intent(this, Control.class);
				startService(mIntent);

				Log.i(TAG,
						"serval was started in response to third party request");
			} else {
				Log.i(TAG,
						"third party requested serval start when it was in a state that prohibited start");
			}
		} else {
			Log.w(TAG, "service called with the wrong intent");
			return;
		}
	}
}
