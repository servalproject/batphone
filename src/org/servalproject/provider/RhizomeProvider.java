package org.servalproject.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeManifest;
import org.servalproject.rhizome.RhizomeManifest_File;
import org.servalproject.servald.ServalD;
import org.servalproject.servaldna.BundleId;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.SubscriberId;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;

public class RhizomeProvider extends ContentProvider {
	public static final String AUTHORITY = "org.servalproject.files";
	private static final String TAG = "RhizomeProvider";
	private Handler handler;

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		Log.v(TAG, "delete " + uri);
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public String getType(Uri uri) {
		Log.v(TAG, "getType " + uri);
		try {
			List<String> segments = uri.getPathSegments();
			if (segments.size() < 1)
				throw new FileNotFoundException();

			BundleId bid = new BundleId(segments.get(0));
			RhizomeManifest manifest = Rhizome.readManifest(bid);
			return manifest.getMimeType();
		}catch (Exception e){
			Log.e(TAG, e.getMessage(), e);
			return null;
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.v(TAG, "insert " + uri);
		try {
			File payloadFile = null;
			File tempManifest = null;
			RhizomeManifest manifest = null;
			SubscriberId author = ServalBatPhoneApplication.context.server.getIdentity().sid;

			String filePath = values.getAsString("path");
			String manifestPath = values.getAsString("manifest");
			String authorSid = values.getAsString("author");
			Long version = values.getAsLong("version");
			Long date = values.getAsLong("date");
			String name = values.getAsString("name");
			String saveManifestPath = values.getAsString("save_manifest");

			if (manifestPath != null) {
				File manifestFile = new File(manifestPath);
				if (!manifestFile.exists())
					throw new UnsupportedOperationException(
							"Existing manifest file could not be read");
				manifest = RhizomeManifest.readFromFile(manifestFile);
				manifest.unsetFilehash();
				manifest.unsetFilesize();
				manifest.unsetDateMillis();
			}

			if (filePath != null) {
				payloadFile = new File(filePath);
				if (!payloadFile.exists())
					throw new UnsupportedOperationException(
							"Payload file could not be read");
			}

			if (authorSid != null) {
				if (authorSid.equals("")) {
					author = null;
				} else {
					author = new SubscriberId(authorSid);
				}
			}

			if (version != null) {
				if (manifest == null)
					manifest = new RhizomeManifest_File();
				manifest.setVersion(version);
			}

			if (date != null) {
				if (manifest == null)
					manifest = new RhizomeManifest_File();
				manifest.setDateMillis(date);
			}

			if (name != null
					&& (manifest == null || manifest instanceof RhizomeManifest_File)) {
				if (manifest == null)
					manifest = new RhizomeManifest_File();
				((RhizomeManifest_File) manifest).setName(name);
			}

			if (manifest != null) {
				// save to a temporary location
				tempManifest = File.createTempFile("manifest", ".temp",
						Rhizome.getTempDirectoryCreated());
				tempManifest.deleteOnExit();
				manifest.writeTo(tempManifest);
			}

			ServalDCommand.ManifestResult result = ServalDCommand.rhizomeAddFile(
					payloadFile,
					tempManifest, author, null);

			if (tempManifest != null)
				tempManifest.delete();

			if (saveManifestPath != null) {
				// save the new manifest here, so the caller can use it to
				// update a file
				tempManifest = new File(saveManifestPath);
				ServalDCommand.rhizomeExportManifest(result.manifestId,
						tempManifest);
			}

			return Uri.parse("content://" + AUTHORITY + "/"
					+ result.manifestId.toHex());
		} catch (UnsupportedOperationException e) {
			throw e;
		} catch (Exception e) {
			throw new UnsupportedOperationException(e.getMessage(), e);
		}
	}

	@Override
	public boolean onCreate() {
		handler = new Handler();
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		Log.v(TAG, "query for uri; " + uri +
				", projection; " + Arrays.toString(projection) +
				", selection; " + selection +
				", selectionArgs; " + Arrays.toString(selectionArgs) +
				", sortOrder; " + sortOrder);

		if (projection != null || selection != null
				|| (!uri.getPath().equals("/"))) {
			throw new UnsupportedOperationException("Not implemented");
		}
		try {
			String service = null;
			String name = null;
			SubscriberId sender = null;
			SubscriberId recipient = null;
			if (selectionArgs.length > 0)
				service = selectionArgs[0];
			if (selectionArgs.length > 1)
				name = selectionArgs[1];
			if (selectionArgs.length > 2)
				sender = new SubscriberId(selectionArgs[2]);
			if (selectionArgs.length > 3)
				recipient = new SubscriberId(selectionArgs[3]);
			return ServalD.rhizomeList(service, name, sender, recipient);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		Log.v("RhizomeProvider", "update " + uri);
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		Log.v("RhizomeProvider", "openFile " + uri);
		if (mode.indexOf('w') > 0)
			throw new SecurityException("Write operations are not allowed");

		try {
			List<String> segments = uri.getPathSegments();
			if (segments.size() < 1)
				throw new FileNotFoundException();

			BundleId bid = new BundleId(segments.get(0));
			File dir = Rhizome.getTempDirectoryCreated();
			final File temp = new File(dir, bid.toHex() + ".tmp");
			ServalDCommand.rhizomeExtractFile(bid, temp);

			// We *should* be able to pipe data here on demand.
			// However, some default image or media viewers
			// need a seek-able file descriptor that they can call fstat on.
			// So we need a file that we can't delete immediately
			// I just hope that 10 seconds is enough...

			ParcelFileDescriptor fd = ParcelFileDescriptor.open(temp,
					ParcelFileDescriptor.MODE_READ_ONLY);
			handler.postDelayed(new Runnable(){
				@Override
				public void run() {
					temp.delete();
				}
			}, 10000);

			temp.deleteOnExit();
			return fd;
		} catch (FileNotFoundException e) {
			Log.e("RhizomeProvider", e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			Log.e("RhizomeProvider", e.getMessage(), e);
			FileNotFoundException f = new FileNotFoundException(e.getMessage());
			f.initCause(e);
			throw f;
		}
	}
}
