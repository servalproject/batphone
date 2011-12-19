/**
 *
 */
package org.servalproject.rhizome;

import org.servalproject.R;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

/**
 * @author rbochet
 *
 *         This class displays a form with the required field to create the
 *         manifest (author, version). The results are sent back to Main.
 *
 */
public class ManifestEditorActivity extends Activity implements OnClickListener {
	/** TAG for debugging */
	public static final String TAG = "R2";

	/** Content of the author field on the form */
	private String author;

	/** Content of the version field on the form */
	private String version;

	@Override
	public void onClick(View v) {
		// Extract the data from the form
		author = ((EditText) findViewById(R.id.me_author)).getText()
				.toString();
		version = ((EditText) findViewById(R.id.me_version)).getText()
				.toString();

		// Fill the intent
		Intent intent = this.getIntent();
		intent.putExtra("author", author);
		intent.putExtra("version", version);
		this.setResult(RESULT_OK, intent);
		finish();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.manifest_editor);

		// Handle the validations
		Button validate = (Button) findViewById(R.id.me_validate);
		validate.setOnClickListener(this);

		Intent intent = getIntent();
		String filename = intent.getStringExtra("fileName");

		if (filename != null && filename.endsWith(".apk")) {
			PackageManager pm = this.getPackageManager();
			PackageInfo info = pm.getPackageArchiveInfo(filename, 0);
			if (info != null) {
				((EditText) findViewById(R.id.me_version)).setText(String
						.valueOf(info.versionCode));
			}
		}
	}
}
