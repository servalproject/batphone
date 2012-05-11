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

import java.lang.Math;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.servalproject.R;

import android.util.Log;
import android.os.Bundle;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.view.View;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Button;
import android.text.format.DateUtils;
import android.net.Uri;
import android.webkit.MimeTypeMap;

/**
 * Dialog that is popped up when a user selects a file in the rhizome list view.  Displays
 * information about the file and gives the user a button to save it.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeDetail extends Dialog {

	private RhizomeManifest mManifest;
	private File mManifestFile;
	private File mPayloadFile;
	private boolean mDeleteButtonClicked;

	public RhizomeDetail(Context context) {
		super(context);
		mManifest = null;
		mDeleteButtonClicked = false;
		setTitle("File Detail");
		setContentView(R.layout.rhizome_detail);
		Button cancelButton = ((Button) findViewById(R.id.Cancel));
		cancelButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					cancel();
				}
			});
	}

	public void setManifest(RhizomeManifest m) {
		mManifest = m;
		if (mManifest != null) {
			((TextView) findViewById(R.id.detail_name)).setText(mManifest.getName(), TextView.BufferType.NORMAL);
			((TextView) findViewById(R.id.detail_date)).setText(formatDate(mManifest.getDateMillis()), TextView.BufferType.NORMAL);
			((TextView) findViewById(R.id.detail_version)).setText("" + mManifest.getVersion(), TextView.BufferType.NORMAL);
			((TextView) findViewById(R.id.detail_size)).setText(formatSize(mManifest.getFilesize(), true), TextView.BufferType.NORMAL);
			mManifestFile = Rhizome.savedManifestFileFromName(mManifest.getName());
			mPayloadFile = Rhizome.savedPayloadFileFromName(mManifest.getName());
		 } else {
			((TextView) findViewById(R.id.detail_name)).setText("", TextView.BufferType.NORMAL);
			((TextView) findViewById(R.id.detail_date)).setText("", TextView.BufferType.NORMAL);
			((TextView) findViewById(R.id.detail_version)).setText("", TextView.BufferType.NORMAL);
			((TextView) findViewById(R.id.detail_size)).setText("", TextView.BufferType.NORMAL);
			mManifestFile = null;
			mPayloadFile = null;
		}
	}

	public void disableSaveButton() {
		Button saveButton = ((Button) findViewById(R.id.Save));
		saveButton.setVisibility(Button.GONE);
	}

	public void enableSaveButton() {
		disableOpenButton();
		Button saveButton = ((Button) findViewById(R.id.Save));
		saveButton.setVisibility(Button.VISIBLE);
		saveButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onSaveButtonClicked();
				}
			});
	}

	public void disableOpenButton() {
		Button openButton = ((Button) findViewById(R.id.Open));
		openButton.setVisibility(Button.GONE);
	}

	public void enableOpenButton() {
		disableSaveButton();
		Button openButton = ((Button) findViewById(R.id.Open));
		openButton.setVisibility(Button.VISIBLE);
		openButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onOpenButtonClicked();
				}
			});
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
		if (checkFilesExist()) {
			try {
				FileInputStream mfis = new FileInputStream(mManifestFile);
				if (mManifestFile.length() <= RhizomeManifest.MAX_MANIFEST_BYTES) {
					byte[] manifestbytes = new byte[(int) mManifestFile.length()];
					mfis.read(manifestbytes);
					mfis.close();
					RhizomeManifest m = RhizomeManifest.fromByteArray(manifestbytes);
					return m.getIdHex().equalsIgnoreCase(mManifest.getIdHex())
						&& m.getVersion() == mManifest.getVersion();
				} else {
					Log.w(Rhizome.TAG, "manifest file " + mManifestFile + "is too long");
				}
			}
			catch (IOException e) {
				Log.w(Rhizome.TAG, "cannot read manifest file " + mManifestFile, e);
			}
			catch (RhizomeManifestParseException e) {
				Log.w(Rhizome.TAG, "file " + mManifestFile, e);
			}
		}
		return false;
	}

	public void enableSaveOrOpenButton() {
		if (checkFilesSaved())
			enableOpenButton();
		else
			enableSaveButton();
	}

	public void disableDeleteButton() {
		Button deleteButton = ((Button) findViewById(R.id.Delete));
		deleteButton.setVisibility(Button.GONE);
	}

	public void enableDeleteButton() {
		mDeleteButtonClicked = false;
		Button deleteButton = ((Button) findViewById(R.id.Delete));
		deleteButton.setVisibility(Button.VISIBLE);
		deleteButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onDeleteButtonClicked();
				}
			});
	}

	protected void onSaveButtonClicked() {
		if (!Rhizome.extractFile(mManifest.getIdHex(), mManifest.getName()))
			dismiss();
		enableSaveOrOpenButton();
	}

	protected void onOpenButtonClicked() {
		String manifestId = mManifest.getIdHex();
		Uri uri = Uri.fromFile(mPayloadFile);
		String filename = mPayloadFile.getName();
		String ext = filename.substring(filename.lastIndexOf(".") + 1);
		String contentType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
		if (contentType == null) {
			Log.i(Rhizome.TAG, "Cannot open uri='" + uri + "', unknown content type");
		} else {
			Log.i(Rhizome.TAG, "Open uri='" + uri + "', contentType='" + contentType + "'");
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_VIEW);
			intent.setDataAndType(uri, contentType);
			try {
				getContext().startActivity(intent);
				dismiss();
			}
			catch (ActivityNotFoundException e) {
				Log.e(Rhizome.TAG, "no activity for content type '" + contentType + "'");
			}
		}
	}

	protected void onDeleteButtonClicked() {
		mDeleteButtonClicked = true;
		Rhizome.deleteSavedFiles(mPayloadFile, mManifestFile);
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
