package org.servalproject.account;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class AccountAuthActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		this.setContentView(R.layout.account_auth);
		Button ok = (Button) this.findViewById(R.id.Ok);
		ok.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				try {
					// TODO add name & phone number to DNA, use public key for
					// username?

					String username = "Serval Mesh";
					Account account = new Account(username, AccountService.TYPE);
					AccountManager am = AccountManager
							.get(AccountAuthActivity.this);
					Intent intent = AccountAuthActivity.this.getIntent();

					if (intent.getAction().equals(AccountService.ACTION_ADD)) {
						if (!am.addAccountExplicitly(account, "", null))
							throw new IllegalStateException(
									"Failed to create account");

						ServalBatPhoneApplication app = (ServalBatPhoneApplication) getApplication();

						AccountService.addContact(getContentResolver(),
								account, "Myself", app.getSubscriberId(),
								app.getPrimaryNumber());
						AccountAuthenticatorResponse response = intent
								.getExtras()
								.getParcelable(
										AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
						Bundle result = new Bundle();
						result.putString(AccountManager.KEY_ACCOUNT_NAME,
								username);
						result.putString(AccountManager.KEY_ACCOUNT_TYPE,
								AccountService.TYPE);
						response.onResult(result);
					} else {
						am.setPassword(account, "");
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
