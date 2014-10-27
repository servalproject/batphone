/*
 * Copyright (C) 2012 The Serval Project
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
package org.servalproject.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;

/**
 * main activity for contact management
 */
public class ContactsActivity extends Activity implements OnClickListener {

	/*
	 * private class level constants
	 */
	// private final boolean V_LOG = true;
	private final String TAG = "ContactsActivity";

	private final int PEER_LIST_RETURN = 0;

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.contacts_activity);

		// attach handlers to the button
		ViewGroup mButton = (ViewGroup) findViewById(R.id.contacts_ui_lookup_phone_contact);
		mButton.setOnClickListener(this);

		mButton = (ViewGroup) findViewById(R.id.contacts_ui_lookup_serval_contact);
		mButton.setOnClickListener(this);

	}

	@Override
	public void onClick(View view) {
		Intent mIntent;

		switch (view.getId()) {
		case R.id.contacts_ui_lookup_phone_contact:
			try{
				// show the contact address book
				mIntent = new Intent(Intent.ACTION_VIEW);
				mIntent.setData(Uri.parse("content://contacts/people"));
				mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(mIntent);
			}catch(ActivityNotFoundException e){
				Log.e(TAG, e.getMessage(), e);
				ServalBatPhoneApplication.context.displayToastMessage(e.getMessage());
			}
			break;
		case R.id.contacts_ui_lookup_serval_contact:
			// show the peer list screen
			mIntent = new Intent(this, org.servalproject.PeerList.class);
			startActivityForResult(mIntent, PEER_LIST_RETURN);
			break;
		default:
			Log.w(TAG, "unknown view called onClick method");
		}
	}

}
