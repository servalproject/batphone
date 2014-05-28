package org.servalproject.ui;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.servalproject.PeerBinder;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.IPeer;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.TunnelService;
import org.servalproject.servaldna.AsyncResult;
import org.servalproject.servaldna.MdpServiceLookup;
import org.servalproject.servaldna.SubscriberId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jeremy on 14/05/14.
 */
public class TunnelSearchActivity extends ListActivity implements
		IPeerListListener, AsyncResult<MdpServiceLookup.ServiceResult>, View.OnClickListener {
	private static final String TAG = "ProxySearch";
	private MdpServiceLookup lookup;
	private ServalBatPhoneApplication app;
	private SimpleAdapter<SearchResult> adapter;
	private Handler handler;
	private HandlerThread handlerThread;
	private Runnable searcher=new Runnable() {
		@Override
		public void run() {
			search();
		}
	};
	private List<SearchResult> items = new ArrayList<SearchResult>();
	private Button socks;
	private Button http;
	private Button stop;
	private TextView address;

	private class SearchResult implements IPeer {
		final Peer peer;
		final int remotePort;
		final String type;
		final String name;

		private SearchResult(Peer peer, String type, int remotePort, String name){
			this.peer = peer;
			this.remotePort = remotePort;
			this.type = type;
			this.name = name;
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
			return name != null || peer.hasName();
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
			return false;
		}

		@Override
		public int hashCode() {
			return peer.hashCode() ^ this.remotePort ^ this.type.hashCode();
		}

		@Override
		public String toString() {
			if (name != null)
				return name;
			return peer.toString();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof SearchResult){
				SearchResult s = (SearchResult)o;
				return this.type.equals(s.type)
					&& this.remotePort == s.remotePort
					&& this.peer.equals(s.peer);
			}
			return false;
		}

		@Override
		public void onClick(View view) {
			peer.onClick(view);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tunnel);

		app = ServalBatPhoneApplication.context;
		handlerThread = new HandlerThread("Search");
		handlerThread.start();
		handler = new Handler(handlerThread.getLooper());
		PeerBinder<SearchResult> binder = new PeerBinder<SearchResult>(this){
			@Override
			public int getTextColour(SearchResult searchResult) {
				TunnelService service = TunnelService.getInstance();
				if (service!=null && searchResult.getSubscriberId().equals(service.getPeer())){
					return searchResult.isReachable() ? Color.GREEN : Color.RED;
				}
				return super.getTextColour(searchResult);
			}
		};
		binder.showCall = false;
		binder.showChat = false;
		binder.showContact = false;
		adapter = new SimpleAdapter<SearchResult>(this, binder);
		adapter.setItems(items);
		setListAdapter(adapter);

		socks=(Button)findViewById(R.id.socks);
		socks.setOnClickListener(this);
		http=(Button)findViewById(R.id.http);
		http.setOnClickListener(this);
		stop=(Button)findViewById(R.id.stop);
		stop.setOnClickListener(this);
		address = (TextView)findViewById(R.id.address);
		findViewById(R.id.http_whitelist).setOnClickListener(this);
		findViewById(R.id.socks_whitelist).setOnClickListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		PeerListService.removeListener(this);
		handler.removeCallbacks(searcher);
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (lookup != null) {
					lookup.close();
					lookup = null;
				}
			}
		});
		unregisterReceiver(receiver);
	}

	private void updateUI(){
		TunnelService service = TunnelService.getInstance();
		String addressText="Not Running";

		socks.setEnabled(service == null);
		http.setEnabled(service == null);
		stop.setEnabled(service != null);

		if (service != null){
			String type = service.getType();
			if (service.isClient()){
				addressText="127.0.0.1:"+service.getIpPort();
			}else if (TunnelService.HTTP_PROXY.equals(type)){
				addressText="Running HTTP proxy";
			}else if (TunnelService.SOCKS5.equals(type)){
				addressText="Running SOCKS proxy";
			}
		}

		address.setText(addressText);
		adapter.notifyDataSetChanged();
	}

	BroadcastReceiver receiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(TunnelService.ACTION_STATE)){
				updateUI();
			}
		}
	};

	@Override
	protected void onResume() {
		super.onResume();
		items.clear();
		adapter.notifyDataSetChanged();
		PeerListService.addListener(this);
		handler.post(searcher);
		registerReceiver(receiver, new IntentFilter(TunnelService.ACTION_STATE));
		updateUI();
	}

	private void search(){
		try {
			if (lookup == null)
				lookup = app.server.getMdpServiceLookup(this);
			lookup.sendRequest(SubscriberId.broadcastSid, TunnelService.HTTP_PROXY +".*");
			lookup.sendRequest(SubscriberId.broadcastSid, TunnelService.SOCKS5 +".*");
			handler.postDelayed(searcher, 3000);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	@Override
	public void result(final MdpServiceLookup.ServiceResult nextResult) {
		if (!app.isMainThread()){
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					result(nextResult);
				}
			});
			return;
		}
		String type = TunnelService.HTTP_PROXY;
		String name = getString(R.string.http_proxy);
		int port = Integer.parseInt(nextResult.getProperty(type +".msp.port", "0"));
		if (port==0){
			type = TunnelService.SOCKS5;
			port = Integer.parseInt(nextResult.getProperty(type +".msp.port", "0"));
			name = getString(R.string.socks5);
		}
		if (port==0){
			Log.v(TAG, "Ignored result "+nextResult.toString());
			return;
		}
		Peer p=PeerListService.getPeer(nextResult.subscriberId);
		name = nextResult.getProperty(type +".name", name);
		SearchResult result = new SearchResult(p, type, port, name);
		if (!items.contains(result)) {
			items.add(result);
			adapter.notifyDataSetChanged();
		}
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
		adapter.notifyDataSetChanged();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		SearchResult result = items.get(position);
		Intent intent = new Intent(this, TunnelService.class);
		intent.putExtra(TunnelService.EXTRA_MODE, TunnelService.CLIENT);
		intent.putExtra(TunnelService.EXTRA_TYPE, result.type);
		intent.putExtra(TunnelService.EXTRA_PORT, result.remotePort);
		intent.putExtra(TunnelService.EXTRA_PEER, result.getSubscriberId().toHex());
		startService(intent);
	}

	@Override
	public void onClick(View view) {
		switch(view.getId()){
			case R.id.socks: {
				Intent intent = new Intent(this, TunnelService.class);
				intent.putExtra(TunnelService.EXTRA_MODE, TunnelService.SERVER);
				intent.putExtra(TunnelService.EXTRA_TYPE, TunnelService.SOCKS5);
				startService(intent);
				break;
			}
			case R.id.http: {
				Intent intent = new Intent(this, TunnelService.class);
				intent.putExtra(TunnelService.EXTRA_MODE, TunnelService.SERVER);
				intent.putExtra(TunnelService.EXTRA_TYPE, TunnelService.HTTP_PROXY);
				startService(intent);
				break;
			}
			case R.id.stop: {
				Intent intent = new Intent(this, TunnelService.class);
				stopService(intent);
				break;
			}
			case R.id.http_whitelist: {
				Intent intent = new Intent(this, WhitelistActivity.class);
				intent.putExtra(WhitelistActivity.EXTRA_NAME, TunnelService.getHttpFilterFile().getAbsolutePath());
				intent.putExtra(WhitelistActivity.EXTRA_PORT, TunnelService.HTTP_PORT);
				startActivity(intent);
				break;
			}
			case R.id.socks_whitelist: {
				Intent intent = new Intent(this, WhitelistActivity.class);
				intent.putExtra(WhitelistActivity.EXTRA_NAME, TunnelService.getSocksFilterFile().getAbsolutePath());
				intent.putExtra(WhitelistActivity.EXTRA_PORT, TunnelService.SOCKS_PORT);
				startActivity(intent);
				break;
			}
		}
	}

}
