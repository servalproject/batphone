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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.servalproject.account.AccountService;
import org.servalproject.batphone.BatPhone;
import org.servalproject.servald.Identities;
import org.servalproject.servald.SubscriberId;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class PeerList extends ListActivity {

	Adapter listAdapter;

	class Adapter extends ArrayAdapter<Peer> {
		public Adapter(Context context) {
			super(context, R.layout.peer, R.id.Number);
		}

		@Override
		public View getView(final int position, View convertView,
				ViewGroup parent) {
			View ret = super.getView(position, convertView, parent);
			View chat = ret.findViewById(R.id.chat);
			chat.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Peer p = listAdapter.getItem(position);
					if (p.phoneNumber == null)
						return;

					Intent intent = new Intent(Intent.ACTION_SENDTO, Uri
							.parse("sms:" + p.phoneNumber));
					intent.addCategory(Intent.CATEGORY_DEFAULT);
					PeerList.this.startActivity(intent);
				}
			});
			return ret;
		}

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		listAdapter = new Adapter(this);
		listAdapter.setNotifyOnChange(false);
		this.setListAdapter(listAdapter);

		ListView lv = getListView();

		// TODO Long click listener for more options, eg text message
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Peer p=listAdapter.getItem(position);
				if (p.phoneNumber == null)
					return;

				BatPhone.getEngine().call(p.phoneNumber);
			}
		  });

	}

	private class Peer{
		SubscriberId sid;
		int linkScore=-1;
		String phoneNumber;
		String name;
		long contactId = -1;
		int retries;
		int pingTime;
		int ttl = -1;
		boolean displayed=false;

		boolean tempInPeerList=false;
		boolean tempDnaResponse=false;

		Peer(SubscriberId sid) {
			this.sid = sid;
		}

		private String getDisplayNumber() {
			if (name != null)
				return name;
			if (phoneNumber != null)
				return phoneNumber;
			// only display the first part of a SID
			return this.sid.toString().substring(0, 9) + "*";
		}

		private String getNetworkState() {

			if (ttl <= 0)
				return "";
			if (ttl == 64)
				return " Direct";
			int hops = 64 - (ttl - 1);

			return " " + hops + " Hops";
		}

		@Override
		public String toString() {
			return getDisplayNumber() + getNetworkState();
		}
	}

	Map<SubscriberId, Peer> peerMap = new HashMap<SubscriberId, Peer>();

	class PollThread extends Thread{
		@Override
		public void run() {
				// TODO return results as they change???

				ServalBatPhoneApplication app = (ServalBatPhoneApplication) PeerList.this
						.getApplication();

			// Get peer list first time round, and remember version
			Identities.getPeers();
			long last_peer_list_update_time = Identities
					.getLastPeerListUpdateTime();

				while(true){
				try {

					// Wait until peer list updates to a new version
					while (Identities.getLastPeerListUpdateTime() == last_peer_list_update_time)
					{
						Thread.sleep(50);
						// Ask Identities to refetch list. It will only do it if
						// it thinks it needs
						// refreshing.
						Identities.getPeers();
					}
					last_peer_list_update_time = Identities
							.getLastPeerListUpdateTime();

					// Get list of overlay peers from servald
					ArrayList<PeerRecord> peers = Identities.getPeers();

					if (peers != null) {
						for (PeerRecord peer : peers) {
							SubscriberId sid = peer.getSid();
							Peer p = peerMap.get(sid);
							if (p == null) {
								p = new Peer(sid);
								peerMap.put(sid, p);
							}
							p.linkScore = peer.getLinkScore();
							p.tempInPeerList = true;
							p.phoneNumber = peer.did;
						}
					}

					final SubscriberId ourPrimary = Identities
							.getCurrentIdentity();

					for (Peer p:peerMap.values()){
						if (p != null)
							if (p.contactId == -1)
								resolveContact(p);
					}
					PeerList.this.runOnUiThread(updateDisplay);
				} catch (Exception e) {
					Log.d("BatPhone", e.toString(), e);
				}
				try {
					sleep(1000);
				} catch (InterruptedException e) {
				} catch (Exception e) {
					Log.d("BatPhone", e.toString(), e);
				}
				}

		}
	}

	PollThread pollThread;
	Runnable updateDisplay=new Runnable(){
		@Override
		public void run() {
			for (Peer p:peerMap.values()){
				if (p.displayed) continue;
				listAdapter.add(p);
				p.displayed=true;
			}

			listAdapter.sort(new Comparator<Peer>() {
				@Override
				public int compare(Peer object1, Peer object2) {
					return object1.getDisplayNumber().compareTo(
							object2.getDisplayNumber());
				}

			});

			listAdapter.notifyDataSetChanged();
		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		if (pollThread!=null){
			pollThread.interrupt();
			pollThread=null;
		}
	}

	public void resolveContact(Peer p) {
		if (p.contactId != -1 || p.sid == null)
			return;
		ContentResolver resolver = this.getContentResolver();
		p.contactId = AccountService.getContactId(resolver, p.sid);
		boolean subscriberFound = (p.contactId != -1);

		if (p.contactId == -1 && p.phoneNumber != null)
			p.contactId = AccountService.getContactId(resolver, p.phoneNumber);

		if (p.contactId == -1) {
			p.contactId = -2;
		} else
			p.name = AccountService.getContactName(resolver, p.contactId);

		if (!subscriberFound) {
			AccountService.addContact(this, p.name, p.sid, p.phoneNumber);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		pollThread=new PollThread();
		pollThread.start();
	}
}
