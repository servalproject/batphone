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
package org.servalproject.meshms;

import org.servalproject.dna.DataFile;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * used to receive MeshMS messages from DNA and, repackage them and send them
 * out to any apps registered to receive the public API
 */
public class OutgoingMeshMS extends BroadcastReceiver {

	// class level constants
	private final String TAG = "OutgoingMeshMS";

	/*
	 * (non-Javadoc)
	 *
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
	 * android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {

		// check to make sure we've received the appropriate intent
		// this is the intent used by the server.c component of Serval DNA
		if (intent.getAction().equals("org.servalproject.DT") == true) {
			// process the message from Serval DNA
			processFromServalDna(context, intent);
		} else if (intent.getAction().equals(
				"org.servalproject.meshms.RECEIVE_MESHMS") == true) {
			processFromRhizome(context, intent);
		} else {
			Log.w(TAG, "unknown intent received: " + intent.getAction());
		}
	}

	private void processFromServalDna(Context context, Intent intent) {

		// process the message
		String mSender = intent.getExtras().getString("number");
		String mContent = intent.getExtras().getString("content");

		// create a new SimpleMeshMS message and populate it
		SimpleMeshMS mMessage;

		mMessage = new SimpleMeshMS(mSender, DataFile.getDid(0), mContent);

		Intent mBroadcastIntent = new Intent(
				"org.servalproject.meshms.RECEIVE_MESHMS");
		mBroadcastIntent.putExtra("simple", mMessage);
		context.sendBroadcast(mBroadcastIntent);

		addToSMSStore(mMessage.getSender(), mMessage.getContent(), context);
	}

	private void processFromRhizome(Context context, Intent intent) {

		SimpleMeshMS mMessage = (SimpleMeshMS) intent
				.getParcelableExtra("simple");

		addToSMSStore(mMessage.getSender(), mMessage.getContent(), context);

	}

	private void addToSMSStore(String sender, String content, Context context) {

		// TODO have some way to suppress what messages end up in the datastore
		ContentValues values = new ContentValues();
		values.put("address", sender);
		values.put("body", content);
		context.getContentResolver().insert(Uri.parse("content://sms/inbox"),
				values);
	}
}
