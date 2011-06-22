/*
 * This file is part of the Serval Batman Inspector Library.
 *
 *  Serval Batman Inspector Library is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation, either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  Serval Batman Inspector Library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Serval Batman Inspector Library.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package org.servalproject.batman;

import java.io.IOException;
import java.util.ArrayList;

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
 * A class the defines a service that can be used to easily inspect various aspects
 * of the underlying batman system without the need to integrate with native code
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
	 * Constant to identify the maximum valid link score
	 */
	public static final int MAX_LINK_SCORE = 255;

	public static final String ROUTE_TABLE = "batmanRouteTable";
	public static final String PEER_RECORDS = "batmanPeerRecords";

	/*
	 * private class variables
	 */

	// class to parse the peers file
	private FileParser fileParser;

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
				try {
					reply.arg1=fileParser.getPeerCount();
				} catch (IOException e) {
					reply.arg1= -1;
					Log.e(TAG, "unable to retrieve batman peer count", e);
				}
				break;

			case MSG_PEER_LIST:
				mBundle = new Bundle();
				try {
					ArrayList<PeerRecord> mPeerRecords = fileParser.getPeerList();
					mBundle.putParcelableArrayList(PEER_RECORDS, mPeerRecords);
					reply.arg1=mPeerRecords.size();

				} catch (IOException e) {
					// an error occurred so return null to indicate indeterminate status
					mBundle.putParcelableArrayList(PEER_RECORDS, null);
					reply.arg1=-1;
					Log.e(TAG, "unable to retrieve batman peer list", e);
				}
				reply.setData(mBundle);
				break;

			case MSG_ROUTE_TABLE:
				mBundle = new Bundle();
				try {
					RoutingParser mParser = new RoutingParser();
					ArrayList<PeerRecord> mPeerRecords = mParser.getPeerList();
					String[] mPeerStrings = new String[mPeerRecords.size()];

					for(int i = 0; i < mPeerRecords.size(); i++) {
						mPeerStrings[i] = mPeerRecords.get(i).toString();
					}

					mBundle.putStringArray(ROUTE_TABLE, mPeerStrings);
					reply.arg1 = mPeerRecords.size();

				} catch (IOException e) {
					// an error occurred so return null to indicate
					// indeterminate status
					mBundle.putStringArray(ROUTE_TABLE, null);
					reply.arg1 = -1;
					Log.e(TAG, "unable to retrieve batman route table", e);
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

		// set up any objects that are required to respond to messages that may be reusable
		fileParser = FileParser.getFileParser();

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

	/*
	 * called when the system destroys this service
	 *
	 * (non-Javadoc)
	 * @see android.app.Service#onDestroy()
	 */
	@Override
	public void onDestroy() {

		// tidy up after any objects that may have been created in preparation to respond to any messages
		fileParser= null;

		if(V_LOG) {
			Log.v(TAG, "service destroyed");
		}
	}
}
