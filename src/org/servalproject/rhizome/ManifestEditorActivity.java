/**
 *
 */
package org.servalproject.rhizome;

import java.io.File;

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

	@Override
	public void onClick(View v) {
		// Extract the data from the form
		String author = ((EditText) findViewById(R.id.me_author)).getText()
				.toString();
		String version = ((EditText) findViewById(R.id.me_version)).getText()
				.toString();
		String dest = ((EditText) findViewById(R.id.me_name)).getText()
				.toString();
		// Fill the intent
		Intent intent = this.getIntent();
		intent.putExtra("author", author);
		intent.putExtra("version", version);
		intent.putExtra("destinationName", dest);
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
		File source = new File(filename);
		CharSequence destinationName = source.getName();
		int version = 1;

		if (filename != null && filename.toLowerCase().endsWith(".apk")) {
			PackageManager pm = this.getPackageManager();
			PackageInfo info = pm.getPackageArchiveInfo(RhizomeUtils.dirRhizome
					+ "/" + filename, 0);
			if (info != null) {
				version = info.versionCode;

				// see http://code.google.com/p/android/issues/detail?id=9151
				if (info.applicationInfo.sourceDir == null)
					info.applicationInfo.sourceDir = filename;
				if (info.applicationInfo.publicSourceDir == null)
					info.applicationInfo.publicSourceDir = filename;

				CharSequence label = info.applicationInfo.loadLabel(pm);
				if (label != null && !"".equals(label))
					destinationName = label + ".apk";
				else
					destinationName = info.packageName + ".apk";
			}
		}

		((EditText) findViewById(R.id.me_version)).setText(String
				.valueOf(version));
		((EditText) findViewById(R.id.me_name)).setText(destinationName);

	}
}
