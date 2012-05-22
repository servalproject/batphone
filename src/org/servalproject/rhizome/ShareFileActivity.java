package org.servalproject.rhizome;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.Peer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

public class ShareFileActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		String action = intent.getAction();

		// if this is from the share menu
		if (Intent.ACTION_SEND.equals(action)) {
			Uri uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
			if (uri == null)
				uri = intent.getData();

			String text = intent.getStringExtra(Intent.EXTRA_TEXT);
			String type = intent.getType();
			Bundle extras = intent.getExtras();
			for (String key : extras.keySet()) {
				Log.v(this.getClass().getName(),
						"Extra " + key + " = " + extras.getString(key));
			}

			if (text!=null){
				// Does the tex include a market uri??
				// TODO - check that this still works with Google Play
				String marketUrl = "http://market.android.com/search?q=pname:";
				int x = text.indexOf(marketUrl);
				if (x>0){
					String appPackage = text.substring(x + marketUrl.length(), text.indexOf(' ', x));
					Log.v(this.getClass().getName(), "App Package? \""
							+ appPackage + "\"");
					try{
						ApplicationInfo info = this.getPackageManager().getApplicationInfo(appPackage, 0);
						uri = Uri.fromFile(new File(info.sourceDir));
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			}

			if (uri != null) {
				try {

					// Get resource path from intent callee
					String fileName = getRealPathFromURI(uri);
					File file = new File(fileName);
					Log.v(this.getClass().getName(), "Sharing " + fileName
							+ " ("
							+ uri + ")");

					addFile(file);

				} catch (Exception e) {
					Log.e(this.getClass().getName(), e.toString(), e);
					ServalBatPhoneApplication.context.displayToastMessage(e
							.toString());
				}

			} else if (text != null) {
				Log.v(this.getClass().getName(), "Text content: \"" + text
						+ "\" (" + type
						+ ")");
				ServalBatPhoneApplication.context
						.displayToastMessage("sending of text not yet supported");
			}
		} else {
			ServalBatPhoneApplication.context.displayToastMessage("Intent "
					+ action + " not supported!");
		}
		finish();
	}

	private void addFile(final File file) {
		new AsyncTask<Void, Peer, Void>() {
			@Override
			protected void onPostExecute(Void result) {
				ServalBatPhoneApplication.context
						.displayToastMessage("file shared via Rhizome");
			}

			@Override
			protected Void doInBackground(Void... params) {
				Rhizome.addFile(file);
				return null;
			}
		}.execute();
	}

	public String getRealPathFromURI(Uri contentUri) {
		if (contentUri.getScheme().equals("file")) {
			return contentUri.getEncodedPath();
		}

		// can post image
		String[] proj = { MediaStore.Images.Media.DATA };
		Cursor cursor = managedQuery(contentUri, proj, // Which columns to
														// return
				null, // WHERE clause; which rows to return (all rows)
				null, // WHERE clause selection arguments (none)
				null); // Order-by clause (ascending by name)
		int column_index = cursor
				.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
		cursor.moveToFirst();

		return cursor.getString(column_index);
	}

	public static byte[] readBytes(InputStream inputStream) throws Exception {
		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
		// this is storage overwritten on each iteration with bytes
		int bufferSize = 1024;
		byte[] buffer = new byte[bufferSize];
		int len = 0;
		while ((len = inputStream.read(buffer)) != -1) {
			byteBuffer.write(buffer, 0, len);
		}
		return byteBuffer.toByteArray();
	}
}
