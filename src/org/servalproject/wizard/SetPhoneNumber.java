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

import java.io.File;
import java.io.IOException;

import org.servalproject.Control;
import org.servalproject.Main;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.account.AccountService;
import org.servalproject.servald.Identities;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

public class SetPhoneNumber extends Activity {
	ServalBatPhoneApplication app;

	EditText number;
	EditText name;
	Button button;
	ProgressBar progress;

	public String readExistingName() {
		// XXX should get stored to SD card?
		String name = Identities.getCurrentName();
		return name;
	}

	public String readExistingNumber() {
		// try to get number from phone, probably wont work though...
		TelephonyManager mTelephonyMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String number = mTelephonyMgr.getLine1Number();
		if (number != null && !number.equals(""))
			return number;

		// try to read the last configured number from the sd card
		try {
			char[] buf = new char[128];
			File f = new File(ServalBatPhoneApplication.getStorageFolder(),
					"/serval/primaryNumber");
			if (f.exists()) {
				java.io.FileReader fr = new java.io.FileReader(f);
				fr.read(buf, 0, 128);
				fr.close();
				return new String(buf).trim();
			}
			// read and tidy up file from previous version
			f = new File(ServalBatPhoneApplication.getStorageFolder(),
					"/BatPhone/primaryNumber");
			if (f.exists()) {
				java.io.FileReader fr = new java.io.FileReader(f);
				fr.read(buf, 0, 128);
				fr.close();
				f.delete();
				f.getParentFile().delete();
				return new String(buf).trim();
			}
		} catch (IOException e) {
		}

		// return a randomly assigned number
		String primaryNumber = Identities.getCurrentDid();

		if (primaryNumber != null && !primaryNumber.equals(""))
			return primaryNumber;

		return null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app=(ServalBatPhoneApplication)this.getApplication();

		setContentView(R.layout.set_phone_no);
		number = (EditText)this.findViewById(R.id.batphoneNumberText);
		number.setText(readExistingNumber());
		number.setSelectAllOnFocus(true);

		name = (EditText) this.findViewById(R.id.batphoneNameText);
		name.setText(readExistingName());
		name.setSelectAllOnFocus(true);

		button = (Button) this.findViewById(R.id.btnPhOk);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				button.setEnabled(false);

				new AsyncTask<Void, Void, Void>() {
					@Override
					protected Void doInBackground(Void... params) {
						try {
							app.setPrimaryNumber(number.getText().toString(),
									name.getText().toString(),
									false);

							// create the serval android acount if it doesn't
							// already exist
							Account account = AccountService
									.getAccount(SetPhoneNumber.this);
							if (account == null) {
								account = new Account("Serval Mesh",
										AccountService.TYPE);
								AccountManager am = AccountManager
										.get(SetPhoneNumber.this);

								if (!am.addAccountExplicitly(account, "", null))
									throw new IllegalStateException(
											"Failed to create account");

								Intent ourIntent = SetPhoneNumber.this
										.getIntent();
								if (ourIntent != null
										&& ourIntent.getExtras() != null) {
									AccountAuthenticatorResponse response = ourIntent
											.getExtras()
											.getParcelable(
													AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
									if (response != null) {
										Bundle result = new Bundle();
										result.putString(
												AccountManager.KEY_ACCOUNT_NAME,
												account.name);
										result.putString(
												AccountManager.KEY_ACCOUNT_TYPE,
												AccountService.TYPE);
										response.onResult(result);
									}
								}
							}

							Intent serviceIntent = new Intent(
									SetPhoneNumber.this, Control.class);
							startService(serviceIntent);

							Intent intent = new Intent(SetPhoneNumber.this,
									Main.class);
							intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
							SetPhoneNumber.this.startActivity(intent);

						} catch (IllegalArgumentException e) {
							app.displayToastMessage(e.getMessage());
						} catch (Exception e) {
							Log.e("BatPhone", e.toString(), e);
							app.displayToastMessage(e.toString());
						}
						return null;
					}

					@Override
					protected void onPostExecute(Void result) {
						button.setEnabled(true);
					}
				}.execute((Void[]) null);
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
		case Upgrading:
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
