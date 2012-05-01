package org.servalproject.rhizome;

import org.servalproject.R;

import android.os.Bundle;
import android.app.Dialog;
import android.content.Context;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.text.format.DateUtils;

public class RhizomeDetail extends Dialog {

	public RhizomeDetail(Context context) {
		super(context);
		setTitle("File Detail");
		setContentView(R.layout.rhizome_detail);
	}

	public void setData(Bundle bundle) {
		((TextView) findViewById(R.id.detail_name)).setText(bundle.getString("name"), TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_manifest_id)).setText(bundle.getString("manifestid"), TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_date)).setText(formatDate(bundle.getLong("date")), TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_version)).setText("" + bundle.getLong("version"), TextView.BufferType.NORMAL);
		((TextView) findViewById(R.id.detail_size)).setText("" + bundle.getLong("length"), TextView.BufferType.NORMAL);
	}

	protected String formatDate(long millis) {
		return DateUtils.formatDateTime(getContext(), millis, DateUtils.LENGTH_MEDIUM);
	}

}
