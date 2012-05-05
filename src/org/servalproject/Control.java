package org.servalproject;

import java.io.File;
import java.io.IOException;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.batphone.UnsecuredCall;
import org.servalproject.batphone.VoMP;
import org.servalproject.servald.Identities;
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
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

public class Control extends Service {
	private ServalBatPhoneApplication app;
	private boolean radioOn = false;
	private boolean everythingRunning = false;
	private boolean serviceRunning = false;
	private SimpleWebServer webServer;
	private PowerManager powerManager;
	private int lastPeerCount = -1;

	public static final String ACTION_RESTART = "org.servalproject.restart";

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(WiFiRadio.WIFI_MODE_ACTION)) {
				String newMode = intent
						.getStringExtra(WiFiRadio.EXTRA_NEW_MODE);
				radioOn = !(newMode == null || newMode.equals("Off"));

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

	private Handler handler;

	private Runnable notification = new Runnable() {
		@Override
		public void run() {
			int last_peer_count = 0;
			if (powerManager.isScreenOn()) {
				// XXX - Should cache instead of poll every second
				int this_peer_count = Identities.getPeerCount();
				if (this_peer_count != last_peer_count)
					updateNotification();
				last_peer_count = this_peer_count;
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Log.e("BatPhone", e.toString(), e);
				}
			}
			handler.postDelayed(notification, 1000);
		}
	};

	private synchronized void modeChanged() {
		boolean wifiOn = radioOn;

		// if the software is disabled, or the radio has cycled to sleeping,
		// make sure everything is turned off.
		if (!serviceRunning)
			wifiOn = false;

		if (wifiOn == everythingRunning)
			return;

		if (this.handler == null) {
			// Don't crash if looper has already been prepared.
			// It has happened even inside this.handler check.
			try {
				Looper.prepare();
			} catch (Exception e) {

			}
			this.handler = new Handler();
		}

		this.handler.removeCallbacks(notification);

		if (wifiOn) {
			try {
				startServalD();

				if (webServer == null)
					webServer = new SimpleWebServer(new File(
							app.coretask.DATA_FILE_PATH + "/htdocs"), 8080);

			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}

			updateNotification();
			handler.postDelayed(this.notification, 1000);

		} else {
			try {

				stopServalD();

				if (webServer != null) {
					webServer.interrupt();
					webServer = null;
				}

			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}

			this.stopForeground(true);
		}
		everythingRunning = wifiOn;
	}

	private void updateNotification() {
		int peerCount = Identities.getPeerCount();

		Notification notification = new Notification(
				R.drawable.start_notification, "Serval Mesh",
				System.currentTimeMillis());

		Intent intent = new Intent(app, Main.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		notification.setLatestEventInfo(Control.this, "Serval Mesh", peerCount
				+ " Phone(s)", PendingIntent.getActivity(app, 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT));

		notification.flags = Notification.FLAG_ONGOING_EVENT;
		notification.number = peerCount;
		this.startForeground(-1, notification);

		lastPeerCount = peerCount;
	}

	public static void stopServalD() throws IOException {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		if (app.coretask.isProcessRunning("bin/servald")) {
			Log.v("BatPhone", "Stopping Serval Daemon");
			app.coretask.runCommand(app.coretask.DATA_FILE_PATH
					+ "/bin/servald stop");
			if (false) {
				try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Log.e("BatPhone", e.toString(), e);
				}
			}
			// last resort
			app.coretask.killProcess("bin/servald", false);
		}
		if (app.servaldMonitor != null)
			app.servaldMonitor.stop();
		app.servaldMonitor = null;
	}

	public static void restartServalD() throws IOException {
		stopServalD();
		startServalD();
	}

	public static void startServalD() throws IOException {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		if (!app.coretask.isProcessRunning("bin/servald")) {
			Log.v("BatPhone", "Starting Serval Daemon");
			boolean instrumentation = app.settings.getBoolean("instrument_rec",
					false);
			Boolean gateway = app.settings.getBoolean("gatewayenable", false);

			// Make sure that servald knows that it has stopped
			// (asking twice makes sure it works it seems)
			app.coretask.runCommand(app.coretask.DATA_FILE_PATH
					+ "/bin/servald stop");
			app.coretask.runCommand(app.coretask.DATA_FILE_PATH
					+ "/bin/servald stop");

			app.coretask.runCommand(app.coretask.DATA_FILE_PATH
					+ "/bin/servald start");
		}
		if (app.servaldMonitor == null) {
			app.servaldMonitor = new ServalDMonitor() {
				@Override
				protected void notifyCallStatus(int l_id, int r_id,
						int l_state, int r_state,
						SubscriberId l_sid, SubscriberId r_sid,
						String l_did, String r_did) {
					ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
					// Ignore state glitching from servald
					if (l_state <= VoMP.STATE_NOCALL
							&& r_state <= VoMP.STATE_NOCALL)
						return;
					if (app.vompCall == null
							&& SystemClock.elapsedRealtime() > (app.lastVompCallTime + 4000))
					{
						app.lastVompCallTime = SystemClock.elapsedRealtime();
						// Ignore state glitching from servald
						// (don't create a call for something that is not worth
						// reporting.
						// If the call status becomes interesting, we will pick
						// it up then).
						if (l_state == VoMP.STATE_NOCALL
								|| l_state == VoMP.STATE_CALLENDED)
							return;

						if (l_id != 0) {
							// start VoMP call activity
							Intent myIntent = new Intent(ServalBatPhoneApplication.context,
									UnsecuredCall.class);
							myIntent.putExtra("incoming_call_session", ""
									+ l_id);
							myIntent.putExtra("sid", r_sid.toString());
							myIntent.putExtra("did", r_did);
							myIntent.putExtra("local_state", "" + l_state);
							myIntent.putExtra("remote_state", "" + r_state);
							// Create call as a standalone activity stack
							myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							// Uncomment below if we want to allow multiple mesh calls in progress
							// myIndent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
							ServalBatPhoneApplication.context.startActivity(myIntent);
						}
					} else if (app.vompCall != null) {
						UnsecuredCall v = app.vompCall;
						v.notifyCallStatus(l_id, r_id, l_state, r_state, l_sid,
								r_sid, l_did, r_did);
					}
				}
			};
			new Thread(app.servaldMonitor).start();
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

		powerManager = (PowerManager) app
				.getSystemService(Context.POWER_SERVICE);

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
