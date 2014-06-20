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

package org.servalproject.servald;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import org.servalproject.R;
import org.servalproject.batphone.CallHandler;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.servaldna.AsyncResult;
import org.servalproject.servaldna.ChannelSelector;
import org.servalproject.servaldna.MdpDnaLookup;
import org.servalproject.servaldna.MdpServiceLookup;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.ServalDFailureException;
import org.servalproject.servaldna.ServalDInterfaceException;
import org.servalproject.servaldna.ServerControl;
import org.servalproject.servaldna.SubscriberId;

import java.io.IOException;

/**
 * Low-level class for invoking servald JNI command-line operations.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class ServalD extends ServerControl
{
	public static final String ACTION_STATUS = "org.servalproject.ACTION_STATUS";
	public static final String EXTRA_STATUS = "status";
	private static final String TAG = "ServalD";

	private long started = -1;
	private String status=null;
	private ServalDMonitor monitor;
	private final Context context;
	private ChannelSelector selector;

	public String getStatus(){
		return status;
	}

	public ServalDMonitor getMonitor(){
		return monitor;
	}

	public void updateStatus(int resourceId) {
		updateStatus(context.getString(resourceId));
	}
	public void updateStatus(String newStatus) {
		status = newStatus;
		Intent intent = new Intent(ACTION_STATUS);
		intent.putExtra(EXTRA_STATUS, newStatus);
		context.sendBroadcast(intent);
	}

	private static ServalD instance;

	private ServalD(String execPath, Context context){
		super(execPath);
		this.context = context;
	}

	public static synchronized ServalD getServer(String execPath, Context context){
		if (instance==null)
			instance = new ServalD(execPath, context);
		return instance;
	}

	private synchronized void startMonitor(){
		if (monitor!=null)
			return;
		ServalDMonitor m = monitor = new ServalDMonitor(this);
		CallHandler.registerMessageHandlers(m);
		PeerListService.registerMessageHandlers(m);
		Rhizome.registerMessageHandlers(m);
		new Thread(m, "Monitor").start();
	}

	/** Start the servald server process if it is not already running.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void start() throws ServalDFailureException {
		updateStatus(R.string.server_starting);
		super.start();
		started = System.currentTimeMillis();
		Log.i(TAG, "Server started");
		startMonitor();
	}

	/** Stop the servald server process if it is running.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public void stop() throws ServalDFailureException {
		try{
			if (monitor!=null){
				monitor.stop();
				monitor=null;
			}
			super.stop();
		}finally{
			updateStatus(R.string.server_off);
			started = -1;
		}
	}

	/** Query the servald server process status.
	 *
	 * @return	True if the process is running
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public boolean isRunning() throws ServalDFailureException {
		if (monitor!=null && monitor.ready())
			return true;
		// always start servald daemon if it isn't running.
		start();
		return true;
	}

	public MdpServiceLookup getMdpServiceLookup(AsyncResult<MdpServiceLookup.ServiceResult> results) throws ServalDInterfaceException, IOException {
		if (selector==null)
			selector = new ChannelSelector();
		return getMdpServiceLookup(selector, results);
	}

	public MdpDnaLookup getMdpDnaLookup(AsyncResult<ServalDCommand.LookupResult> results) throws ServalDInterfaceException, IOException {
		if (selector==null)
			selector = new ChannelSelector();
		return getMdpDnaLookup(selector, results);
	}

	public static Cursor rhizomeList(final String service, final String name, final SubscriberId sender, final SubscriberId recipient)
			throws ServalDFailureException
	{
		return new ServalDCursor(){
			@Override
			void fillWindow(CursorWindowJniResults window, int offset, int numRows) throws ServalDFailureException{
				ServalDCommand.rhizomeList(window, service, name, sender, recipient, offset, numRows);
			}
		};
	}

	public static boolean isRhizomeEnabled() {
		return ServalDCommand.getConfigItemBoolean("rhizome.enable", true);
	}

}
