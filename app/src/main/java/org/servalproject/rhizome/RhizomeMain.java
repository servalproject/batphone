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

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.ServalD;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.ServalDFailureException;

import java.math.BigDecimal;
import java.text.NumberFormat;

/**
 * Rhizome list activity.  Presents the contents of the Rhizome store as a list of names.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeMain extends Activity implements OnClickListener {
	// Preferences
	private SharedPreferences settings = null;
	private static final String TAG = "RhizomeMain";
	// some size constants
	BigDecimal gb = new BigDecimal(1073741824);
	BigDecimal mb = new BigDecimal(1048576);
	BigDecimal kb = new BigDecimal(1024);

	@Override
	public void onClick(View view) {
		if (!ServalD.isRhizomeEnabled()) {
			ServalBatPhoneApplication.context
					.displayToastMessage("File sharing cannot function without an sdcard");
			return;
		}

		switch (view.getId()) {
		case R.id.rhizome_share:
			DialogInterface.OnClickListener fileConfirm = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface di, int which) {
					if (which == DialogInterface.BUTTON_POSITIVE) {
						ShareFileActivity
								.addFile(RhizomeMain.this,
										((FolderPicker) di)
												.getPath(), true);
					}
				}
			};

			FolderPicker shareDialog = new FolderPicker(
					RhizomeMain.this, fileConfirm,
					android.R.style.Theme, settings,
					"rhizome_share_dialog_last_folder", true);
			shareDialog.show();
			break;
		case R.id.rhizome_find:
			RhizomeMain.this.startActivity(new Intent(this, RhizomeList.class));
			break;
		case R.id.rhizome_saved:
			RhizomeMain.this
					.startActivity(new Intent(this, RhizomeSaved.class));
			break;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Preferences
		settings = PreferenceManager.getDefaultSharedPreferences(this);

		setContentView(R.layout.rhizome_main);

		this.findViewById(R.id.rhizome_share).setOnClickListener(this);
		this.findViewById(R.id.rhizome_find).setOnClickListener(this);
		this.findViewById(R.id.rhizome_saved).setOnClickListener(this);
	}

	@Override
	protected void onResume() {
		setupFreeSpace();
		super.onResume();
	}

	private void setupFreeSpace() {
		ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
		TextView progressLabel = (TextView) findViewById(R.id.progress_label);

		String state = Environment.getExternalStorageState();
		// checks if the SD card is attached to the Android device
		if (state.equals(Environment.MEDIA_MOUNTED)
				|| state.equals(Environment.MEDIA_UNMOUNTED)
				|| state
						.equals(Environment.MEDIA_MOUNTED_READ_ONLY))
		{
			// obtain the stats from the root of the SD card.
			StatFs stats = new StatFs(Environment.getExternalStorageDirectory()
					.getPath());

			String outputInfo = "Total Size: ";

			BigDecimal blockCount = new BigDecimal(stats.getBlockCount());
			BigDecimal blockSize = new BigDecimal(stats.getBlockSize());
			BigDecimal availBlocks = new BigDecimal(stats.getAvailableBlocks());

			BigDecimal totalSize = blockCount.multiply(blockSize);
			BigDecimal freeSpace = availBlocks.multiply(blockSize);

			NumberFormat numberFormat = NumberFormat.getInstance();
			numberFormat.setGroupingUsed(false);
			numberFormat.setMaximumFractionDigits(2);

			// Output the SD card's total size in gigabytes, megabytes
			BigDecimal totalSizeGb = totalSize.divide(gb);
			BigDecimal totalSizeMb = totalSize.divide(mb);
			outputInfo += numberFormat.format(totalSizeGb) + " GB ("
					+ numberFormat.format(totalSizeMb) + " MB)\n";

			// Output the SD card's available free space in gigabytes,
			// megabytes
			BigDecimal freeSpaceGb = freeSpace.divide(gb);
			BigDecimal freeSpaceMb = freeSpace.divide(mb);
			outputInfo += "Free Space: "
					+ numberFormat.format(freeSpaceGb) + " GB ("
					+ numberFormat.format(freeSpaceMb) + " MB)";

			progressBar.setMax(totalSizeMb.intValue());
			progressBar.setProgress(totalSizeMb.subtract(freeSpaceMb)
					.intValue());

			progressLabel.setText(outputInfo);
		}
		else // external storage was not found
		{
			// output the SD card state
			progressLabel.setTextColor(Color.RED);
			progressLabel.setText("SD card not found! SD card is \""
					+ state + "\".");
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO, added these just for demo
		try {
			if (!ServalDCommand.getConfig("rhizome.direct.peer.**").values.isEmpty()) {
				menu.add(Menu.NONE, 1, Menu.NONE, "Push");
				menu.add(Menu.NONE, 2, Menu.NONE, "Sync");
			}
		} catch (ServalDFailureException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		return super.onCreateOptionsMenu(menu);
	}

	private void runSync(String cmd) {
		new AsyncTask<String, Void, Void>() {
			@Override
			protected Void doInBackground(String... arg0) {
				try {
					if ("push".equals(arg0[0]))
						ServalDCommand.rhizomeDirectPush();
					else if ("pull".equals(arg0[0]))
						ServalDCommand.rhizomeDirectPull();
					else if ("sync".equals(arg0[0]))
						ServalDCommand.rhizomeDirectSync();
					else {
						Log.e("RhizomeMain", "unsupported operation: " + arg0[0]);
					}
				} catch (Exception e) {
					Log.e("RhizomeMain", e.toString(), e);
					ServalBatPhoneApplication.context.displayToastMessage(e.toString());
				}
				return null;
			}
		}.execute(cmd);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		String cmd = null;
		switch (item.getItemId()) {
		case 1:
			cmd = "push";
			break;
		case 2:
			cmd = "sync";
			break;
		}
		if (cmd != null)
			runSync(cmd);
		return super.onOptionsItemSelected(item);
	}

}
