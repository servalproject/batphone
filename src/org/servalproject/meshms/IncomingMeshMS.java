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

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeMessage;
import org.servalproject.servald.Identities;
import org.servalproject.servald.SubscriberId;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

/**
 * used to receive MeshMS messages sent by other applications
 */
public class IncomingMeshMS extends IntentService {

	/*
	 * private constants
	 */
	private static final String TAG = "MeshMS";

	/*
	 * call the super constructor with a name for the worker thread
	 */
	public IncomingMeshMS() {
		super("IncomingMeshMS");
		Log.i(IncomingMeshMS.TAG, "constructor");
	}

	/** Inject a MeshMS message into Rhizome, to be sent over the mesh.
	 *
	 * The IntentService calls this method from the default worker thread with the intent that
	 * started the service. When this method returns, IntentService stops the service, as
	 * appropriate.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	@Override
	protected void onHandleIntent(Intent intent) {
		SimpleMeshMS message = intent.getParcelableExtra("simple");
		if (message != null) {
			Log.v(TAG, "new simple message recieved");
		} else {
			SubscriberId sender;
			SubscriberId recipient;
			try {
				sender = new SubscriberId(intent.getStringExtra("sender"));
			}
			catch (NullPointerException e) {
				Log.w(TAG, "intent is missing 'sender' extra data -- using current identity");
				sender = Identities.getCurrentIdentity();
			}
			catch (SubscriberId.InvalidHexException e) {
				Log.e(TAG, "intent has invalid 'sender' extra data", e);
				return;
			}
			try {
				recipient = new SubscriberId(intent.getStringExtra("recipient"));
			}
			catch (NullPointerException e) {
				Log.e(TAG, "intent is missing 'recipient' extra data");
				return;
			}
			catch (SubscriberId.InvalidHexException e) {
				Log.e(TAG, "intent has invalid 'recipient' extra data", e);
				return;
			}
			String text = intent.getStringExtra("text");
			if (text == null) {
				Log.e(TAG, "intent is missing 'text' extra data");
				return;
			}
			String timestamp = intent.getStringExtra("timestamp");
			long millis;
			if (timestamp != null) {
				try {
					millis = Long.parseLong(timestamp);
				}
				catch (NumberFormatException e) {
					Log.e(TAG, "invalid 'timestamp' extra data", e);
					return;
				}
			} else {
				Log.w(TAG, "intent is missing 'millis' extra data -- using current time");
				millis = System.currentTimeMillis();
			}
			message = new SimpleMeshMS(
					sender,
					recipient,
					intent.getStringExtra("senderDid"),
					intent.getStringExtra("recipientDid"),
					millis,
					text
				);
		}
		processSimpleMessage(message);
		/*
		ComplexMeshMS cm = intent.getParcelableExtra("complex");
		if (cm != null) {
			Log.v(TAG, "new complex message recieved");
			processComplexMessage(cm);
		}
		*/
	}

	// private method to write a complex message to a binary file
	private void processComplexMessage(ComplexMeshMS message) {
		Log.e(TAG, "complex messages NOT IMPLEMENTED");
		/*
		//TODO add validation of fields?
		// build a protobuf bassed meshsms
		MeshMSProtobuf.MeshMS.Builder mMeshMS = MeshMSProtobuf.MeshMS.newBuilder();

		// add the main metadata
		mMeshMS.setSender(message.getSender());
		mMeshMS.setRecipient(message.getRecipient());
		mMeshMS.setType(message.getType());
		mMeshMS.setTimestamp(message.getTimestamp());

		// add the content
		ArrayList<MeshMSElement> mContentList = message.getContent();

		MeshMSProtobuf.MeshMS.ContentElem.Builder mMeshMSContent;

		for(MeshMSElement mContent: mContentList) {

			if(mContent.getContent() != null) {
				// optional message components must still have a value
				mMeshMSContent = MeshMSProtobuf.MeshMS.ContentElem.newBuilder();
				mMeshMSContent.setType(mContent.getType());
				mMeshMSContent.setContent(mContent.getContent());
				mMeshMS.addContent(mMeshMSContent);
			}
		}

		// write the file
		File mOutbox = new File(getString(R.string.system_path_meshms_outbox));
		mOutbox.mkdirs();

		String mFileName = getString(R.string.system_path_meshms_outbox) + message.getSender() + "-" + message.getRecipient() + ".bin";

		try {
			FileOutputStream mOutput = new FileOutputStream(mFileName);
			mMeshMS.build().writeTo(mOutput);
			mOutput.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Unable to create output file", e);
		} catch (IOException e) {
			Log.e(TAG, "Unable to write the output file", e);
		}
		*/
	}

	// private method to process a simple message
	private void processSimpleMessage(SimpleMeshMS message) {
		if (message.content == null) {
			Log.e(TAG, "new simpleMeshMS is missing the content field");
			return;
		}
		Log.d(TAG, "sender=" + message.sender);
		Log.d(TAG, "recipient=" + message.recipient);
		Log.d(TAG, "senderDID=" + message.senderDid);
		Log.d(TAG, "recipientDID=" + message.recipientDid);
		Log.d(TAG, "timestamp=" + message.timestamp);
		Log.d(TAG, "content=" + message.content);
		RhizomeMessage rm = new RhizomeMessage(message.sender, message.recipient, message.senderDid, message.recipientDid, message.timestamp, message.content);
		if (rm.send()) {
			Log.i(TAG, "new simpleMeshMS to: " + message.recipient + " has been sent via Rhizome");
		} else {
			Log.w(TAG, "unable to send new SimpleMeshMS via Rhizome");
		}
	}

}
