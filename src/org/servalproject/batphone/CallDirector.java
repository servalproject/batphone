package org.servalproject.batphone;

import org.servalproject.account.AccountService;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalDResult;
import org.servalproject.servald.SubscriberId;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

public class CallDirector extends ListActivity {

	String dialed_number = BatPhone.getDialedNumber();
	String dids[] = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		this.setTitle("How shall I call " + BatPhone.getDialedNumber() + "?");
		super.onCreate(savedInstanceState);

		Intent intent = this.getIntent();
		StringBuilder sb = new StringBuilder();
		if (intent.getAction().equals(Intent.ACTION_VIEW)) {
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

				BatPhone.callBySid(sid);
				finish();
				return;
			} catch (Exception e) {
				// Blasted exceptions from poorly formatted integers.
			}

		}

		searchMesh(BatPhone.getDialedNumber());
	}

	private void searchMesh(String did) {
		ListAdapter adapter = createAdapter(true);
		setListAdapter(adapter);

		new AsyncTask<String, String, String[]>() {
			@Override
			protected void onPostExecute(String s[]) {
				// Runs on IO thread
				dids = s;
				ListAdapter adapter = createAdapter(false);
				setListAdapter(adapter);
			}

			@Override
			protected String[] doInBackground(String... params) {
				String did = BatPhone.getDialedNumber();
				ServalDResult results = ServalD.command("dna", "lookup", did);
				return results.outv;
			}

		}.execute();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		if (position == 0) {
			// make call by cellular/normal means
			BatPhone.ignoreCall(dialed_number);
			String url = "tel:" + dialed_number;
			Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
			startActivity(intent);
			finish();
		} else if (position == 1) {
			// cancel call
			BatPhone.cancelCall();
			finish();
		} else if (position == 2) {
			// if not currently probing the mesh, send out another probe
			Object o = super.getListAdapter().getItem(2);
			String s = o.toString();
			if (s.equals("Search on the mesh")) {
				searchMesh(BatPhone.getDialedNumber());
			}
		} else if (position > 2) {
			// Mesh call using the specified identity
		}
		super.onListItemClick(l, v, position, id);
	}

	protected ListAdapter createAdapter(boolean probingP)
	{
		// Dids contains list of SID,DID tuples, so we only use every 2nd.
		// XXX Soon we will allow storing of names, in which case we will get
		// SID,DID,Name, and have to divide by 3
		int did_count=0;
		if (dids!=null) did_count=dids.length;
		String[] values = new String[3 + did_count / 3];
		values[0]="Normal (cellular) call";
		values[1]="Cancel call";
		if (probingP) values[2]="Probing the mesh ...";
		else values[2]="Search on the mesh";
		if (dids != null)
			for (int i = 0; i < dids.length; i += 3) {
				values[3 + i / 3] = dids[i] + " '" + dids[i + 2] + "'";
			}

		// Create a simple array adapter (of type string) with the test values
		ListAdapter adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, values);

		return adapter;
	}


}
