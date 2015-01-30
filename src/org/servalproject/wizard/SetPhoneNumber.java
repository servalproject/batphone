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

/**
 * @author Paul Gardner-Stephen <paul@servalproject.org>
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 * @author Romana Challans <romana@servalproject.org>
 *
 *  Wizard: Set Phone Number And Name.
 *  Used for initial run and is called again to reset phone number.
 **/
package org.servalproject.wizard;

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
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.servalproject.Main;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.account.AccountService;
import org.servalproject.servald.Identity;

import java.util.List;

public class SetPhoneNumber extends Activity {
	ServalBatPhoneApplication app;

	EditText number;
	EditText name;
	TextView sid;
	Button button;
	ProgressBar progress;
	Identity identity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app=(ServalBatPhoneApplication)this.getApplication();

		setContentView(R.layout.set_phone_no);
		number = (EditText)this.findViewById(R.id.batphoneNumberText);
		number.setSelectAllOnFocus(true);

		name = (EditText) this.findViewById(R.id.batphoneNameText);
		name.setSelectAllOnFocus(true);

		sid = (TextView) this.findViewById(R.id.sidText);

		button = (Button) this.findViewById(R.id.btnPhOk);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				button.setEnabled(false);

				new AsyncTask<Void, Void, Boolean>() {

					@Override
					protected Boolean doInBackground(Void... params) {
						try {
							identity.setDetails(app,
									number.getText().toString(),
									name.getText().toString());

							// create the serval android acount if it doesn't
							// already exist
							Account account = AccountService
									.getAccount(SetPhoneNumber.this);
							if (account == null) {
								account = AccountService.createAccount(SetPhoneNumber.this, getString(R.string.app_name));

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

							return true;
						} catch (IllegalArgumentException e) {
							app.displayToastMessage(e.getMessage());
						} catch (Exception e) {
							Log.e("BatPhone", e.getMessage(), e);
							app.displayToastMessage(e.getMessage());
						}
						return false;
					}

					@Override
					protected void onPostExecute(Boolean result) {
						if (result) {
                            app.mainIdentityUpdated(identity);
							Intent intent = new Intent(SetPhoneNumber.this,
									Main.class);
							intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
							SetPhoneNumber.this.startActivity(intent);
							SetPhoneNumber.this.setResult(RESULT_OK);
							SetPhoneNumber.this.finish();
							return;
						}
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

		String existingName = null;
		String existingNumber = null;
		String sidAbbrev = null;

		List<Identity> identities = Identity.getIdentities();

		if (identities.size() > 0) {
			identity = identities.get(0);

			existingName = identity.getName();
			existingNumber = identity.getDid();
			sidAbbrev = identity.subscriberId.abbreviation();
		} else {
			// try to get number from phone, probably wont work though...
			TelephonyManager mTelephonyMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			existingNumber = mTelephonyMgr.getLine1Number();

			try {
				identity = Identity.createIdentity();
				sidAbbrev = identity.subscriberId.abbreviation();
			} catch (Exception e) {
				Log.e("SetPhoneNumber", e.getMessage(), e);
				app.displayToastMessage(e.getMessage());
			}
		}

		sid.setText(sidAbbrev);
		number.setText(existingNumber);
		name.setText(existingName);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (registered)
			this.unregisterReceiver(receiver);
	}
}
