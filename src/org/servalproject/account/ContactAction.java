package org.servalproject.account;

import org.servalproject.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class ContactAction extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		this.setContentView(R.layout.contact_action);
		TextView text = (TextView) this.findViewById(R.id.text);
		Intent intent = this.getIntent();
		StringBuilder sb = new StringBuilder();
		sb.append("Action: ").append(intent.getAction()).append('\n')
				.append("Data: ").append(intent.getData().toString());
		text.setText(sb.toString());
		super.onCreate(savedInstanceState);
	}

}
