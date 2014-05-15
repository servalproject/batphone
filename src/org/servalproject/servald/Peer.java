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
package org.servalproject.servald;

import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.account.AccountService;
import org.servalproject.batphone.CallHandler;
import org.servalproject.messages.ShowConversationActivity;
import org.servalproject.servaldna.SubscriberId;

import java.io.IOException;


public class Peer implements IPeer {
	private static final String TAG = "Peer";
	public long contactId = -1;
	String contactName;
	public long cacheContactUntil = 0;
	public long cacheUntil = 0;
	public long nextRequest = 0;
	public final SubscriberId sid;
    private SubscriberId transmitter;
    private int hop_count;

	// did / name resolved from looking up the sid
	public String did;
	public String name;

	// every peer must have a sid
	Peer(SubscriberId sid) {
		this.sid = sid;
	}

	@Override
	public String getSortString() {
		return getContactName() + did + sid;
	}

	public String getDisplayName() {
		if (contactName != null && !contactName.equals(""))
			return contactName;
		if (name != null && !name.equals(""))
			return name;
		if (did != null && !did.equals(""))
			return did;
		return sid.abbreviation();
	}

	@Override
	public boolean hasName() {
		return (contactName != null && !contactName.equals(""))
				|| (name != null && !name.equals(""));
	}

	public String getContactName() {
		if (contactName != null && !contactName.equals(""))
			return contactName;
		if (name != null && !name.equals(""))
			return name;
		return "";
	}

	public void setContactName(String contactName) {
		this.contactName = contactName;
	}

    public boolean linkChanged(SubscriberId transmitter, int hop_count){
		if (transmitter == this.transmitter && hop_count== this.hop_count)
			return false;
        this.transmitter=transmitter;
        this.hop_count=hop_count;
		return true;
    }

    public SubscriberId getTransmitter(){
        return this.transmitter;
    }

    public int getHopCount(){
        return this.hop_count;
    }

	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof Peer))
			return false;
		Peer other = (Peer) o;
		return this.sid.equals(other.sid);
	}

	@Override
	public int hashCode() {
		return sid.hashCode();
	}

	@Override
	public String toString() {
		if (contactName != null && !contactName.equals(""))
			return contactName;
		if (name != null && !name.equals(""))
			return name;
		if (did != null && !did.equals(""))
			return did;
		return sid.abbreviation();
	}

	public boolean hasDid() {
		return did != null && !did.equals("");
	}

	@Override
	public String getDid() {
		return did;
	}

	public boolean isReachable() {
		return this.transmitter!=null;
	}

	@Override
	public SubscriberId getSubscriberId() {
		return sid;
	}

	@Override
	public long getContactId() {
		return contactId;
	}

	@Override
	public void addContact(Context context) throws RemoteException,
			OperationApplicationException {
		if (contactId == -1) {
			contactId = AccountService.addContact(
					context, getContactName(), sid,
					did);
		}
	}

	public void call() throws IOException {
		CallHandler.dial(this);
	}

	public void chat(Context context){
		if (!ServalD.isRhizomeEnabled()){
			ServalBatPhoneApplication.context.displayToastMessage("Messaging cannot function without an sdcard");
			return;
		}

		// Send MeshMS by SID
		Intent intent = new Intent(
				ServalBatPhoneApplication.context, ShowConversationActivity.class);
		intent.putExtra("recipient", getSubscriberId().toHex());
		context.startActivity(intent);
	}

	@Override
	public void onClick(View view) {
		ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
		Context context = view.getContext();
		Intent intent;

		try {
			switch(view.getId()){
				case R.id.call:
					call();
					return;
				case R.id.chat:
					chat(view.getContext());
					return;
				case R.id.add_contact:
					// Create contact if required
					addContact(view.getContext());
					view.setVisibility(View.INVISIBLE);

					// immediately display/edit contact
					intent = new Intent(Intent.ACTION_VIEW,
							Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(getContactId())));
					view.getContext().startActivity(intent);
					return;
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		}
	}
}
