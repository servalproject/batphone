package org.servalproject;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.servald.PeerListService;

/**
 *
 * Control service responsible for enabling wifi network, and keeping our process alive
 *
 */
public class Control extends Service {
	private ServalBatPhoneApplication app;
	private boolean showingNotification = false;
	private int peerCount = -1;
	private static final String TAG = "Control";

	private void updateNotification() {
		if (app.controlService != this || peerCount<0){
			if (showingNotification)
				this.stopForeground(true);
			showingNotification = false;
			return;
		}

		Notification notification = new Notification(
				R.drawable.ic_serval_logo, getString(R.string.app_name),
				System.currentTimeMillis());

		Intent intent = new Intent(app, Main.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		notification.setLatestEventInfo(Control.this, getString(R.string.app_name),
				app.getResources().getQuantityString(R.plurals.peers_label, peerCount, peerCount),
				PendingIntent.getActivity(app, 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT));

		notification.flags = Notification.FLAG_ONGOING_EVENT;
		this.startForeground(-1, notification);
		showingNotification = true;
	}

	public void updatePeerCount(int peerCount) {
		if (this.peerCount == peerCount)
			return;
		this.peerCount = peerCount;
		updateNotification();
	}

	@Override
	public void onCreate() {
		this.app = (ServalBatPhoneApplication) this.getApplication();
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		app.controlService = null;
		peerCount = -1;
		updateNotification();
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		State existing = app.getState();
		// Don't attempt to start the service if the current state is invalid
		// (ie Installing...)
		if (existing != State.Running) {
			Log.v("Control", "Unable to process request as app state is "
					+ existing);
			return START_NOT_STICKY;
		}

		peerCount =-1;
		app.controlService = this;
		updatePeerCount(PeerListService.getLastPeerCount());

		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

}
