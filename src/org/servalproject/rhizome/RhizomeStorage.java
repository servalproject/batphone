package org.servalproject.rhizome;


import java.text.NumberFormat;

import org.servalproject.R;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.widget.ProgressBar;


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

	private NumberFormat numberFormat;
	// set percentage number
	int freePercent;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.rhizome_main);

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

			// total usable size
			totalSize = stats.getBlockCount() * stats.getBlockSize();

			// initialize the NumberFormat object
			numberFormat = NumberFormat.getInstance();
			// disable grouping
			numberFormat.setGroupingUsed(false);
			// display numbers with two decimal places
			numberFormat.setMaximumFractionDigits(2);

			// Output the SD card's total size in gigabytes
			outputInfo += "Available Size: "
					+ numberFormat.format((totalSize / 1073741824)) + " GB \n";

			// available free space
			freeSpace = stats.getAvailableBlocks() * stats.getBlockSize();
			freePercent = stats.getAvailableBlocks() * stats.getBlockSize();

			// Output the SD card's available free space in gigabytes,
			outputInfo += "Remaining Space: "
					+ numberFormat.format((freeSpace / 1073741824)) + " GB \n";

			// progress bar layout
			ProgressBar mProgress = (ProgressBar) findViewById(R.id.progress_bar);
			mProgress.setMax(100);
			mProgress.setProgress(freePercent * 100);

		}
	}

}
