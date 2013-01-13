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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.TextView;

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
	private boolean mUnshareButtonClicked;

	public RhizomeDetail(Context context) {
		super(context);
		mManifest = null;
		mDeleteButtonClicked = false;
		mUnshareButtonClicked = false;
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

	public void setBundleFiles(File manifestFile, File payloadFile) {
		mManifestFile = manifestFile;
		mPayloadFile = payloadFile;
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
			try { size = formatSize(mManifest.getFilesize(), true); } catch (RhizomeManifest.MissingField e) {}
			try {
				mManifestFile = Rhizome.savedManifestFileFromName(name);
			} catch (FileNotFoundException e) {
				mManifestFile = null;
			}
			try {
				mPayloadFile = Rhizome.savedPayloadFileFromName(name);
			} catch (FileNotFoundException e) {
				mPayloadFile = null;
			}
		 } else {
			mManifestFile = null;
			mPayloadFile = null;
		}
		((TextView) findViewById(R.id.detail_name)).setText(name, TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_date)).setText(date, TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_version)).setText(version, TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_size)).setText(size, TextView.BufferType.NORMAL);
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
		if (!checkFilesExist())
			return false;
		try {
			FileInputStream mfis = new FileInputStream(mManifestFile);
			if (mManifestFile.length() <= RhizomeManifest_File.MAX_MANIFEST_BYTES) {
				byte[] manifestbytes = new byte[(int) mManifestFile.length()];
				mfis.read(manifestbytes);
				mfis.close();
				RhizomeManifest_File m = RhizomeManifest_File.fromByteArray(manifestbytes);
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

	public void enableSaveOrOpenButton() {
		if (mManifest instanceof RhizomeManifest_File) {
			if (checkFilesSaved())
				enableOpenButton();
			else
				enableSaveButton();
		}
	}

	public void disableUnshareButton() {
		Button unshareButton = ((Button) findViewById(R.id.Unshare));
		unshareButton.setVisibility(Button.GONE);
	}

	public void enableUnshareButton() {
		mUnshareButtonClicked = false;
		disableDeleteButton();
		Button unshareButton = ((Button) findViewById(R.id.Unshare));
		unshareButton.setVisibility(Button.VISIBLE);
		unshareButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onUnshareButtonClicked();
				}
			});
	}

	public void disableDeleteButton() {
		Button deleteButton = ((Button) findViewById(R.id.Delete));
		deleteButton.setVisibility(Button.GONE);
	}

	public void enableDeleteButton() {
		mDeleteButtonClicked = false;
		disableUnshareButton();
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
		try {
			if (mManifest instanceof RhizomeManifest_File)
				Rhizome.extractFile(mManifest.getManifestId(),
						((RhizomeManifest_File) mManifest).getName());
		} catch (Exception e) {
			Log.w(Rhizome.TAG, "cannot extract", e);
			ServalBatPhoneApplication.context
					.displayToastMessage("Failed to save file");
		}
		enableSaveOrOpenButton();
	}

	protected void onOpenButtonClicked() {
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
				ServalBatPhoneApplication.context
						.displayToastMessage("No activity for content type '"
								+ contentType + "'");
				Log.e(Rhizome.TAG, "No activity for content type '"
						+ contentType + "'");
			}
		}
	}

	protected void onUnshareButtonClicked() {
		mUnshareButtonClicked = true;
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
