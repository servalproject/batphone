package org.servalproject.rhizome;

import java.lang.Math;

import org.servalproject.R;

import android.util.Log;
import android.os.Bundle;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Button;
import android.text.format.DateUtils;

/**
 * Dialog that is popped up when a user selects a file in the rhizome list view.  Displays
 * information about the file and gives the user a button to save it.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeDetail extends Dialog {

	private Bundle mData;

	public RhizomeDetail(Context context) {
		super(context);
		mData = null;
		setTitle("File Detail");
		setContentView(R.layout.rhizome_detail);
	}

	public void setData(Bundle bundle) {
		mData = bundle;
		((TextView) findViewById(R.id.detail_name)).setText(mData.getString("name"), TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_date)).setText(formatDate(mData.getLong("date")), TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_version)).setText("" + mData.getLong("version"), TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_size)).setText(formatSize(mData.getLong("length"), true), TextView.BufferType.NORMAL);
		//((TextView) findViewById(R.id.detail_manifest_id)).setText(mData.getString("manifestid"), TextView.BufferType.NORMAL);
		((Button) findViewById(R.id.Cancel)).setOnClickListener(new View.OnClickListener() {
             public void onClick(View v) {
                 cancel();
             }
         });
		((Button) findViewById(R.id.Save)).setOnClickListener(new View.OnClickListener() {
             public void onClick(View v) {
			 	onSaveButtonClicked();
             }
         });
	}

	protected void onSaveButtonClicked() {
		String manifestId = mData.getString("manifestid");
		String name = mData.getString("name");
		if (Rhizome.extractFile(manifestId, name))
			dismiss();
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
