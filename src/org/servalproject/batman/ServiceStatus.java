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

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

/**
 * A class the defines a service that can be used to easily inspect various aspects
 * of the underlying batman system without the need to integrate with native code
 * 
 * @author corey.wallis@servalproject.org
 *
 */
public class ServiceStatus extends Service {
	
	/**
	 * Constant to identify the isAlive message
	 * 
	 * The response to this message is a message that identifies if batman is running or not
	 */
	public static final int MSG_IS_RUNNING = 1;
	
	/**
	 * Constant to identify the peerCount message
	 * 
	 * The response to this message is a message containing the number of current peers
	 */
	public static final int MSG_PEER_COUNT = 2;
	
	/**
	 * Constant to identify the peerList message
	 * 
	 * The response to this message is a message containing information about peers
	 */
	public static final int MSG_PEER_LIST = 3;
	
	// reference to the service itself for use in the handler
	private ServiceStatus self = this;
	
	// target to handle incoming messages
	private final Messenger messenger = new Messenger(new IncomingHandler());
	
	/*
	 * private class constants
	 */
	
	private final boolean V_LOG = true;
	private final String TAG = "ServalBatman-Status";
	
	/*
	 * A private class to respond to incoming messages
	 */
	private class IncomingHandler extends Handler {
		
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
			switch(msg.what) {
			case MSG_IS_RUNNING: // a message to see if batman is running
				self.isBatmanRunning(msg.replyTo);
				break;
			case MSG_PEER_COUNT: // a message to provide the peer count
				self.getPeerCount(msg.replyTo);
				break;
			case MSG_PEER_LIST:
				self.getPeerList(msg.replyTo);
				break;
			default: // unknown message identifier
				super.handleMessage(msg);
			}
			
		}
		
	}
	
	/*
	 * called when this service is initially created
	 * 
	 * (non-Javadoc)
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate() {

		// set up any objects that are required to respond to messages that may be reusable
		
		// output some logging to help in initial development / debugging
		if(V_LOG) {
			Log.v(TAG, "service created");
		}
	}
	
	/*
	 * private methods to respond to messages
	 */
	
	/*
	 * Private method to respond to the is running message
	 */
	private void isBatmanRunning(Messenger replyTo) {
		// add code here to respond to the message
		
	}
	
	/*
	 * Private method to respond to the peer count message
	 */
	private void getPeerCount(Messenger replyTo) {
		// add code here to respond to the message
	}
	
	/*
	 * Private method to respond to the peer list message
	 */
	private void getPeerList(Messenger replyTo) {
		// add code here to respond to the message
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
		
		if(V_LOG) {
			Log.v(TAG, "service destroyed");
		}
	}


}
