/*
 * This file is part of the Serval Mapping Services app.
 *
 *  Serval Mapping Services app is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation, either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  Serval Mapping Services app is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Serval Mapping Services app.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package org.servalproject.rhizome.peers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;


/**
 * A client class to the ServiceStatus service in the batphone software
 * Used to get a list of available peers
 *
 */
public class BatmanServiceClient implements Runnable {

	/**
	 * delay between updating the route table (in seconds)
	 */
	public static final int SLEEP_TIME = 1000 * 30;

	/*
	 * private class level constants
	 */

	private final boolean V_LOG = true;
	private final String TAG = "Serval-BSC";

	/*
	 * private class level variables
	 */
	private Messenger serviceMessenger = null;
	private Context context;
	private BatmanPeerList peerList;

	private volatile boolean keepGoing = true;

	 /*
     * class to handle the incoming messages from the service
     */
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
        	// look for the id as defined in the service implementation
        	if(V_LOG) {
    			Log.v(TAG, "message received");
    			Log.v(TAG, "message what: " + msg.what);
    			Log.v(TAG, "message arg1: " + msg.arg1);
    		}


        	switch (msg.what) {
            case 3:
            	// this is the route table message
            	if(msg.arg1 > 0) {
            		Bundle mBundle = msg.getData();
            		String[] mPeerRecords = mBundle.getStringArray("batmanRouteTable");

            		// update the peer list
            		peerList.updatePeerList(mPeerRecords);

            		if(V_LOG) {
            			Log.v(TAG, "peer list updated, size: " + mPeerRecords.length);
            		}
            	}
            	break;
            default:
                super.handleMessage(msg);
        	}
        }
    }

    /*
     * Messenger object that the service can use to send replies
     */
	private Messenger messenger;

    /*
     * ServiceConnection class that represents a collection to the org.servalproject.batman.ServiceStatus service
     */
    private ServiceConnection connection = new ServiceConnection() {
    	@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
    		// called when the connection is made
    		serviceMessenger = new Messenger(service);

    		if(V_LOG) {
    			Log.v(TAG, "connected to the batman service");
    		}
    	}

    	@Override
		public void onServiceDisconnected(ComponentName className) {
    		// called when the connection is lost
    		serviceMessenger = null;

    		if(V_LOG) {
    			Log.v(TAG, "disconnected to the batman service");
    		}
    	}
    };

   // bind to the service
    private void doBindService() {
    	context.bindService(new Intent("org.servalproject.batman.SERVICE_STATUS"), connection, Context.BIND_AUTO_CREATE);
    }

    // unbind from the service
    private void doUnbindService() {
        if(serviceMessenger != null) {
            // Detach our existing connection.
            context.unbindService(connection);
        }
    }

    /**
     * constructor for this class
     *
     * @param context the parent context
     * @param peerList an object that will be shared with other threads to store the peer list
     * @throws IllegalArgumentException if any parameter is null
     */
    public BatmanServiceClient(Context context, BatmanPeerList peerList) {
    	if(context == null || peerList == null) {
    		throw new IllegalArgumentException("all parameters to this method are required");
    	}

    	this.context = context;
    	this.peerList = peerList;

    	doBindService();
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
	@Override
	public void run() {
		Looper.prepare();
		Looper.loop();

		if (messenger == null)
			messenger = new Messenger(new IncomingHandler());

		while(keepGoing) {

			if(V_LOG) {
    			Log.v(TAG, "update thread running");
    		}


			if(serviceMessenger != null) {
				Message mMessage = Message.obtain(null, 3); // 3 being the required constant as defined in the service
				mMessage.replyTo = messenger;
    			try {
					serviceMessenger.send(mMessage);

					if(V_LOG) {
		    			Log.v(TAG, "message sent");
		    		}

				} catch (RemoteException e) {
					Log.e(TAG, "unable to update the route table from batman", e);
				}
			}

			try {
				if(V_LOG) {
	    			Log.v(TAG, "thread sleeping");
	    		}
				Thread.sleep(SLEEP_TIME);
			} catch (InterruptedException e) {
//				if(V_LOG) {
//					Log.v(TAG, "interrupted while sleeping", e);
//				}
			}
		}
	}

	/**
	 * request that the thread stops
	 */
	public void requestStop() {
		keepGoing = false;
		doUnbindService();
	}

}
