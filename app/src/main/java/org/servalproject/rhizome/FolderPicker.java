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
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;

import org.servalproject.R;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Environment;
import android.os.SystemClock;
import android.util.StateSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class FolderPicker extends Dialog implements OnItemClickListener, OnClickListener {

	private final ListView mFolders;
	private TextView mCurrentFolder;
	private Folder mPath;
	private Folder mFilePath;
	private final File mStorageFolder;
	private FolderAdapter mAdapter;
	private OnClickListener mListener;
	private final boolean mAcceptFiles;
	private final View mOkButton;
	// Preferences
	private final SharedPreferences mSharedPreferences;
	private final String mfolderPreference;

	public FolderPicker(Context context, OnClickListener listener, int themeId,
			SharedPreferences sharedPreference,
			String _folderPreference, boolean acceptFiles) {

		super(context, themeId);
		mListener = listener;
		mAcceptFiles = acceptFiles;
		mSharedPreferences = sharedPreference;
		mfolderPreference = _folderPreference;

		setTitle(acceptFiles? R.string.pick_file : R.string.pick_folder);
		setContentView(R.layout.folders);

		mStorageFolder = Environment.getExternalStorageDirectory();

		mOkButton = findViewById(R.id.ok_btn);
		mOkButton.setOnClickListener(this);
		mCurrentFolder = (TextView) findViewById(R.id.current_folder);
		mCurrentFolder.setSelected(true);
		mFolders = (ListView) findViewById(R.id.folders);
		mFolders.setOnItemClickListener(this);

		mAdapter = new FolderAdapter();
		mFolders.setAdapter(mAdapter);

		if (mfolderPreference == null || mfolderPreference == "") {
			mPath = new Folder(mStorageFolder.getAbsolutePath());
		} else {
			mPath = new Folder(mSharedPreferences.getString(mfolderPreference, ""));
			if (!mPath.canRead())
				mPath = new Folder(mStorageFolder.getAbsolutePath());
		}

		updateAdapter();
	}

	public void setOnClickListener(OnClickListener listener) {
		mListener = listener;
	}

	public File getPath() {
		return mAcceptFiles ? mFilePath : mPath;
	}

	@Override
	public void onClick(View v) {
		if (v == mOkButton && mListener != null) {
			mListener.onClick(this, DialogInterface.BUTTON_POSITIVE);
		}
		dismiss();
	}

	private void updateAdapter() {
		mCurrentFolder.setText(mPath.getAbsolutePath());
		mAdapter.clear();
		if (!mPath.equals(mStorageFolder)) {
			mAdapter.add(new Folder(mPath, true));
		}
		File[] dirs = mPath.listFiles(mDirFilter);
		if (dirs != null) {
			Arrays.sort(dirs);
			for (int i = 0; i < dirs.length; i++) {
				mAdapter.add(new Folder(dirs[i]));
			}
		}
		if (mAcceptFiles) {
			File[] files = mPath.listFiles(mFileFilter);
			if (files != null) {
				Arrays.sort(files);
				for (int i = 0; i < files.length; i++) {
					mAdapter.add(new Folder(files[i]));
				}
			}
		}
		mAdapter.notifyDataSetChanged();
		mFolders.setSelection(0);
	}

	private void updatePreference() {
		Editor ed = this.mSharedPreferences.edit();
		ed.putString(mfolderPreference, mPath.toString());
		ed.commit();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (mAcceptFiles) {
			Folder item = (Folder) mAdapter.getItem(position);
			if (item.isDirectory()) {
				mPath = item;
				updatePreference();
				updateAdapter();
				mFilePath = null;
				mAdapter.clearSelection();
			} else {
				mCurrentFolder.setText(item.getAbsolutePath());
				mAdapter.setSelectedPosition(position);
				mFilePath = item;
			}
		} else {
			mPath = (Folder) mAdapter.getItem(position);
			updatePreference();
			updateAdapter();
		}
	}

	private FileFilter mDirFilter = new FileFilter() {
		@Override
		public boolean accept(File file) {
			return file.isDirectory();
		}
	};

	private FileFilter mFileFilter = new FileFilter() {
		@Override
		public boolean accept(File file) {
			return file.isFile();
		}
	};

	class FolderAdapter extends BaseAdapter {
		ArrayList<Folder> mFolders = new ArrayList<Folder>();
		LayoutInflater mInflater = LayoutInflater.from(getContext());
		private Drawable[] mFolderUpLayers;
		private Drawable[] mFolderLayers;
		private Drawable mFileDrawable;
		private int selectedPos = -1;

		public FolderAdapter() {
			Resources res = getContext().getResources();
			mFolderUpLayers = new Drawable[] {
					res.getDrawable(R.drawable.ic_launcher_folder_up_closed),
					res.getDrawable(R.drawable.ic_launcher_folder_up_open),
			};
			mFolderLayers = new Drawable[] {
					res.getDrawable(R.drawable.ic_launcher_folder_closed),
					res.getDrawable(R.drawable.ic_launcher_folder_open),
			};
			mFileDrawable = res.getDrawable(R.drawable.file);
		}

		@Override
		public int getCount() {
			return mFolders.size();
		}

		public void add(Folder folder) {
			mFolders.add(folder);
		}

		public void clear() {
			mFolders.clear();
		}

		@Override
		public Object getItem(int position) {
			return mFolders.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = mInflater.inflate(R.layout.folder, parent, false);
			Folder folder = mFolders.get(position);
			TextView name = (TextView) v.findViewById(R.id.folder_name);

			Drawable drawable = null;
			if (folder.isParent) {
				name.setText("[..]");
				drawable = new FolderTransitionDrawable(mFolderUpLayers);
			} else {
				name.setText(folder.getName());
				if (folder.isDirectory()) {
					drawable = new FolderTransitionDrawable(mFolderLayers);
				} else {
					drawable = mFileDrawable;
					if (selectedPos == position) {
						// should be same as serval R.color.green which
						// incorrectly shows fully transparent - stupid
						// Android transparency bug.
						v.setBackgroundColor(Color.argb(255, 83, 104, 43));
					} else {
						v.setBackgroundColor(Color.TRANSPARENT);
					}
				}
			}

			v.findViewById(R.id.folder_icon).setBackgroundDrawable(drawable);
			return v;
		}

		public void clearSelection() {
			selectedPos = -1;
			// inform the view of this change
			notifyDataSetChanged();
		}

		public void setSelectedPosition(int pos) {
			selectedPos = pos;
			// inform the view of this change
			notifyDataSetChanged();
		}

		public int getSelectedPosition() {
			return selectedPos;
		}
	}


	static class FolderTransitionDrawable extends LayerDrawable implements Runnable {
		private static final int TRANSITION_DURATION = 400;
		private static final int[] STATE_SELECTED = {android.R.attr.state_selected};
		private static final int[] STATE_PRESSED = {android.R.attr.state_pressed};

		private boolean mActive;
		private int mAlpha;
		private int mFrom;
		private int mTo;
		private long mStartTime;
		private long mEndTime;

		public FolderTransitionDrawable(Drawable[] layers) {
			super(layers);
			mAlpha = 255;
		}


		@Override
		public boolean isStateful() {
			return true;
		}

		@Override
		protected boolean onStateChange(int[] state) {
			boolean active = StateSet.stateSetMatches(STATE_SELECTED, state) | StateSet.stateSetMatches(STATE_PRESSED, state);
			if (active != mActive) {
				mActive = active;
//				Log.d("FolderTransitionDrawable", "onStateChange " + StateSet.dump(state) + " " + active);
				if (!active) {
					unscheduleSelf(this);
					if (mAlpha != 255) {
						startTransition(false);
					}
				} else {
					scheduleSelf(this, SystemClock.uptimeMillis() + 500);
				}
				return true;
			}
			return false;
		}

		@Override
		public void run() {
			startTransition(true);
		}

		private void startTransition(boolean showLayer1) {
			mStartTime = SystemClock.uptimeMillis();
			mFrom = mAlpha;
			mEndTime = mStartTime;
			if (showLayer1) {
				mTo = 0;
				mEndTime += mAlpha * TRANSITION_DURATION / 255;
			} else {
				mTo = 255;
				mEndTime += (255 - mAlpha) * TRANSITION_DURATION / 255;
			}
			invalidateSelf();
		}

		@Override
		public void draw(Canvas canvas) {
			boolean done = true;

			if (mStartTime != 0) {
				long time = SystemClock.uptimeMillis();
				done = time > mEndTime;
				if (done) {
					mStartTime = 0;
					mAlpha = mTo;
				} else {
					float normalized = (time - mStartTime) / (float) (mEndTime - mStartTime);
					mAlpha = (int) (mFrom  + (mTo - mFrom) * normalized);
				}
			}

			Drawable d = getDrawable(0);
			d.setAlpha(mAlpha);
			d.draw(canvas);

			d = getDrawable(1);
			d.setAlpha(255 - mAlpha);
			d.draw(canvas);

			if (!done) {
				invalidateSelf();
//				Log.d("TAG", "draw invalidate");
			}
		}
	}

	@SuppressWarnings("serial")
	class Folder extends File {
		private boolean isParent;

		public Folder(File file) {
			super(file.getAbsolutePath());
		}

		public Folder(File file, boolean unused) {
			super(file.getParent());
			isParent = true;
		}

		public Folder(String path) {
			super(path);
		}
	}
}
