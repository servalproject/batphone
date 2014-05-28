package org.servalproject.ui;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;

import org.servalproject.PeerBinder;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.IPeer;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerComparator;
import org.servalproject.servald.PeerListService;
import org.servalproject.servaldna.AbstractId;
import org.servalproject.servaldna.MdpPacket;
import org.servalproject.servaldna.SubscriberId;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by jeremy on 28/05/14.
 */
public class WhitelistActivity extends ListActivity implements IPeerListListener {
	private static final String TAG = "Whitelist";
	private ServalBatPhoneApplication app;
	private SimpleAdapter<Entry> adapter;
	private List<Entry> items = new ArrayList<Entry>();
	private File whitelist;
	private int mdp_port;

	public static final String EXTRA_NAME="name";
	public static final String EXTRA_PORT="port";

	class Entry implements IPeer {
		final Peer peer;
		boolean whitelisted=false;

		Entry(Peer peer){
			this.peer = peer;
		}

		@Override
		public SubscriberId getSubscriberId() {
			return peer.getSubscriberId();
		}

		@Override
		public long getContactId() {
			return peer.getContactId();
		}

		@Override
		public void addContact(Context context) throws RemoteException, OperationApplicationException {
			peer.addContact(context);
		}

		@Override
		public boolean hasName() {
			return peer.hasName();
		}

		@Override
		public String getSortString() {
			return peer.getSortString();
		}

		@Override
		public String getDid() {
			return peer.getDid();
		}

		@Override
		public boolean isReachable() {
			return peer.isReachable();
		}

		@Override
		public boolean isChecked() {
			return whitelisted;
		}

		@Override
		public void onClick(View view) {
			switch (view.getId()){
				case R.id.check:{
					CheckBox checkbox = (CheckBox)view;
					this.whitelisted = checkbox.isChecked();
					Log.v(TAG, peer.getSubscriberId().abbreviation()+".whitelisted = "+whitelisted);
					writeWhiteList();
					break;
				}
			}
		}

		@Override
		public String toString() {
			return peer.toString();
		}

		@Override
		public int hashCode() {
			return peer.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Entry){
				Entry s = (Entry)o;
				return this.peer.equals(s.peer);
			}
			return false;
		}

	}
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.app = ServalBatPhoneApplication.context;
		PeerBinder<Entry> binder = new PeerBinder<Entry>(this);
		binder.showCall = false;
		binder.showChat = false;
		binder.showContact = false;
		binder.showCheckbox = true;
		adapter = new SimpleAdapter<Entry>(this, binder);
		adapter.setItems(items);
		setListAdapter(adapter);

		Intent i = this.getIntent();
		this.whitelist = new File(i.getStringExtra(EXTRA_NAME));
		this.mdp_port = i.getIntExtra(EXTRA_PORT, 0);
	}

	private void readWhiteList(){
		// TODO improve parser....
		items.clear();
		try {
			DataInputStream i = new DataInputStream(new FileInputStream(whitelist));
			try {
				String line;
				while((line = i.readLine())!=null){
					Log.v(TAG, "Line: "+line);
					String fields[]=line.split(" ");
					Log.v(TAG, "fields: "+fields.length+", "+fields[0]);
					if (fields.length < 4 || !"allow".equals(fields[0]))
						continue;

					try {
						Log.v(TAG, "sid: "+fields[3]);
						SubscriberId s = new SubscriberId(fields[3]);
						Entry e = new Entry(PeerListService.getPeer(s));
						if (items.contains(e))
							continue;
						e.whitelisted = true;
						Log.v(TAG, "Adding "+e.toString());
						items.add(e);
					} catch (AbstractId.InvalidHexException e) {
						Log.e(TAG, e.getMessage());
						continue;
					}
				}
			} finally {
				i.close();
			}
		}catch (IOException e){
			Log.e(TAG, e.getMessage(), e);
		}
		adapter.notifyDataSetChanged();
	}

	private void writeWhiteList(){
		try {
			FileOutputStream o = new FileOutputStream(whitelist);
			try{
				boolean hasOne=false;
				for(Entry e:items){
					if (!e.whitelisted)
						continue;
					hasOne=true;
					o.write(("allow *:"+mdp_port+" <> "+e.peer.getSubscriberId()+"\n").getBytes());
					o.write(("allow *:"+MdpPacket.MDP_PORT_SERVICE_DISCOVERY+" <> "+e.peer.getSubscriberId()+"\n").getBytes());
				}
				if (hasOne) {
					o.write(("drop *:" + mdp_port + " <> *\n").getBytes());
					o.write(("drop *:" + MdpPacket.MDP_PORT_SERVICE_DISCOVERY + " <> *\n").getBytes());
				}
			}finally{
				o.close();
			}
		}catch(IOException e){
			Log.e(TAG, e.getMessage(), e);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		readWhiteList();
		PeerListService.addListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		PeerListService.removeListener(this);
	}

	@Override
	public void peerChanged(Peer p) {
		if (!app.isMainThread()){
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					adapter.notifyDataSetChanged();
				}
			});
			return;
		}
		Entry e = new Entry(p);
		if (!items.contains(e)){
			if (!p.isReachable())
				return;
			items.add(e);
		}
		Collections.sort(items, new PeerComparator());
		adapter.notifyDataSetChanged();
	}
}
