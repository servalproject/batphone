package org.servalproject.wizard;

import org.servalproject.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
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

				LayoutInflater li = LayoutInflater.from(Wizard.this);
				View view = li.inflate(R.layout.warning_dialog, null);

				AlertDialog.Builder alert = new AlertDialog.Builder(Wizard.this);
				alert.setView(view);
				alert.setPositiveButton("I Agree",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int whichButton) {
								startActivity(new Intent(Wizard.this,
										Instructions.class));
							}
						});
				alert.setNegativeButton("Cancel", null);
				alert.show();

			}
		});
	}

}
