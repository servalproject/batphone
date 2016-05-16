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

package org.servalproject.wizard;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;

public class Wizard extends Activity {

	private ServalBatPhoneApplication app;
	private Button button;
	private ProgressBar progress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = ServalBatPhoneApplication.context;

		setContentView(R.layout.wizard);

		button = (Button) this.findViewById(R.id.btnwizard);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivityForResult(new Intent(Wizard.this,
						SetPhoneNumber.class), 0);
			}
		});
		progress = (ProgressBar) this.findViewById(R.id.progress);
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			int stateOrd = intent.getIntExtra(
					ServalBatPhoneApplication.EXTRA_STATE, 0);
			ServalBatPhoneApplication.State state = ServalBatPhoneApplication.State.values()[stateOrd];
			stateChanged(state);
		}
	};
	private boolean registered = false;

	private void stateChanged(ServalBatPhoneApplication.State state) {
		switch (state) {
			case NotInstalled:
			case Installing:
			case Upgrading:
				progress.setVisibility(View.VISIBLE);
				button.setVisibility(View.GONE);
				break;
			case Running:
			case RequireDidName:
				progress.setVisibility(View.GONE);
				button.setVisibility(View.VISIBLE);
				break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		IntentFilter filter = new IntentFilter();
		filter.addAction(ServalBatPhoneApplication.ACTION_STATE);
		this.registerReceiver(receiver, filter);
		registered = true;

		stateChanged(app.getState());
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (registered)
			this.unregisterReceiver(receiver);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			setResult(RESULT_OK);
			finish();
		}
	}
}
