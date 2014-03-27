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

package org.servalproject;

/**
 *
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 *
 *         Peer List fetches a list of known peers from the PeerListService.
 *         When a peer is received from the service this activity will attempt
 *         to resolve the peer by calling ServalD in an async task.
 */

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.servalproject.messages.ShowConversationActivity;
import org.servalproject.servald.IPeer;
import org.servalproject.servald.ServalD;

import java.util.List;

public class PeerListAdapter<T extends IPeer> extends ArrayAdapter<T> {
	public PeerListAdapter(Context context, List<T> peers) {
		super(context, R.layout.peer, R.id.Name, peers);
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		View ret = super.getView(position, convertView, parent);
		T p = this.getItem(position);

		TextView displayName = (TextView) ret.findViewById(R.id.Name);
		TextView displaySid = (TextView) ret.findViewById(R.id.sid);
		TextView displayNumber = (TextView) ret.findViewById(R.id.Number);
		View chat = ret.findViewById(R.id.chat);
		View call = ret.findViewById(R.id.call);
		View contact = ret.findViewById(R.id.add_contact);

		displaySid.setText(p.getSubscriberId().abbreviation());
		displayNumber.setText(p.getDid());

		if (p.getSubscriberId().isBroadcast()) {
			call.setVisibility(View.INVISIBLE);
		} else {
			call.setVisibility(View.VISIBLE);
		}

		if (p.getContactId() >= 0) {
			contact.setVisibility(View.INVISIBLE);
		} else {
			contact.setVisibility(View.VISIBLE);
		}

		if (p.isReachable()){
			displayName.setTextColor(Color.WHITE);
			displayNumber.setTextColor(Color.WHITE);
			displaySid.setTextColor(Color.WHITE);
		}else{
			displayName.setTextColor(Color.GRAY);
			displayNumber.setTextColor(Color.GRAY);
			displaySid.setTextColor(Color.GRAY);
		}

		chat.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ServalBatPhoneApplication app = ServalBatPhoneApplication.context;

				T p = getItem(position);

				if (!ServalD.isRhizomeEnabled()) {
					app.displayToastMessage("Messaging cannot function without an sdcard");
					return;
				}

				// Send MeshMS by SID
				Intent intent = new Intent(
						app, ShowConversationActivity.class);
				intent.putExtra("recipient", p.getSubscriberId().toHex());
				getContext().startActivity(intent);
			}
		});
		contact.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				T p = getItem(position);

				// Create contact if required
				try {
					p.addContact(getContext());

					v.setVisibility(View.INVISIBLE);

					// now display/edit contact
					Intent intent = new Intent(Intent.ACTION_VIEW,
							Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(p.getContactId())));
					getContext().startActivity(intent);
				} catch (Exception e) {
					Log.e("PeerList", e.getMessage(), e);
					ServalBatPhoneApplication.context.displayToastMessage(e
							.getMessage());
				}
			}
		});

		return ret;
	}

}