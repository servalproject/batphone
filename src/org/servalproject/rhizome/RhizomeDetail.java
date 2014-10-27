/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.rhizome;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.provider.RhizomeProvider;
import org.servalproject.servaldna.ServalDCommand;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Dialog that is popped up when a user selects a file in the rhizome list view.  Displays
 * information about the file and gives the user a button to save it.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeDetail extends Dialog implements View.OnClickListener {

	private RhizomeManifest mManifest;
	private File mManifestFile;
	private File mPayloadFile;
	private boolean mDeleteButtonClicked;

	private final Button cancelButton;
	private final Button openButton;
	private final Button saveButton;
	private final Button unshareButton;
	private final Button deleteButton;
	private static final String TAG = "RhizomeDetail";

	public RhizomeDetail(Context context) {
		super(context);
		mManifest = null;
		mDeleteButtonClicked = false;
		setTitle("File Detail");
		setContentView(R.layout.rhizome_detail);
		cancelButton = ((Button) findViewById(R.id.Cancel));
		cancelButton.setOnClickListener(this);
		openButton = ((Button) findViewById(R.id.Open));
		openButton.setOnClickListener(this);
		saveButton = ((Button) findViewById(R.id.Save));
		saveButton.setOnClickListener(this);
		unshareButton = ((Button) findViewById(R.id.Unshare));
		unshareButton.setOnClickListener(this);
		deleteButton = ((Button) findViewById(R.id.Delete));
		deleteButton.setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		switch(view.getId()){
			case R.id.Cancel:
				cancel();
				break;
			case R.id.Open:
				onOpenButtonClicked();
				break;
			case R.id.Save:
				onSaveButtonClicked();
				break;
			case R.id.Unshare:
				onUnshareButtonClicked();
				break;
			case R.id.Delete:
				onDeleteButtonClicked();
		}
	}

	public void setBundleFiles(File manifestFile, File payloadFile) {
		mManifestFile = manifestFile;
		mPayloadFile = payloadFile;
		try {
			setManifest(RhizomeManifest.readFromFile(manifestFile));
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public void setManifest(RhizomeManifest lastManifest) {
		mManifest = lastManifest;
		String name = "";
		CharSequence date = "";
		CharSequence version = "";
		CharSequence size = "";
		if (mManifest != null) {
			name = mManifest.getDisplayName();
			try { date = formatDate(mManifest.getDateMillis()); } catch (RhizomeManifest.MissingField e) {}
			try { version = "" + mManifest.getVersion(); } catch (RhizomeManifest.MissingField e) {}
			size = formatSize(mManifest.getFilesize(), true);
		}
		((TextView) findViewById(R.id.detail_name)).setText(name, TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_date)).setText(date, TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_version)).setText(version, TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_size)).setText(size, TextView.BufferType.NORMAL);

		if (mManifestFile == null && mManifest instanceof RhizomeManifest_File) {
			RhizomeManifest_File file = (RhizomeManifest_File)mManifest;
			try {
				mPayloadFile = Rhizome.savedPayloadFileFromName(file.getName());
				mManifestFile = Rhizome.savedManifestFileFromName(file.getName());
			} catch (FileNotFoundException e) {
			}
		}
		enableSaveOrOpenButton();
	}

	/** Return true if the "saved" directory contains a manifest/payload file pair whose names
	 * correspond to our manifest.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected boolean checkFilesExist() {
		return mManifestFile != null && mManifestFile.exists() && mPayloadFile != null && mPayloadFile.exists();
	}

	/** Return true if the "saved" directory contains a manifest/payload file pair whose names
	 * correspond to our manifest and the saved manifest file has the same ID and version as
	 * our manifest.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected boolean checkFilesSaved() {
		if (!checkFilesExist())
			return false;
		try {
			FileInputStream mfis = new FileInputStream(mManifestFile);
			if (mManifestFile.length() <= RhizomeManifest_File.MAX_MANIFEST_BYTES) {
				byte[] manifestbytes = new byte[(int) mManifestFile.length()];
				mfis.read(manifestbytes);
				mfis.close();
				RhizomeManifest m = RhizomeManifest.fromByteArray(manifestbytes);
				return mManifest.getManifestId().equals(m.getManifestId())
					&& mManifest.getVersion() == m.getVersion();
			} else {
				Log.w(Rhizome.TAG, "manifest file " + mManifestFile + "is too long");
				return false;
			}
		} catch (RhizomeManifest.MissingField e) {
			return false;
		}
		catch (IOException e) {
			Log.w(Rhizome.TAG, "cannot read manifest file " + mManifestFile, e);
			return false;
		}
		catch (RhizomeManifestParseException e) {
			Log.w(Rhizome.TAG, "file " + mManifestFile, e);
			return false;
		}
	}

	private void enableSaveOrOpenButton() {
		boolean saved = checkFilesSaved();
		boolean isFile = (mManifest instanceof RhizomeManifest_File);
		openButton.setVisibility(mManifest.getFilesize()>0 ? View.VISIBLE : View.GONE);
		saveButton.setVisibility((saved || !isFile) ? View.GONE : View.VISIBLE);
		mDeleteButtonClicked = false;
		deleteButton.setVisibility(saved ? Button.VISIBLE : View.GONE);
	}

	public void enableUnshareButton() {
		unshareButton.setVisibility(Button.VISIBLE);
	}

	protected void onSaveButtonClicked() {
		try {
			if (mManifest instanceof RhizomeManifest_File){
				RhizomeManifest_File file = (RhizomeManifest_File) mManifest;
				Rhizome.getSaveDirectoryCreated();
				// A manifest file without a payload file is ok, but not vice versa. So always delete
				// manifest files last and create them first.
				mPayloadFile.delete();
				mManifestFile.delete();
				ServalDCommand.rhizomeExtractBundle(file.getManifestId(), mManifestFile, mPayloadFile);
				enableSaveOrOpenButton();
			}
		} catch (Exception e) {
			Log.w(Rhizome.TAG, "cannot extract", e);
			Rhizome.safeDelete(mPayloadFile);
			Rhizome.safeDelete(mManifestFile);
			ServalBatPhoneApplication.context.displayToastMessage("Failed to save file");
		}
	}

	protected void onOpenButtonClicked() {
		try {
			if (!(mManifest instanceof RhizomeManifest_File))
				throw new IOException("manifest is not a file service");

			RhizomeManifest_File file = (RhizomeManifest_File) mManifest;
			String contentType = file.getMimeType();

			Uri uri = null;
			if (mPayloadFile != null && mPayloadFile.exists()) {
				uri = Uri.fromFile(mPayloadFile);
			} else {
				uri = Uri.parse("content://"
						+ RhizomeProvider.AUTHORITY + "/"
						+ file.getManifestId().toHex()+"/"+file.getName());
			}

			if (uri == null || contentType == null)
				throw new IOException("Cannot open uri='" + uri + "', unknown content type");

			Log.i(Rhizome.TAG, "Open uri='" + uri + "', contentType='" + contentType + "'");
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_VIEW);
			intent.setDataAndType(uri, contentType);
			getContext().startActivity(intent);
			dismiss();
		}catch (ActivityNotFoundException e) {
			ServalBatPhoneApplication.context
					.displayToastMessage("No activity found for that content type");
		}catch(Exception e) {
			ServalBatPhoneApplication.context
					.displayToastMessage(e.getMessage());
			Log.e(TAG, e.getMessage(), e);
		}
	}

	protected void onUnshareButtonClicked() {
		if (mManifest instanceof RhizomeManifest_File)
			if (Rhizome.unshareFile((RhizomeManifest_File) mManifest))
				dismiss();
	}

	protected void onDeleteButtonClicked() {
		mDeleteButtonClicked = true;
		Rhizome.safeDelete(mPayloadFile);
		Rhizome.safeDelete(mManifestFile);
		if (!checkFilesExist())
			dismiss();
	}

	public boolean deleteButtonClicked() {
		return mDeleteButtonClicked;
	}

	protected CharSequence formatDate(long millis) {
		return DateUtils.getRelativeDateTimeString(getContext(), millis,
					DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS,
					DateUtils.LENGTH_MEDIUM | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
				);
	}

	/**
	 * Lifted from http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
	 */
	protected CharSequence formatSize(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
}
