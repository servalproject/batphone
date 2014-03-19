package org.servalproject;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.batphone.CallHandler;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeManifest;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalDMonitor;
import org.servalproject.servaldna.BundleId;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.ServalDFailureException;
import org.servalproject.system.WifiControl;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 *
 * Control service responsible for turning Serval on and off and changing the
 * Wifi radio mode.
 *
 *
 */
public class Control extends Service {
	private ServalBatPhoneApplication app;
	private boolean servicesRunning = false;
	private boolean serviceRunning = false;
	private SimpleWebServer webServer;
	private int peerCount = -1;
	private PowerManager.WakeLock cpuLock;
	private WifiControl.AlarmLock alarmLock;
	private WifiManager.MulticastLock multicastLock = null;

	public void onNetworkStateChanged() {
		if (serviceRunning) {
			new AsyncTask<Object, Object, Object>() {
				@Override
				protected Object doInBackground(Object... params) {
					modeChanged();
					return null;
				}
			}.execute();
		}
	}

	private Handler handler = new Handler();

	private Runnable notification = new Runnable() {
		@Override
		public void run() {
			handler.removeCallbacks(this);
			// TODO use peer list service?
			updateNotification();
			// we'll refresh based on the monitor callback, but that might fail
			handler.postDelayed(notification, 60000);
		}
	};

	private Runnable stopService = new Runnable() {
		@Override
		public void run() {
			handler.removeCallbacks(this);
			stopServices();
		}
	};

	private synchronized void startServices() {
		handler.removeCallbacks(stopService);

		if (servicesRunning)
			return;
		cpuLock.acquire();
		multicastLock.acquire();
		this.handler.removeCallbacks(notification);
		Log.d("BatPhone", "wifiOn=true, multicast lock acquired");
		try {
			startServalD();
		} catch (ServalDFailureException e) {
			app.displayToastMessage(e.getMessage());
			Log.e("BatPhone", e.toString(), e);
		}
		try {
			if (webServer == null)
				webServer = new SimpleWebServer(8080);
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}

		updatePeerCount();

		servicesRunning = true;
	}

	private synchronized void stopServices() {
		if (!servicesRunning)
			return;

		handler.removeCallbacks(notification);
		multicastLock.release();
		try {
			Log.d("BatPhone", "Stopping ServalD, released multicast lock");
			stopServalD();
		} catch (ServalDFailureException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		if (webServer != null) {
			webServer.interrupt();
			webServer = null;
		}

		this.stopForeground(true);
		if (alarmLock != null)
			alarmLock.change(false);
		app.updateStatus("Off");
		servicesRunning = false;
		cpuLock.release();
	}

	private synchronized void modeChanged() {
		boolean wifiOn = app.nm.isUsableNetworkConnected();

		Log.d("BatPhone", "modeChanged() entered");

		// if the software is disabled, or the radio has cycled to sleeping,
		// make sure everything is turned off.
		if (!serviceRunning)
			wifiOn = false;

		if (multicastLock == null)
		{
			WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			multicastLock = wm.createMulticastLock("org.servalproject");
		}

		if (wifiOn) {
			startServices();
		} else {
			handler.postDelayed(stopService, 5000);
		}
	}

	private void updateNotification() {
		Notification notification = new Notification(
				R.drawable.ic_serval_logo, "Serval Mesh",
				System.currentTimeMillis());

		Intent intent = new Intent(app, Main.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		notification.setLatestEventInfo(Control.this, "Serval Mesh",
				app.getString(R.string.peers_label, Integer.toString(peerCount)),
				PendingIntent.getActivity(app, 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT));

		notification.flags = Notification.FLAG_ONGOING_EVENT;
		this.startForeground(-1, notification);
	}

	private void stopServalD() throws ServalDFailureException {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		if (app.servaldMonitor != null) {
			app.servaldMonitor.stop();
			app.servaldMonitor = null;
		}
		ServalD.serverStop();
	}

	// make sure servald is running
	// only return success when we have established a monitor connection
	private void startServalD() throws ServalDFailureException {
		final ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		if (app.servaldMonitor != null && app.servaldMonitor.ready())
			return;
		app.updateStatus("Starting");
		ServalD.serverStart();

		if (app.servaldMonitor == null) {
			ServalDMonitor monitor = new ServalDMonitor();
			app.servaldMonitor = monitor;
			monitor.addHandler("", new Messages(app));
			CallHandler.registerMessageHandlers(monitor);
			PeerListService.registerMessageHandlers(monitor);

			new Thread(monitor, "Monitor").start();
		}

		// sleep until servald monitor is ready
		while (app.servaldMonitor != null
				&& !app.servaldMonitor.ready()) {
			try {
				Thread.sleep(100);
			} catch (Exception e) {
			}
		}
	}

	private synchronized void startService() {
		app.controlService = this;
		app.setState(State.Starting);
		try {
			this.modeChanged();
			app.setState(State.On);
		} catch (Exception e) {
			app.setState(State.Off);
			Log.e("BatPhone", e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		}
	}

	private synchronized void stopService() {
		app.setState(State.Stopping);
		app.nm.onStopService();
		stopServices();
		app.setState(State.Off);

		// Need ServalDMonitor to stop before we can actually
		// do this, else ServalDMonitor will start servald again.
		if (app.servaldMonitor != null)
			app.servaldMonitor.stop();
		app.controlService = null;
	}

	public void updatePeerCount() {
		try {
			peerCount = ServalDCommand.peerCount();
			app.updateStatus(app.getString(R.string.peers_label, Integer.toString(peerCount)));
			handler.post(notification);

			if (alarmLock == null) {
				alarmLock = app.nm.control.getLock("Peers");
			}
			alarmLock.change(peerCount > 0);
		} catch (ServalDFailureException e) {
			Log.e("Control", e.toString(), e);
		}
	}

	private class Messages implements ServalDMonitor.Messages {
		private final ServalBatPhoneApplication app;

		private Messages(ServalBatPhoneApplication app) {
			this.app = app;
		}

		@Override
		public int message(String cmd, Iterator<String> args,
				InputStream in, int dataBytes)
				throws IOException {

			int ret = 0;

			if (cmd.equalsIgnoreCase("INFO")) {
				StringBuilder sb = new StringBuilder();
				while (args.hasNext()) {
					if (sb.length() != 0)
						sb.append(" ");
					sb.append(args.next());
				}
				Log.v("Control", sb.toString());
			} else if (cmd.equalsIgnoreCase("MONITORSTATUS")) {
				// returns monitor status
				int flags = ServalDMonitor.parseInt(args.next());

				// make sure we refresh the peer count after
				// reconnecting to the monitor interface
				if (flags == (ServalDMonitor.MONITOR_VOMP |
						ServalDMonitor.MONITOR_RHIZOME | ServalDMonitor.MONITOR_PEERS)) {

					updatePeerCount();
				}

			} else if (cmd.equalsIgnoreCase("BUNDLE")) {
				try {
					String manifestId=args.next();
					BundleId bid=new BundleId(manifestId);
					RhizomeManifest manifest;
					if (dataBytes > 0) {
						byte manifestBytes[] = new byte[dataBytes];
						int offset = 0;
						while (offset < dataBytes) {
							int read = in.read(manifestBytes, offset, dataBytes
									- offset);
							if (read < 0)
								throw new EOFException();
							offset += read;
							ret += read;
						}
						manifest = RhizomeManifest.fromByteArray(manifestBytes);
					} else {
						manifest = Rhizome.readManifest(bid);
					}
					Rhizome.notifyIncomingBundle(manifest);
				} catch (Exception e) {
					Log.v("ServalDMonitor", e.getMessage(), e);
				}
			} else if (cmd.equals("ERROR")) {
				while (args.hasNext())
					Log.e("ServalDMonitor", args.next());
			} else {
				Log.i("ServalDMonitor",
						"Unhandled monitor cmd " + cmd);
			}
			return ret;
		}

		@Override
		public void onConnect(ServalDMonitor monitor) {
			try {
				app.updateStatus("Running");
				monitor.sendMessage("monitor rhizome");
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public void onDisconnect(ServalDMonitor monitor) {

		}
	}

	class Task extends AsyncTask<State, Object, Object> {
		@Override
		protected Object doInBackground(State... params) {
			if (app.getState() == params[0])
				return null;

			if (params[0] == State.Off) {
				stopService();
			} else {
				startService();
			}
			return null;
		}
	}

	@Override
	public void onCreate() {
		this.app = (ServalBatPhoneApplication) this.getApplication();
		PowerManager pm = (PowerManager) app
				.getSystemService(Context.POWER_SERVICE);
		cpuLock = pm
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Services");

		super.onCreate();
	}

	@Override
	public void onDestroy() {
		new Task().execute(State.Off);
		app.controlService = null;
		serviceRunning = false;
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		State existing = app.getState();
		// Don't attempt to start the service if the current state is invalid
		// (ie Installing...)
		if (existing != State.Off && existing != State.On) {
			Log.v("Control", "Unable to process request as app state is "
					+ existing);
			return START_NOT_STICKY;
		}

		new Task().execute(State.On);

		serviceRunning = true;
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

}
