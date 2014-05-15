package org.servalproject.batphone;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.servalproject.PeerBinder;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.DnaResult;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servaldna.AbstractId;
import org.servalproject.servaldna.AsyncResult;
import org.servalproject.servaldna.MdpDnaLookup;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.ServalDInterfaceException;
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.ui.SimpleAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CallDirector extends ListActivity implements OnClickListener, IPeerListListener {

	private ServalBatPhoneApplication app;
	private String last_number;
	private SimpleAdapter<DnaResult> adapter;
	private Button call;
	private Button cancel;
	private Button search;
	private TextView phone_number;
	private static final String TAG = "CallDirector";
	private MdpDnaLookup searchSocket = null;
	private Handler handler;
	private List<DnaResult> items = new ArrayList<DnaResult>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.app = (ServalBatPhoneApplication)getApplication();
		handler = new Handler();
		this.setContentView(R.layout.call_director);
		call = (Button) this.findViewById(R.id.call);
		call.setOnClickListener(this);

		cancel = (Button) this.findViewById(R.id.cancel);
		cancel.setOnClickListener(this);

		search = (Button) this.findViewById(R.id.search);
		search.setOnClickListener(this);

		phone_number = (TextView) this.findViewById(R.id.phone_number);

		Intent intent = this.getIntent();

		String dialed_number = intent.getStringExtra("phone_number");
		phone_number.setText(dialed_number);
		if (dialed_number==null || dialed_number.equals("")){
			call.setVisibility(View.GONE);
			phone_number.setVisibility(View.VISIBLE);
		}else{
			call.setVisibility(View.VISIBLE);
			phone_number.setVisibility(View.GONE);
		}
		adapter = new SimpleAdapter<DnaResult>(this, new PeerBinder<DnaResult>(this));
		adapter.setItems(items);
		setListAdapter(adapter);
	}

	@Override
	public void onClick(View view) {
		switch(view.getId()){
			case R.id.call:
				BatPhone.call(phone_number.getText().toString());
				closeNow();
				break;
			case R.id.cancel:
				closeNow();
				break;
			case R.id.search:
				searchMesh(false);
				break;
		}
	}

	private void closeNow(){
		if (phone_number.getVisibility() == View.VISIBLE) {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(phone_number.getWindowToken(), 0);
		}
		finish();
	}

	@Override
	protected void onResume() {
		super.onResume();
		searchMesh(true);
		PeerListService.addListener(this);
	}

	@Override
	protected void onStop() {
		super.onStop();

		if (!isFinishing())
			finish();
	}

	@Override
	protected void onPause() {
		super.onPause();
		PeerListService.removeListener(this);
		handler.removeCallbacks(searcher);
		search_count=0;
		closeSocket();
	}

	private void closeSocket(){
		if (searchSocket != null){
			searchSocket.close();
			searchSocket = null;
		}
	}

	private int search_count=0;
	private Runnable searcher=new Runnable() {
		@Override
		public void run() {
			search();
		}
	};

	private void search(){
		try {
			handler.removeCallbacks(searcher);

			if (searchSocket == null){
				searchSocket = app.server.getMdpDnaLookup(new AsyncResult<ServalDCommand.LookupResult>() {
					@Override
					public void result(ServalDCommand.LookupResult nextResult) {
						try {
							final DnaResult result = new DnaResult(nextResult);
							handler.post(new Runnable() {
								@Override
								public void run() {
									if (!items.contains(result)) {
										items.add(result);
										adapter.notifyDataSetChanged();
									}
								}
							});
						} catch (AbstractId.InvalidHexException e) {
							Log.e(TAG, e.getMessage(), e);
						}
					}
				});
			}
			searchSocket.sendRequest(SubscriberId.broadcastSid, last_number);
			if (--search_count<=0)
				return;
			handler.postDelayed(searcher, 1000);
		}catch (IOException e){
			Log.e(TAG, e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		} catch (ServalDInterfaceException e) {
			Log.e(TAG, e.getMessage(), e);
			app.displayToastMessage(e.getMessage());
		}
	}

	private void searchMesh(boolean onResume) {
		final String phone = phone_number.getText().toString();
		if (onResume && (phone==null || phone.equals("")))
			return;

		if (!phone.equals(last_number)){
			last_number = phone;
			items.clear();
			adapter.notifyDataSetChanged();
			closeSocket();
		}
		search_count=5;
		search();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		try {
			CallHandler.dial(adapter.getItem(position));
			closeNow();
		} catch (Exception e) {
			app.displayToastMessage(e
					.getMessage());
			Log.e(TAG, e.getMessage(), e);
		}
	}

	@Override
	public void peerChanged(final Peer p) {
		if (!app.isMainThread()){
			handler.post(new Runnable() {
				@Override
				public void run() {
					peerChanged(p);
				}
			});
			return;
		}
		adapter.notifyDataSetChanged();
	}
}
