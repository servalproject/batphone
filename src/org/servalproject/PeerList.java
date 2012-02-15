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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.servalproject.account.AccountService;
import org.servalproject.batman.PeerRecord;
import org.servalproject.dna.Dna;
import org.servalproject.dna.Packet;
import org.servalproject.dna.PeerConversation;
import org.servalproject.dna.SubscriberId;
import org.servalproject.dna.VariableResults;
import org.servalproject.dna.VariableType;
import org.sipdroid.sipua.SipdroidEngine;

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
				if (p.phoneNumber == null || !p.inDna)
					return;

				SipdroidEngine.getEngine().call(p.phoneNumber);
			}
		  });

	}

	private class Peer{
		InetAddress addr;
		int linkScore=-1;
		SubscriberId sid;
		String phoneNumber;
		String name;
		long contactId = -1;
		int retries;
		int pingTime;
		int ttl = -1;
		boolean inDna=false;
		boolean displayed=false;

		boolean tempInPeerList=false;
		boolean tempDnaResponse=false;

		Peer(InetAddress addr){
			this.addr=addr;
		}

		private String getDisplayNumber() {
			if (name != null)
				return name;
			if (phoneNumber != null)
				return phoneNumber;
			return this.addr.getHostAddress();
		}

		private String getNetworkState() {
			if (!inDna)
				return " Unreachable";

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
	Map<InetAddress,Peer> peerMap=new HashMap<InetAddress,Peer>();

	class PollThread extends Thread{
		@Override
		public void run() {
			try{

				// TODO return results as they change???

				Dna dna=new Dna();
				dna.broadcast = true;
				ServalBatPhoneApplication app = (ServalBatPhoneApplication) PeerList.this
						.getApplication();

				while(true){

					// clear flags and display...

					for (Peer p:peerMap.values()){
						p.tempInPeerList=false;
						p.tempDnaResponse=false;
					}

					ArrayList<PeerRecord> peers = app.wifiRadio.getPeers();

					if (peers != null) {
						for (PeerRecord peer : peers) {
							InetAddress addr = peer.getAddress();
							Peer p = peerMap.get(addr);
							if (p == null) {
								p = new Peer(addr);
								peerMap.put(addr, p);
							}
							p.linkScore = peer.getLinkScore();
							p.tempInPeerList = true;
						}
					}

					dna.clearPeers();

					// add all previously known peers, we want to keep
					// trying peers that have dropped off the batman peer
					// file.
					for (Peer p : peerMap.values()) {
						dna.addStaticPeer(p.addr);
					}

					final SubscriberId ourPrimary = app.getSubscriberId();

					dna.readVariable(null, "", VariableType.DIDs, (byte) -1,
							new VariableResults() {
								@Override
								public void observedTTL(PeerConversation peer,
										SubscriberId sid, int ttl) {
									if (ourPrimary.equals(sid)) {
										return;
									}

									InetAddress addr = peer.getAddress().addr;
									Peer p = peerMap.get(addr);
									if (p == null) {
										p = new Peer(addr);
										peerMap.put(addr, p);
									}
									p.ttl = ttl;
									p.sid = sid;
								}

								@Override
								public void result(PeerConversation peer,
										SubscriberId sid, VariableType varType,
										byte instance, InputStream value) {
									try {
										// skip any responses with our own id
										if (ourPrimary.equals(sid)) {
											return;
										}

										InetAddress addr = peer.getAddress().addr;
										Peer p = peerMap.get(addr);
										if (p == null) {
											p = new Peer(addr);
											peerMap.put(addr, p);
										}
										p.phoneNumber = Packet.unpackDid(value);
										p.tempDnaResponse = true;
										p.retries = peer.getRetries();
										p.pingTime = peer.getPingTime();
										p.sid = sid;
									} catch (IOException e) {
										Log.d("BatPhone", e.toString(), e);
									}
								}
							});

					for (Peer p:peerMap.values()){
						p.inDna=p.tempDnaResponse;
						if (!p.tempInPeerList)
							p.linkScore=-1;

						if (p.contactId != -1)
							resolveContact(p);
					}
					PeerList.this.runOnUiThread(updateDisplay);
					sleep(1000);
				}
			}
			catch (InterruptedException e) {}
			catch (Exception e){
				Log.d("BatPhone",e.toString(),e);
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

		if (p.contactId == -1)
			p.contactId = AccountService.getContactId(resolver, p.phoneNumber);

		if (p.contactId == -1) {
			p.contactId = -2;
		} else
			p.name = AccountService.getContactName(resolver, p.contactId);

		if (!subscriberFound) {
			AccountService.addContact(getContentResolver(),
					p.name, p.sid, p.phoneNumber);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		pollThread=new PollThread();
		pollThread.start();
	}
}
