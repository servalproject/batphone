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

import java.text.NumberFormat;

import org.servalproject.R;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.widget.TextView;

public class RhizomeStorage extends Activity
{
	// the statistics of the SD card
	private StatFs stats;
	// the state of the external storage
	private String externalStorageState;

	// the total size of the SD card
	private double totalSize;
	// the available free space
	private double freeSpace;
	// a String to store the SD card information
	private String outputInfo;
	// a TextView to output the SD card state
	private TextView tv_state;
	// a TextView to output the SD card information
	private TextView tv_info;
	// set the number format output
	private NumberFormat numberFormat;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.rhizome_main);

		// initialize the Text Views with the data at the main.xml file
		tv_state = (TextView) findViewById(R.id.state);
		tv_info = (TextView) findViewById(R.id.info);

		// get external storage (SD card) state
		externalStorageState = Environment.getExternalStorageState();

		// checks if the SD card is attached to the Android device
		if (externalStorageState.equals(Environment.MEDIA_MOUNTED)
				|| externalStorageState.equals(Environment.MEDIA_UNMOUNTED)
				|| externalStorageState
						.equals(Environment.MEDIA_MOUNTED_READ_ONLY))
		{
			// obtain the stats from the root of the SD card.
			stats = new StatFs(Environment.getExternalStorageDirectory()
					.getPath());

			// Add 'Total Size' to the output string:
			outputInfo = "Total Size: ";

			// total usable size
			totalSize = stats.getBlockCount() * stats.getBlockSize();

			// initialize the NumberFormat object
			numberFormat = NumberFormat.getInstance();
			// disable grouping
			numberFormat.setGroupingUsed(false);
			// display numbers with two decimal places
			numberFormat.setMaximumFractionDigits(2);

			// Output the SD card's total size in gigabytes, megabytes,
			// kilobytes and bytes
			outputInfo += numberFormat.format((totalSize / 1073741824))
					+ " GB ( " + numberFormat.format((totalSize / 1048576))
					+ " MB ) \n";
			// + "Size in kilobytes: " + numberFormat.format((totalSize /
			// (double)1024)) + " KB \n"
			// + "Size in bytes: " + numberFormat.format(totalSize) + " B \n";

			// Add 'Remaining Space' to the output string:
			outputInfo += "Remaining Space: ";

			// available free space
			freeSpace = stats.getAvailableBlocks() * stats.getBlockSize();

			// Output the SD card's available free space in gigabytes,
			// megabytes, kilobytes and bytes
			outputInfo += numberFormat.format((freeSpace / 1073741824))
					+ " GB ( " + numberFormat.format((freeSpace / 1048576))
					+ " MB ) \n";
			// + "Size in kilobytes: " + numberFormat.format((freeSpace /
			// (double)1024)) + " KB \n"
			// + "Size in bytes: " + numberFormat.format(freeSpace) + " B \n";

			// output the SD card state
			tv_state.setText("SD card found!");
			// output the SD card info
			tv_info.setText(outputInfo);
		}
		else // external storage was not found
		{
			// output the SD card state
			tv_state.setTextColor(Color.RED);
			tv_state.setText("SD card not found! SD card is \""
					+ externalStorageState + "\".");
		}
	}
}
