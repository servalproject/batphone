package android.tether;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

public class TetherApplication extends Application {

	public static final String MSG_TAG = "TETHER -> TetherApplication";
	
	// WifiManager
	private WifiManager wifiManager;
	
	// PowerManagement
	private PowerManager powerManager = null;
	private PowerManager.WakeLock wakeLock = null;
	
	// Preferences
	SharedPreferences settings = null;
	
	// Sync
	public static final Uri CONTENT_URI = Uri.parse("content://sync/settings");
	public static final String KEY = "name";
	public static final String VALUE = "value";
	private static final String[] PROJECTION = { KEY, VALUE };
	public static final String SETTING_LISTEN_FOR_TICKLES = "listen_for_tickles";
    public static final String SETTING_BACKGROUND_DATA = "background_data";		
	
	// Original States
	private static boolean origWifiState = false;
	public static boolean origTickleState = false;
	public static boolean origBackState = false;	
	
	@Override
	public void onCreate() {
		Log.d(MSG_TAG, "Calling onCreate()");
		// Preferences
		settings = PreferenceManager.getDefaultSharedPreferences(this);
        // init wifiManager
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE); 
		// Powermanagement
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "TETHER_WAKE_LOCK");
        // Original sync states
		origTickleState = getBoolean(getContentResolver(), SETTING_LISTEN_FOR_TICKLES, true);
		origBackState = getBoolean(getContentResolver(), SETTING_BACKGROUND_DATA, true);

	}

	@Override
	public void onTerminate() {
		Log.d(MSG_TAG, "Calling onTerminate()");
	}
	
    //gets user preference on whether wakelock should be disabled during tethering
    public boolean getWakeLock(){
		return this.settings.getBoolean("wakelockpref", false);
	} 
	
    //gets user preference on whether wakelock should be disabled during tethering
    public boolean getSync(){
		return this.settings.getBoolean("syncpref", false);
	}
    
    // Wifi
    public void disableWifi() {
    	if (this.wifiManager.isWifiEnabled()) {
    		origWifiState = true;
    		this.wifiManager.setWifiEnabled(false);
    		Log.d(MSG_TAG, "Wifi disabled!");
        	// Waiting for interface-shutdown
    		try {
    			Thread.sleep(5000);
    		} catch (InterruptedException e) {
    			// nothing
    		}
    	}
    }
    
    public void enableWifi() {
    	if (origWifiState) {
    		this.wifiManager.setWifiEnabled(true);
    		Log.d(MSG_TAG, "Wifi started!");
    	}
    }
    
    // WakeLock
	public void releaseWakeLock() {
		try {
			if(this.wakeLock != null && this.wakeLock.isHeld()) {
				Log.d(MSG_TAG, "Trying to release WakeLock NOW!");
				this.wakeLock.release();
			}
		} catch (Exception ex) {
			Log.d(MSG_TAG, "Ups ... an exception happend while trying to release WakeLock - Here is what I know: "+ex.getMessage());
		}
	}
    
	public void acquireWakeLock() {
		try {
			if (this.getWakeLock() == false) {
				Log.d(MSG_TAG, "Trying to acquire WakeLock NOW!");
				this.wakeLock.acquire();
			}
		} catch (Exception ex) {
			Log.d(MSG_TAG, "Ups ... an exception happend while trying to acquire WakeLock - Here is what I know: "+ex.getMessage());
		}
	}
	
	// Sync
    public void disableSync() {
    	if (getBoolean(getContentResolver(), SETTING_LISTEN_FOR_TICKLES, true)) {
    		origTickleState = true;
    		putBoolean(getContentResolver(), SETTING_LISTEN_FOR_TICKLES, false);
    	}
        if (getBoolean(getContentResolver(), SETTING_BACKGROUND_DATA, true)) {
        	origBackState = true;
    		putBoolean(getContentResolver(), SETTING_BACKGROUND_DATA, false);
    	}
    }
    
    public void enableSync() {
    	if (origTickleState) {
    		putBoolean(getContentResolver(),SETTING_LISTEN_FOR_TICKLES, true);
    	}
    	if (origBackState) {
    		putBoolean(getContentResolver(), SETTING_BACKGROUND_DATA, true);
    	}
    }
    
    // Helpers
    public boolean getBoolean(ContentResolver contentResolver,
            String name, boolean def) {
        Cursor cursor = contentResolver.query(
            CONTENT_URI,
            PROJECTION,
            KEY + "=?",
            new String[] { name },
            null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
            	Log.d(MSG_TAG,cursor.getString(0));
                return Boolean.parseBoolean(cursor.getString(1));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return def;
    }
    
    public void putBoolean(ContentResolver contentResolver, String name, boolean val) {
        ContentValues values = new ContentValues();
        values.put(KEY, name);
        values.put(VALUE, Boolean.toString(val));
        // this insert is translated into an update by the underlying Sync provider
        contentResolver.insert(CONTENT_URI, values);
    }
}
