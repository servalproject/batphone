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

import org.servalproject.Main;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

public class SetPhoneNumber extends Activity {
	ServalBatPhoneApplication app;

	EditText number;
	Button button;
	ProgressBar progress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app=(ServalBatPhoneApplication)this.getApplication();

		setContentView(R.layout.set_phone_no);
		number = (EditText)this.findViewById(R.id.batphoneNumberText);
		number.setText(app.getPrimaryNumber());
		number.setSelectAllOnFocus(true);

		button = (Button) this.findViewById(R.id.btnPhOk);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				new Thread() {
					@Override
					public void run() {
						try {
							app.setPrimaryNumber(number.getText().toString());

							Intent intent = new Intent(SetPhoneNumber.this,
									Main.class);
							intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
							SetPhoneNumber.this.startActivity(intent);

							app.startAdhoc();

						} catch (IllegalArgumentException e) {
							app.displayToastMessage(e.getMessage());
						} catch (Exception e) {
							Log.e("BatPhone", e.toString(), e);
							app.displayToastMessage(e.toString());
						}
					}
				}.start();
			}
		});

		progress = (ProgressBar) this.findViewById(R.id.progress);
	}

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			int stateOrd = intent.getIntExtra(
					ServalBatPhoneApplication.EXTRA_STATE, 0);
			State state = State.values()[stateOrd];
			stateChanged(state);
		}
	};
	boolean registered = false;

	private void stateChanged(State state) {
		// TODO update display of On/Off button
		switch (state) {
		case Installing:
			progress.setVisibility(View.VISIBLE);
			button.setVisibility(View.GONE);
			break;
		default:
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
}
