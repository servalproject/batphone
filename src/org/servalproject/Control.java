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
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDMonitor;
import org.servalproject.servald.SubscriberId;
import org.servalproject.system.WiFiRadio;
import org.servalproject.system.WifiMode;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
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
	private boolean radioOn = false;
	private boolean everythingRunning = false;
	private boolean serviceRunning = false;
	private SimpleWebServer webServer;
	private int peerCount = -1;

	public static final String ACTION_RESTART = "org.servalproject.restart";
	private static Control instance;

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(WiFiRadio.WIFI_MODE_ACTION)) {
				String newMode = intent
						.getStringExtra(WiFiRadio.EXTRA_NEW_MODE);
				radioOn = !(newMode == null || newMode.equals("Off"));

				Log.d("BatPhone", "Changing mode to " + newMode);
				if (newMode.equals("Off"))
					try {
						Log.d("BatPhone", "Trying to stop servald");
						stopServalD();
					} catch (ServalDFailureException e) {
						Log.e("BatPhone",
								"Failed to stop servald: " + e.toString(), e);
					}

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
		}
	};

	private Handler handler = new Handler();

	private Runnable notification = new Runnable() {
		@Override
		public void run() {
			handler.removeCallbacks(this);
			// TODO use peer list service?
			int this_peer_count = PeerListService.peerCount(Control.this);
			if (this_peer_count != peerCount) {
				peerCount = this_peer_count;
				updateNotification();
			}
			// we'll refresh based on the monitor callback, but that might fail
			handler.postDelayed(notification, 60000);
		}
	};

	private synchronized void modeChanged() {
		boolean wifiOn = radioOn;

		Log.d("BatPhone", "modeChanged() entered");

		// if the software is disabled, or the radio has cycled to sleeping,
		// make sure everything is turned off.
		if (!serviceRunning)
			wifiOn = false;

		if (wifiOn == everythingRunning)
			return;

		this.handler.removeCallbacks(notification);

		if (wifiOn) {
			Log.d("BatPhone", "wifiOn=true");
			try {
				startServalD();
			}
			catch (ServalDFailureException e) {
				Log.e("BatPhone", e.toString(), e);
			}
			try {
				if (webServer == null)
					webServer = new SimpleWebServer(new File(
							app.coretask.DATA_FILE_PATH + "/htdocs"), 8080);
			}
			catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}

			handler.post(notification);

		} else {
			try {
				Log.d("BatPhone", "Stopping ServalD");
				stopServalD();
			}
			catch (ServalDFailureException e) {
				Log.e("BatPhone", e.toString(), e);
			}
			if (webServer != null) {
				webServer.interrupt();
				webServer = null;
			}

			this.stopForeground(true);
		}
		everythingRunning = wifiOn;
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

	public static void stopServalD() throws ServalDFailureException {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		if (app.servaldMonitor != null) {
			app.servaldMonitor.stop();
			app.servaldMonitor = null;
		}
		ServalD.serverStop();
	}

	public static void restartServalD() throws ServalDFailureException {
		stopServalD();
		startServalD();
	}

	public static void startServalD() throws ServalDFailureException {
		final ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		ServalD.serverStart(app.coretask.DATA_FILE_PATH + "/bin/servald");

		if (app.servaldMonitor == null) {

			app.servaldMonitor = new ServalDMonitor(
					new Messages(app));

			new Thread(app.servaldMonitor, "Monitor").start();
			while (app.servaldMonitor.ready() == false) {
				try {
					Thread.sleep(100);
				} catch (Exception e) {
					// sleep until servald monitor is ready
				}
			}

		}
	}

	private synchronized void startService() {
		instance = this;

		Editor ed = app.settings.edit();
		ed.putBoolean("start_after_flight_mode", false);
		ed.commit();

		app.setState(State.Starting);
		try {
			app.wifiRadio.turnOn();

			app.setState(State.On);
		} catch (Exception e) {
			app.setState(State.Off);
			Log.e("BatPhone", e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		}
	}

	private synchronized void stopService() {
		app.setState(State.Stopping);
		try {
			WifiMode mode = app.wifiRadio.getCurrentMode();

			// If the current mode is Ap or Adhoc, the user will
			// probably want us to
			// turn off the radio.
			// If client mode, we'll ask them
			switch (mode) {
			case Adhoc:
			case Ap:
				app.wifiRadio.setWiFiMode(WifiMode.Off);
				break;
			}
			app.wifiRadio.checkAlarm();
		} catch (Exception e) {
			Log.e("BatPhone", e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		} finally {
			app.setState(State.Off);
			instance = null;
		}
		// Need ServalDMonitor to stop before we can actually
		// do this, else ServalDMonitor will start servald again.
		if (app.servaldMonitor != null)
			app.servaldMonitor.stop();
		try {
			stopServalD();
		} catch (ServalDFailureException e) {
			Log.e("BatPhone", "Failed to stop servald: " + e.toString(), e);
		}
	}

	private static class Messages implements ServalDMonitor.Messages {
		private final ServalBatPhoneApplication app;

		private Messages(ServalBatPhoneApplication app) {
			this.app = app;
		}

		@Override
		public int message(String cmd, Iterator<String> args,
				InputStream in, int dataBytes)
				throws IOException {

			int ret = 0;

			if (cmd.equals("NEWPEER")) {
				if (instance != null)
					instance.handler.post(instance.notification);
				try {
					SubscriberId sid = new SubscriberId(args
							.next());
					Peer p = PeerListService.getPeer(
							app.getContentResolver(), sid);
					PeerListService.notifyListeners(p);
				} catch (SubscriberId.InvalidHexException e) {
					IOException t = new IOException(e.getMessage());
					t.initCause(e);
					throw t;
				}
			} else if (cmd.equals("KEEPALIVE")) {
				// send keep alive to anyone who cares
				int local_session = ServalDMonitor.parseIntHex(args.next());
				if (app.callHandler != null)
					app.callHandler.keepAlive(local_session);
			} else if (cmd.equals("MONITOR")) {
				while (args.hasNext())
					Log.v("Control", args.next());
			} else if (cmd.equals("MONITORSTATUS")) {
				// returns monitor status
				int flags = ServalDMonitor.parseInt(args.next());
				if (app.callHandler != null)
					app.callHandler.monitor(flags);

			} else if (cmd.equals("AUDIOPACKET")) {
				// AUDIOPACKET:065384:66b07a:5:5:8:2701:2720
				int local_session = ServalDMonitor.parseIntHex(args.next());

				// remote_session
				args.next();

				// local_state
				args.next();

				// remote_state
				args.next();

				VoMP.Codec codec = VoMP.Codec.getCodec(ServalDMonitor
						.parseInt(args.next()));
				int start_time = ServalDMonitor.parseInt(args.next());
				int end_time = ServalDMonitor.parseInt(args.next());

				if (app.callHandler != null) {
					ret += app.callHandler.receivedAudio(
							local_session, start_time,
							end_time, codec, in, dataBytes);
				}

			} else if (cmd.equals("CALLSTATUS")) {
				try {
					int local_session = ServalDMonitor.parseIntHex(args.next());
					int remote_session = ServalDMonitor
							.parseIntHex(args.next());
					int local_state = ServalDMonitor.parseInt(args.next());
					int remote_state = ServalDMonitor.parseInt(args.next());
					int fast_audio = ServalDMonitor.parseInt(args.next());
					SubscriberId local_sid = new SubscriberId(args.next());
					SubscriberId remote_sid = new SubscriberId(args.next());

					String local_did = null, remote_did = null;
					if (args.hasNext())
						local_did = args.next();

					if (args.hasNext())
						remote_did = args.next();

					if (app.callHandler == null) {

						if (local_state <= VoMP.State.NoCall.code
								&& remote_state <= VoMP.State.NoCall.code) {
							Log.d("ServalDMonitor",
									"Ignoring call in NOCALL state");
							return ret;
						}

						if (local_state < VoMP.State.RingingOut.code
								|| local_state >= VoMP.State.CallEnded.code
								|| remote_state >= VoMP.State.CallEnded.code) {
							Log.d("ServalDMonitor",
									"Ignoring call in NOCALL or CALLENDED state");
							return ret;
						}

						if (local_session == 0)
							return ret;

						app.callHandler = new CallHandler(
								PeerListService.getPeer(
										ServalBatPhoneApplication.context
												.getContentResolver(),
										remote_sid));
					}

					app.callHandler.notifyCallStatus(local_session,
							remote_session,
							local_state, remote_state, fast_audio,
							local_sid, remote_sid, local_did,
							remote_did);

					// localtoken:remotetoken:localstate:remotestate
				} catch (SubscriberId.InvalidHexException e) {
					throw new IOException("invalid SubscriberId token: " + e);
				}
			} else if (cmd.equals("BUNDLE")) {
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
				app.servaldMonitor.sendMessage("monitor vomp");
				app.servaldMonitor
						.sendMessage("monitor rhizome");
				app.servaldMonitor.sendMessage("monitor peers");
				// make sure we refresh the peer count after
				// reconnecting to the monitor
				if (instance != null)
					instance.handler
							.post(instance.notification);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	class Task extends AsyncTask<State, Object, Object> {
		@Override
		protected Object doInBackground(State... params) {
			if (params[0] == null) {
				if (app.getState() != State.Off)
					stopService();
				startService();
				return null;
			}

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

		IntentFilter filter = new IntentFilter();
		filter.addAction(WiFiRadio.WIFI_MODE_ACTION);
		registerReceiver(receiver, filter);

		super.onCreate();
	}

	@Override
	public void onDestroy() {
		new Task().execute(State.Off);
		unregisterReceiver(receiver);
		serviceRunning = false;
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = null;
		if (intent != null)
			action = intent.getAction();
		if (ACTION_RESTART.equals(action))
			new Task().execute((State) null);
		else
			new Task().execute(State.On);
		serviceRunning = true;
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

}
