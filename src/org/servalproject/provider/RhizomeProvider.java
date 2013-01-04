package org.servalproject.provider;

import java.io.File;
import java.io.FileNotFoundException;

import org.servalproject.rhizome.Rhizome;
import org.servalproject.servald.BundleId;
import org.servalproject.servald.ServalD;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

public class RhizomeProvider extends ContentProvider {
	public static final String AUTHORITY = "org.servalproject.files";
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public String getType(Uri uri) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		if (mode.indexOf('w') > 0)
			throw new SecurityException("Write operations are not allowed");
		try {
			String bidHex = uri.getPath();
			if (bidHex.startsWith("/"))
				bidHex = bidHex.substring(1);
			BundleId bid = new BundleId(bidHex);
			File dir = Rhizome.getTempDirectoryCreated();
			File temp = new File(dir, bid.toString() + ".tmp");
			ServalD.rhizomeExtractFile(bid, temp);
			ParcelFileDescriptor fd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY);
			temp.delete();
			return fd;
		} catch (Exception e) {
			FileNotFoundException f = new FileNotFoundException();
			f.initCause(e);
			throw f;
		}
	}
}
