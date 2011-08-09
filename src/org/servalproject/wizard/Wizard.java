package org.servalproject.wizard;

import org.servalproject.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Wizard extends Activity {

	@Override
	public void onBackPressed() {
		// Don't let the user go back until they've finished.
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wizard);

		Button b = (Button) this.findViewById(R.id.btnwizard);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				// TODO disclaimer alert...

				startActivity(new Intent(Wizard.this, Instructions.class));
			}
		});
	}

}
