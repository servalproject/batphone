package org.servalproject.batphone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.servald.PeerListService;
import org.servalproject.system.CommotionAdhoc;
import org.servalproject.system.NetworkManager;
import org.servalproject.system.WifiAdhocControl;
import org.servalproject.system.WifiApControl;


public class BatPhone extends BroadcastReceiver {

	static BatPhone instance = null;

	public BatPhone() {
		instance = this;
	}

	public static BatPhone getEngine() {
		// TODO Auto-generated method stub
		if (instance == null)
			instance = new BatPhone();
		return instance;
	}

	public static void call(String phoneNumber) {
		// make call by cellular/normal means
		// we need to ignore this number when it is dialed in the next 3 seconds

		dial_time = SystemClock.elapsedRealtime();
		dialed_number = phoneNumber;

		String url = "tel:" + phoneNumber;
		Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		ServalBatPhoneApplication.context.startActivity(intent);
	}

	static String dialed_number = null;
	static long dial_time = 0;

	public static String getDialedNumber()
	{
		return dialed_number;
	}

	private void onOutgoingCall(Intent intent) {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;

		if (app.getState() != State.On)
			return;
		if (!NetworkManager.getNetworkManager(app).isUsableNetworkConnected())
			return;
		if (!PeerListService.havePeers())
			return;

		String number = getResultData();

		// Set result data to null if we are claiming the call, else set
		// the result data to the number so that someone else can take
		// it.

		// Let the system complete the call if we have said we aren't
		// interested in it.
		if (dialed_number != null && dialed_number.equals(number)
				&& (SystemClock.elapsedRealtime() - dial_time) < 3000) {
			return;
		}

		// Don't try to complete the call while we are
		// giving the user the choice of how to handle it.
		setResultData(null);

		// Send call to director to select how to handle it.
		Intent myIntent = new Intent(app, CallDirector.class);
		// Create call as a standalone activity stack
		myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		myIntent.putExtra("phone_number", number);
		// Uncomment below if we want to allow multiple mesh calls in
		// progress
		// myIndent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		app.startActivity(myIntent);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;

		try {
			// Log.d("BatPhoneReceiver", "Got an intent: " + intent.toString());

			if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
				onOutgoingCall(intent);

			} else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
				// placeholder to ensure ServalBatPhoneApplication is created on
				// boot
				// WifiControl will restart adhoc if required
				// ServalBatPhoneApplication will re-enable services if required

			} else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
				if (app.nm != null)
					app.nm.onFlightModeChanged(intent);

			} else if (action.equals(Intent.ACTION_MEDIA_EJECT)
					|| action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
				Rhizome.setRhizomeEnabled(false);

			} else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
				Rhizome.setRhizomeEnabled();

			} else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				if (app.nm != null)
					app.nm.control.onWifiStateChanged(intent);
				if (app.controlService != null)
					app.controlService.onNetworkStateChanged();

			} else if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
				// TODO force network connections in the background?

			} else if (action
					.equals(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION)) {
				if (app.nm != null)
					app.nm.control.onApStateChanged(intent);
				if (app.controlService != null)
					app.controlService.onNetworkStateChanged();

			} else if (action
					.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
				if (app.nm != null)
					app.nm.control.onSupplicantStateChanged(intent);

			} else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
				if (app.controlService != null)
					app.controlService.onNetworkStateChanged();

			} else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				// TODO?

			} else if (action
					.equals(WifiAdhocControl.ADHOC_STATE_CHANGED_ACTION)) {
				if (app.controlService != null)
					app.controlService.onNetworkStateChanged();

			} else if (action.equals(CommotionAdhoc.ACTION_STATE_CHANGED)) {
				int state = intent.getIntExtra(CommotionAdhoc.STATE_EXTRA, -1);
				if (app.nm != null)
					app.nm.control.commotionAdhoc.onStateChanged(state);
				if (app.controlService != null)
					app.controlService.onNetworkStateChanged();


			} else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
				if (app.nm != null && app.nm.blueToothControl != null)
					app.nm.blueToothControl.onFound(intent);
			} else if (action.equals(BluetoothDevice.ACTION_NAME_CHANGED)) {
				if (app.nm != null && app.nm.blueToothControl != null)
					app.nm.blueToothControl.onRemoteNameChanged(intent);

			} else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
				if (app.nm != null && app.nm.blueToothControl != null)
					app.nm.blueToothControl.onDiscoveryStarted();
			} else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
				if (app.nm != null && app.nm.blueToothControl != null)
					app.nm.blueToothControl.onDiscoveryFinished();
			} else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				if (app.nm != null && app.nm.blueToothControl != null)
					app.nm.blueToothControl.onStateChange(intent);
			} else if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
				if (app.nm != null && app.nm.blueToothControl != null)
					app.nm.blueToothControl.onScanModeChanged(intent);
			} else if (action.equals(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)) {
				if (app.nm != null && app.nm.blueToothControl != null)
					app.nm.blueToothControl.onNameChanged(intent);

			} else {
				Log.v("BatPhone", "Unexpected intent: " + intent.getAction());
			}
		} catch (Exception e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}
}
