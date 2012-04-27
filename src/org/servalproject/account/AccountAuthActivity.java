package org.servalproject.account;

import java.io.File;
import java.io.IOException;

import org.servalproject.Control;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.Identities;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class AccountAuthActivity extends Activity {
	ServalBatPhoneApplication app;
	TextView nameView;
	TextView phoneView;

	public String readExistingNumber() {
		String primaryNumber = Identities.getCurrentDid();

		if (primaryNumber != null && !primaryNumber.equals(""))
			return primaryNumber;

		// try to get number from phone, probably wont work though...
		TelephonyManager mTelephonyMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String number = mTelephonyMgr.getLine1Number();
		if (number != null && !number.equals(""))
			return number;

		// try to read the last configured number from the sd card
		try {
			String storageState = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(storageState)
					|| Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)) {
				char[] buf = new char[128];
				File f = new File(Environment.getExternalStorageDirectory(),
						"/serval/primaryNumber");
				if (f.exists()) {
					java.io.FileReader fr = new java.io.FileReader(f);
					fr.read(buf, 0, 128);
					fr.close();
					return new String(buf).trim();
				}
				// read and tidy up file from previous version
				f = new File(Environment.getExternalStorageDirectory(),
						"/BatPhone/primaryNumber");
				if (f.exists()) {
					java.io.FileReader fr = new java.io.FileReader(f);
					fr.read(buf, 0, 128);
					fr.close();
					f.delete();
					f.getParentFile().delete();
					return new String(buf).trim();
				}
			}
		} catch (IOException e) {
		}

		return null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		app = (ServalBatPhoneApplication) getApplication();

		this.setContentView(R.layout.account_auth);

		this.nameView = (TextView) AccountAuthActivity.this
				.findViewById(R.id.name);
		this.phoneView = (TextView) AccountAuthActivity.this
				.findViewById(R.id.phone);
		this.phoneView.setText(readExistingNumber());

		Button ok = (Button) this.findViewById(R.id.Ok);
		ok.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				try {
					// TODO add name & phone number to DNA, use public key for
					// username?

					String name = nameView.getText().toString();
					String phone = phoneView.getText().toString();

					// set the primary phone number
					app.setPrimaryNumber(name, phone, false);
					Account account = AccountService
							.getAccount(AccountAuthActivity.this);
					if (account == null) {
						account = new Account("Serval Mesh",
								AccountService.TYPE);
						AccountManager am = AccountManager
								.get(AccountAuthActivity.this);

						if (!am.addAccountExplicitly(account, "", null))
							throw new IllegalStateException(
									"Failed to create account");

						AccountService.addContact(getContentResolver(),
								account, "Myself",
								Identities.getCurrentIdentity(),
								Identities.getCurrentDid());

						Intent intent = AccountAuthActivity.this.getIntent();
						if (intent != null && intent.getExtras() != null) {
							AccountAuthenticatorResponse response = intent
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

						Intent serviceIntent = new Intent(
								AccountAuthActivity.this, Control.class);
						startService(serviceIntent);
					}
					finish();
				} catch (Exception e) {
					Log.e("BatPhone", e.getMessage(), e);
					ServalBatPhoneApplication.context.displayToastMessage(e
							.getMessage());
				}
			}
		});
		super.onCreate(savedInstanceState);
	}

}
