package org.servalproject.rhizome;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

public class ShareFileActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		String action = intent.getAction();

		// if this is from the share menu
		if (Intent.ACTION_SEND.equals(action)) {
			if (extras.containsKey(Intent.EXTRA_STREAM)) {
				try {
					// Get resource path from intent callee
					Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

					String fileName = getRealPathFromURI(uri);

					Intent myIntent = new Intent(this.getBaseContext(),
							ManifestEditorActivity.class);

					myIntent.putExtra("fileName", fileName);
					startActivityForResult(myIntent, 0 // FILL_MANIFEST
					);


	                return;
				} catch (Exception e) {
					Log.e(this.getClass().getName(), e.toString());
				}

			} else if (extras.containsKey(Intent.EXTRA_TEXT)) {
				return;
			}
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 0 // FILL_MANIFEST
		) { // Comes back from the manifest
			// filling activity
			if (resultCode == RESULT_OK) {
				// Get the parameters
				String fileName = data.getExtras().getString("fileName");
				String author = data.getExtras().getString("author");
				long version = Long.parseLong(data.getExtras().getString(
						"version"));

				// Creates the manifest
				File dest;
				try {
					dest = RhizomeFile.CopyFile(fileName);
					RhizomeFile.GenerateManifestForFilename(dest.getName(),
							author, version);
					// Create silently the meta data
					RhizomeFile
							.GenerateMetaForFilename(dest.getName(), version);
				} catch (IOException e) {
					Log.e("BatPhone", e.toString(), e);
				}
				finish();

			}
		}
	}

	public String getRealPathFromURI(Uri contentUri) {

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
