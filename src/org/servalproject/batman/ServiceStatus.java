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

package org.servalproject.batman;

import java.util.ArrayList;

import org.servalproject.PeerRecord;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.Identities;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;


/**
 * A class the defines a service that can be used to easily inspect various
 * aspects of the underlying batman system without the need to integrate with
 * native code
 *
 *
 * TODO with the new overlay mesh code, this entire class should probably be
 * deprecated.
 */
public class ServiceStatus extends Service {

	/**
	 * Constant to identify the peerCount message
	 *
	 * The response to this message is a message containing the number of current peers
	 */
	public static final int MSG_PEER_COUNT = 1;

	/**
	 * Constant to identify the peerList message
	 *
	 * The response to this message is a message containing information about peers
	 */
	public static final int MSG_PEER_LIST = 2;

	/**
	 * Constant to identify the routeTable message
	 */
	public static final int MSG_ROUTE_TABLE = 3;

	/**
	 * Constant to identify the minimum valid link score
	 */
	public static final int MIN_LINK_SCORE = 0;

	/**
	 * Constant to identify the maximum valid link score (255 for links to
	 * addresses for non-local nodes, but self can score 256)
	 */
	public static final int MAX_LINK_SCORE = 256;

	public static final String ROUTE_TABLE = "batmanRouteTable";
	public static final String PEER_RECORDS = "batmanPeerRecords";

	/*
	 * private class variables
	 */

	private ServalBatPhoneApplication app;

	/*
	 * private class constants
	 */

	private final boolean V_LOG = true;
	private final String TAG = "ServalBatman-Status";

	// target to handle incoming messages
	private final Messenger messenger = new Messenger(new Handler(){
		/*
		 * Determine how to handle the incoming message
		 * (non-Javadoc)
		 * @see android.os.Handler#handleMessage(android.os.Message)
		 */
		@Override
		public void handleMessage(Message msg) {
			/*
			 * Examine the what parameter of the message to
			 * determine what to do
			 */
			Message reply = Message.obtain(null, msg.what, null);
			Bundle mBundle;

			switch(msg.what) {
			case MSG_PEER_COUNT: // a message to provide the peer count
				reply.arg1 = Identities.getPeerCount();
				break;

			case MSG_PEER_LIST:
			case MSG_ROUTE_TABLE:
				mBundle = new Bundle();
				{
					mBundle.putParcelableArrayList(PEER_RECORDS, null);
					mBundle.putStringArray(ROUTE_TABLE, null);
					ArrayList<PeerRecord> peers = null;
					if (peers != null) {
						if (msg.what == MSG_PEER_LIST) {
							mBundle.putParcelableArrayList(PEER_RECORDS, peers);
						} else {
							String[] mPeerStrings = new String[peers
									.size()];

							for (int i = 0; i < peers.size(); i++) {
								mPeerStrings[i] = peers.get(i)
										.toString();
							}

							mBundle.putStringArray(ROUTE_TABLE, mPeerStrings);
						}
						reply.arg1 = peers.size();
					}
				}
				reply.setData(mBundle);
				break;

			default: // unknown message identifier
				super.handleMessage(msg);
			}

			// send the message as a reply with the included bundle
			try {
				msg.replyTo.send(reply);
			} catch (RemoteException e) {
				Log.e(TAG, "sending of reply failed", e);
			}
		}
	});

	/*
	 * called when this service is initially created
	 *
	 * (non-Javadoc)
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate() {
		app = (ServalBatPhoneApplication) this
				.getApplication();

		// output some logging to help in initial development / debugging
		if(V_LOG) {
			Log.v(TAG, "service created");
		}
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Service#onBind(android.content.Intent)
	 */
	@Override
	public IBinder onBind(Intent intent) {

		// return an instance of the binder associated with our messenger
		// so that the service can receive messages
		return messenger.getBinder();
	}
}
