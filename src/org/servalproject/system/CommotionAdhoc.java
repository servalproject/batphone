package org.servalproject.system;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by jeremy on 28/08/13.
 */
public class CommotionAdhoc extends NetworkConfiguration {
	public final static int STATE_STOPPED  = 0;
	public final static int STATE_STARTING = 1;
	public final static int STATE_RUNNING  = 2; // process said OK

	public final static String STATE_EXTRA = "state";

	public final static String PACKAGE_NAME = "net.commotionwireless.meshtether";
	public final static String TOGGLE_STATE = "net.commotionwireless.meshtether.TOGGLE_STATE";
	public final static String CHANGE_STATE = "net.commotionwireless.meshtether.CHANGE_STATE";

	public static final String ACTION_STATE_CHANGED = "net.commotionwireless.meshtether.STATE_CHANGED";

	private static final String TAG = "MeshTether";

	private static String appName;
	private static boolean needsPermission;

	private int state = -1;
	ScanResults results;

	public static boolean isInstalled(){
		final PackageManager packageManager = ServalBatPhoneApplication.context
				.getPackageManager();
		try {
			appName = packageManager.getApplicationLabel(packageManager.getApplicationInfo(PACKAGE_NAME, 0)).toString();
			needsPermission = (packageManager.checkPermission("org.servalproject", CHANGE_STATE)==packageManager.PERMISSION_GRANTED);
		} catch (Throwable t) {
			// ignore and assume the package doesn't exist
			Log.v(TAG, t.getMessage(), t);
		}
		return appName!=null;
	}

	public void onStateChanged(int state){
		if (this.state==state)
			return;
		Log.v(TAG, "State changed from "+this.state+" to "+state);
		this.state=state;
	}

	public void enable(Context context, boolean enable){
		Intent i = new Intent(TOGGLE_STATE);
		i.putExtra("start", enable);
		Log.v(TAG,"Sending broadcast "+TOGGLE_STATE+", "+enable);
		if (needsPermission)
			context.sendBroadcast(i, CHANGE_STATE);
		else
			context.sendBroadcast(i);
	}

	@Override
	public String getSSID() {
		return appName;
	}

	public int getState(){
		return state;
	}

	public boolean isActive(){
		return state==STATE_RUNNING || state == STATE_STARTING;
	}

	@Override
	public String getStatus(Context context) {
		switch(getState()){
			case STATE_RUNNING:
				return context.getString(R.string.wifi_enabled);
			case STATE_STARTING:
				return context.getString(R.string.wifi_enabling);
			case STATE_STOPPED:
				return context.getString(R.string.wifi_disabled);
		}

		return null;
	}

	@Override
	public int getBars() {
		return results == null ? -1 : results.getBars();
	}

	@Override
	public InetAddress getAddress() throws UnknownHostException {
		return null;
	}

	@Override
	public String getType() {
		return "Mesh";
	}

	@Override
	public String toString() {
		return this.getSSID();
	}
}
