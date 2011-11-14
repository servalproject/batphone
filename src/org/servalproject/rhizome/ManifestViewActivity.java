package org.servalproject.rhizome;

import java.io.File;

import org.servalproject.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * @author rbochet
 *
 */
public class ManifestViewActivity extends Activity implements OnClickListener {
	/** TAG for debugging */
	public static final String TAG = "R2";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.manifest_view);

		// Register the exit button
		Button back_button = (Button) findViewById(R.id.mv_back);
		back_button.setOnClickListener(this);

		// Get the intent with its data
		Intent intent = this.getIntent();
		// Fill the screen
		TextView author_tv = (TextView) findViewById(R.id.mv_author);
		author_tv.setText(intent.getStringExtra("author"));

		TextView version_tv = (TextView) findViewById(R.id.mv_version);
		version_tv.setText(intent.getStringExtra("version"));

		TextView hash_tv = (TextView) findViewById(R.id.mv_hash);
		hash_tv.setText(intent.getStringExtra("hash"));

		TextView size_tv = (TextView) findViewById(R.id.mv_size);
		size_tv.setText(intent.getStringExtra("size"));

		TextView date_tv = (TextView) findViewById(R.id.mv_date);
		date_tv.setText(intent.getStringExtra("date"));

		TextView name_tv = (TextView) findViewById(R.id.mv_name);
		name_tv.setText(intent.getStringExtra("name"));

		// Compute the hash of the file and compares it to the manifest's one.
		File f = new File(RhizomeUtils.dirRhizome, intent.getStringExtra("name"));
		String f_hash = RhizomeUtils.ToHexString(RhizomeUtils.DigestFile(f));

		TextView hash_ok = (TextView) findViewById(R.id.mv_hash_ok);
		hash_ok.setText(f_hash.equals(intent.getStringExtra("hash"))+"");

		// Check if the certificate is signed correctly (TODO)
		TextView sign_ok = (TextView) findViewById(R.id.mv_sign_ok);
		sign_ok.setText("TODO");
	}

	@Override
	public void onClick(View v) {
		finish();
	}

}
