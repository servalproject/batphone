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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.*;
import android.os.Process;
import android.util.Log;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.batphone.CallHandler;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.servaldna.AsyncResult;
import org.servalproject.servaldna.ChannelSelector;
import org.servalproject.servaldna.IJniServer;
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
public class ServalD extends ServerControl implements IJniServer
{
	public static final String ACTION_STATUS = "org.servalproject.ACTION_STATUS";
	public static final String EXTRA_STATUS = "status";
	private static final String TAG = "ServalD";

	private long started = -1;
	private String status=null;
	private ServalDMonitor monitor;
	private final Context context;
	private ChannelSelector selector;
	private final ServalBatPhoneApplication app;

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
		this.app = ServalBatPhoneApplication.context;
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
		Log.i(TAG, "Starting servald background thread");
		try {
			synchronized (this) {
				started = -1;
				serverThread=new Thread(this.runServer, "Servald");
				serverThread.start();
				this.wait();
			}
		} catch (InterruptedException e) {
		}
		if (getPid()==0)
			throw new ServalDFailureException("Server didn't start");
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

/*
	If the android CPU suspends,
	the poll timeout that the daemon thread uses to run the next scheduled job will stop counting down
	So we schedule an alarm to fire just after the next job should run.
	If the CPU has not suspended, poll should return normally.
	If the alarm does fire, we know the CPU suspended. So we signal the other thread to wake up and
	for poll to return EINTR.
 */

	private PowerManager.WakeLock cpuLock;
	private volatile long wakeAt; // >0, wait until this SystemClock.elapsedRealtime(), 0 = running, -1 infinite wait.
	private AlarmManager am;

	@Override
	public long aboutToWait(long now, long nextRun, long nextWake) {
		// Release android lock and set an alarm
		// Note that we may need to allow this thread to enter poll() first
		// (all times are using the same clock as System.currentTimeMillis())
		long delay = (nextWake - now);
		synchronized (receiver) {
			if (delay>=100){
				// set an alarm for 10ms after the daemon wants to wake up.
				// if the CPU suspends the poll timeout will not elapse

				// more than one day? might as well be infinite!
				if (delay > 1000*60*60*24)
					wakeAt = -1;
				else
					wakeAt = SystemClock.elapsedRealtime() + delay + 100;

				app.runOnBackgroundThread(releaseLock, 1);
			}else{
				wakeAt = 0;
				if(alarmIntent !=null) {
					am.cancel(alarmIntent);
					alarmIntent = null;
				}
			}
		}

		return nextWake;
	}

	@Override
	public void wokeUp() {
		// hold wakelock until the next call to aboutToWait
		synchronized (receiver) {
			wakeAt = 0;
			if (!cpuLock.isHeld()) {
				cpuLock.acquire();
			}
			if(alarmIntent !=null) {
				am.cancel(alarmIntent);
				alarmIntent = null;
			}
		}
	}

	@Override
	public void started(String instancePath, int pid, int mdpPort, int httpPort) {
		started = System.currentTimeMillis();
		setStatus(instancePath, pid, mdpPort, httpPort);
		synchronized (this){
			this.notify();
		}
	}

	private static final String WAKE_INTENT = "org.servalproject.WAKE";
	private static final int SIGIO=29;

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// This should only occur if the CPU has suspended and we need to interrupt poll.
			if (intent.getAction().equals(WAKE_INTENT)) {
				alarmIntent = null;
				synchronized (receiver) {
					if (cpuLock!= null && !cpuLock.isHeld()) {
						cpuLock.acquire();
					}
					if (wakeAt!=0) {
						android.os.Process.sendSignal(serverTid, SIGIO);
					}
				}
			}
		}
	};

	private PendingIntent alarmIntent = null;

	private Runnable releaseLock = new Runnable(){
		@Override
		public void run() {
			PowerManager.WakeLock lock = cpuLock;
			Intent intent = new Intent(WAKE_INTENT);
			PendingIntent pe = PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

			synchronized (receiver){
				// last moment check that it is safe to release the lock
				if (lock != null && wakeAt>0) {
					alarmIntent = pe;
					am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, wakeAt, pe);
				}else if(alarmIntent !=null){
					am.cancel(alarmIntent);
					alarmIntent = null;
				}

				if (wakeAt!=0 && cpuLock.isHeld())
					cpuLock.release();
			}
		}
	};

	private Thread serverThread=null;
	private int serverTid=0;

	private Runnable runServer = new Runnable() {
		@Override
		public void run() {
			serverTid = Process.myTid();
			IntentFilter filter = new IntentFilter();
			filter.addAction(WAKE_INTENT);
			app.registerReceiver(receiver, filter);
			PowerManager pm = (PowerManager) app
					.getSystemService(Context.POWER_SERVICE);
			if (am==null)
				am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);

			cpuLock = pm
					.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Services");

			cpuLock.acquire();
			Log.v(TAG, "Calling native method server()");
			ServalDCommand.server(ServalD.this, "", null);

			// we don't currently stop the server, so this is effectively unreachable (and untested)

			wakeAt = 0;
			clearStatus();
			Log.v(TAG, "Returned from native method server()");
			app.unregisterReceiver(receiver);
			cpuLock.release();
			cpuLock = null;
			serverThread = null;
			serverTid = -1;
			synchronized (this){
				this.notify();
			}
		}
	};
}
