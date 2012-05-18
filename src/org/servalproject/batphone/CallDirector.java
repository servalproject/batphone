package org.servalproject.batphone;

import org.servalproject.R;
import org.servalproject.account.AccountService;
import org.servalproject.servald.LookupResults;
import org.servalproject.servald.Peer;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.SubscriberId;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class CallDirector extends ListActivity {

	String dialed_number;
	ArrayAdapter<Peer> adapter;
	private boolean searching = false;
	Button cancel;
	Button search;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setContentView(R.layout.call_director);
		Intent intent = this.getIntent();
		String action = intent.getAction();
		if (Intent.ACTION_VIEW.equals(action)) {
			// Call Director has been triggered from clicking on a SID in contacts.
			// Thus we can bypass the entire selection process, and trigger call by
			// mesh.
			try {
				SubscriberId sid = null;
				Cursor cursor = getContentResolver().query(intent.getData(),
						new String[] {
							ContactsContract.Data.DATA1
						},
						ContactsContract.Data.MIMETYPE + " = ?",
						new String[] {
							AccountService.SID_FIELD_MIMETYPE
						},
						null);
				try {
					if (cursor.moveToNext())
						sid = new SubscriberId(cursor.getString(0));
				} finally {
					cursor.close();
				}

				// TODO get name and number from contact as well.
				BatPhone.callBySid(sid);
				finish();
				return;
			} catch (Exception e) {
				// Blasted exceptions from poorly formatted integers.
			}

		}

		dialed_number = intent.getStringExtra("phone_number");

		adapter = new ArrayAdapter<Peer>(this,
				android.R.layout.simple_list_item_1);
		setListAdapter(adapter);

		TextView call = (TextView) this.findViewById(R.id.call);
		call.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				BatPhone.call(dialed_number);
				finish();
			}
		});

		cancel = (Button) this.findViewById(R.id.cancel);
		cancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

		search = (Button) this.findViewById(R.id.search);
		search.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				searchMesh();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		searchMesh();
	}

	private void searchMesh() {
		if (searching)
			return;

		search.setEnabled(false);
		search.setText("Searching");
		searching = true;
		adapter.notifyDataSetChanged();

		new AsyncTask<String, Peer, Void>() {
			@Override
			protected void onProgressUpdate(Peer... values) {
				if (adapter.getPosition(values[0]) < 0)
					adapter.add(values[0]);
			}

			@Override
			protected void onPostExecute(Void result) {
				search.setEnabled(true);
				search.setText("Search");
				searching = false;
				adapter.notifyDataSetChanged();

				// TODO Auto-generated method stub
				super.onPostExecute(result);
			}

			@Override
			protected Void doInBackground(String... params) {
				ServalD.dnaLookup(new LookupResults() {
					@Override
					public void result(Peer result) {
						publishProgress(result);
					}
				}, params[0]);
				return null;
			}

		}.execute(dialed_number);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		BatPhone.callBySid(adapter.getItem(position).sid);
		finish();
	}

}
