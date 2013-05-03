package org.servalproject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.batphone.CallHandler;
import org.servalproject.batphone.VoMP;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeManifest;
import org.servalproject.servald.BundleId;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDMonitor;
import org.servalproject.servald.SubscriberId;
import org.servalproject.system.WifiControl;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

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

		this.handler.removeCallbacks(notification);
		multicastLock.acquire();
		Log.d("BatPhone", "wifiOn=true, multicast lock acquired");
		try {
			startServalD();
		} catch (ServalDFailureException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		try {
			if (webServer == null)
				webServer = new SimpleWebServer(new File(
						app.coretask.DATA_FILE_PATH + "/htdocs"), 8080);
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

		notification.setLatestEventInfo(Control.this, "Serval Mesh", peerCount
				+ 1
				+ " Phone(s)", PendingIntent.getActivity(app, 0, intent,
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

	public static void reloadConfig() throws ServalDFailureException {
		if (ServalD.serverIsRunning()) {
			// restart servald without restarting the monitor interface.
			ServalBatPhoneApplication.context.updateStatus("Restarting");
			ServalD.serverStop();
			ServalD.serverStart();
		}
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
			app.servaldMonitor = new ServalDMonitor(
					new Messages(app));
			CallHandler.registerMessageHandlers(app.servaldMonitor);
			new Thread(app.servaldMonitor, "Monitor").start();
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

		Editor ed = app.settings.edit();
		ed.putBoolean("start_after_flight_mode", false);
		ed.commit();

		app.setState(State.Starting);
		try {
			// app.wifiRadio.turnOn();
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

	public static PeerList peerList;

	private void updatePeerCount() {
		try {
			peerCount = ServalD.getPeerCount();
			app.updateStatus(peerCount + " peers");
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

			if (cmd.equalsIgnoreCase("NEWPEER")
					|| cmd.equalsIgnoreCase("OLDPEER")) {
				try {
					SubscriberId sid = new SubscriberId(args
							.next());
					PeerListService
							.peerReachable(app.getContentResolver(), sid,
									cmd.equals("NEWPEER"));
				} catch (SubscriberId.InvalidHexException e) {
					IOException t = new IOException(e.getMessage());
					t.initCause(e);
					throw t;
				}

				updatePeerCount();

            } else if(cmd.equalsIgnoreCase("LINK")) {
                try{
                    int hop_count = ServalDMonitor.parseInt(args.next());
                    String sid = args.next();
                    SubscriberId transmitter = sid.equals("")?null:new SubscriberId(sid);
                    SubscriberId receiver = new SubscriberId(args.next());
                    PeerListService.linkChanged(app.getContentResolver(), hop_count, transmitter, receiver);

                } catch (SubscriberId.InvalidHexException e) {
                    IOException t = new IOException(e.getMessage());
                    t.initCause(e);
                    throw t;
                }

			} else if (cmd.equalsIgnoreCase("KEEPALIVE")) {
				// send keep alive to anyone who cares
				int local_session = ServalDMonitor.parseIntHex(args.next());
				if (app.callHandler != null)
					app.callHandler.keepAlive(local_session);
			} else if (cmd.equalsIgnoreCase("INFO")) {
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
				if (app.callHandler != null)
					app.callHandler.monitor(flags);

				// make sure we refresh the peer count after
				// reconnecting to the monitor interface
				if (flags == (ServalDMonitor.MONITOR_VOMP |
						ServalDMonitor.MONITOR_RHIZOME | ServalDMonitor.MONITOR_PEERS)) {
					if (peerList != null)
						peerList.monitorConnected();

					updatePeerCount();
				}

			} else if (cmd.equalsIgnoreCase("AUDIO")) {
				int local_session = ServalDMonitor.parseIntHex(args.next());

				VoMP.Codec codec = VoMP.Codec.getCodec(ServalDMonitor
						.parseInt(args.next()));
				int start_time = ServalDMonitor.parseInt(args.next());
				args.next(); // sequence
				int jitter_delay = ServalDMonitor.parseInt(args.next());
				int this_delay = ServalDMonitor.parseInt(args.next());

				if (app.callHandler != null) {
					ret += app.callHandler.receivedAudio(
							local_session, start_time, jitter_delay,
							this_delay,
							codec, in, dataBytes);
				}
			} else if (cmd.equalsIgnoreCase("HANGUP")) {
				if (app.callHandler == null)
					return ret;
				int local_session = ServalDMonitor.parseIntHex(args.next());
				app.callHandler.remoteHangUp(local_session);

			} else if (cmd.equalsIgnoreCase("CALLSTATUS")) {
				if (app.callHandler == null)
					return ret;

				try {
					int local_session = ServalDMonitor.parseIntHex(args.next());
					args.next(); // remote_session
					int local_state = ServalDMonitor.parseInt(args.next());
					int remote_state = ServalDMonitor.parseInt(args.next());
					args.next(); // fast_audio
					args.next(); // local_sid
					SubscriberId remote_sid = new SubscriberId(args.next());

					app.callHandler.notifyCallStatus(local_session,
							local_state, remote_state, remote_sid);

				} catch (SubscriberId.InvalidHexException e) {
					throw new IOException("invalid SubscriberId token: " + e);
				}

			} else if (cmd.equalsIgnoreCase("CODECS")) {
				int local_session = ServalDMonitor.parseIntHex(args.next());
				if (app.callHandler != null)
					app.callHandler.codecs(local_session, args);

			} else if (cmd.equalsIgnoreCase("BUNDLE")) {
				try {
					String manifestId=args.next();
					BundleId bid=new BundleId(manifestId);

					RhizomeManifest manifest = Rhizome.readManifest(bid);
					Rhizome.notifyIncomingBundle(manifest);
				} catch (Exception e) {
					Log.v("ServalDMonitor", e.getMessage(), e);
				}
			} else {
				Log.i("ServalDMonitor",
						"Unhandled monitor cmd " + cmd);
			}
			return ret;
		}

		@Override
		public void connected() {
			try {
				app.updateStatus("Running");
				// tell servald that we can initiate and answer phone calls, and
				// the list of codecs we support
				app.servaldMonitor.sendMessage("monitor vomp "
						+ VoMP.Codec.Signed16.codeString + " "
						+ VoMP.Codec.Ulaw8.codeString + " "
						+ VoMP.Codec.Alaw8.codeString);
				app.servaldMonitor
						.sendMessage("monitor rhizome");
				app.servaldMonitor.sendMessage("monitor peers");
                app.servaldMonitor.sendMessage("monitor links");
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
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
