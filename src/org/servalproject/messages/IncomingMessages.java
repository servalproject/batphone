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
package org.servalproject.messages;

import org.servalproject.meshms.SimpleMeshMS;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * receives the incoming MeshMS intents and processes them by putting them into
 * the database
 */
public class IncomingMessages extends BroadcastReceiver {

	/*
	 * private class level variables
	 */
	private final String TAG = "IncomingMessages";
	private final boolean V_LOG = true;

	@Override
	public void onReceive(Context context, Intent intent) {

		if (V_LOG) {
			Log.v(TAG, "Intent Received");
		}

		// check to see if this is a complex or simple message
		SimpleMeshMS mSimpleMessage = intent.getParcelableExtra("simple");

		if (mSimpleMessage == null) {
			Log.e(TAG, "missing SimpleMeshMS message");
			return;
		}

		// see if there is already a thread with this recipient
		ContentResolver mContentResolver = context.getContentResolver();

		int mThreadId = MessageUtils.getThreadId(mSimpleMessage,
				mContentResolver);

		if (V_LOG) {
			Log.v(TAG, "Thread ID: " + mThreadId);
		}

		if (mThreadId != -1) {
			int mMessageId = MessageUtils.saveReceivedMessage(
					mSimpleMessage,
					mContentResolver,
					mThreadId);

			if (mMessageId != -1) {
				Log.i(TAG, "New message saved with thread '" + mThreadId
						+ "' and message '" + mMessageId + "'");
			}
		} else {
			Log.e(TAG, "unable to save new message");
		}
	}
}
