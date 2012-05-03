package org.servalproject.rhizome;

import org.servalproject.R;

import android.util.Log;
import android.app.Activity;
import android.os.Environment;
import android.os.Bundle;

/**
 * Rhizome list activity.  Presents the contents of the Rhizome store as a list of names.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeMain extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(Rhizome.TAG, getClass().getName()+".onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.rhizome_list);
	}

	@Override
	protected void onStart() {
		Log.i(Rhizome.TAG, getClass().getName()+".onStart()");
		super.onStart();
	}

	@Override
	protected void onResume() {
		Log.i(Rhizome.TAG, getClass().getName()+".onResume()");
		setUpUI();
		super.onResume();
	}

	@Override
	protected void onStop() {
		Log.i(Rhizome.TAG, getClass().getName()+".onStop()");
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		Log.i(Rhizome.TAG, getClass().getName()+".onDestory()");
		super.onDestroy();
	}

	/**
	 * Set up the interface layout.
	 */
	private void setUpUI() {
		setContentView(R.layout.rhizome_main);
		// If there is no SD card...
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			;
		} else {
			;
		}
	}

}
