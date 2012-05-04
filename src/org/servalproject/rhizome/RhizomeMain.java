package org.servalproject.rhizome;

import java.io.File;

import org.servalproject.R;
import org.servalproject.Main;

import android.util.Log;
import android.app.Activity;
import android.os.Environment;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.widget.Button;
import android.view.View;
import android.view.View.OnClickListener;
import android.net.Uri;

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
		Log.i(Rhizome.TAG, getClass().getName()+".onDestroy()");
		super.onDestroy();
	}

	/**
	 * Set up the interface layout.
	 */
	private void setUpUI() {
		setContentView(R.layout.rhizome_main);
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			Button buttonFind = (Button) this.findViewById(R.id.rhizome_find);
			buttonFind.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					RhizomeMain.this.startActivity(new Intent(RhizomeMain.this, RhizomeList.class));
				}
			});
		} else {
			// If there is not SD card present, grey out the buttons and the storage display.
			;
		}

		Button buttonSendApp = (Button) this.findViewById(R.id.rhizome_share_app);
		buttonSendApp.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				try {
					File apk = new File(RhizomeMain.this.getApplicationInfo().sourceDir);
					Intent intent = new Intent(Intent.ACTION_SEND);
					intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(apk));
					intent.setType("image/apk");
					intent.addCategory(Intent.CATEGORY_DEFAULT);
					// There are at least two different classes for handling this intent on
					// different platforms.  Find the bluetooth one.  Alternative strategy: let the
					// user choose.
					for (ResolveInfo r : RhizomeMain.this.getPackageManager().queryIntentActivities(intent, 0)) {
						if (r.activityInfo.packageName.equals("com.android.bluetooth")) {
							intent.setClassName(r.activityInfo.packageName, r.activityInfo.name);
							break;
						}
					}
					RhizomeMain.this.startActivity(intent);
				} catch (Exception e) {
					Log.e(Rhizome.TAG, "failed to send app", e);
					Rhizome.goToast("Failed to send app: " + e.getMessage());
				}
			}
		});

	}

}
