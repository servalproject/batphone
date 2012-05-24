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
package org.servalproject.messages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.servalproject.IPeerListListener;
import org.servalproject.IPeerListMonitor;
import org.servalproject.R;
import org.servalproject.meshms.IncomingMeshMS;
import org.servalproject.meshms.SimpleMeshMS;
import org.servalproject.provider.MessagesContract;
import org.servalproject.provider.ThreadsContract;
import org.servalproject.servald.Identities;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.SubscriberId;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * main activity to display the list of messages
 */
public class MessagesListActivity extends ListActivity implements
		OnItemClickListener, OnClickListener {

	/*
	 * private class level constants
	 */
	private final boolean V_LOG = true;
	private final String TAG = "MessagesListActivity";

	// codes to determine which dialog to show
	private final int DIALOG_RECIPIENT_EMPTY = 0;
	private final int DIALOG_RECIPIENT_INVALID = 1;
	private final int DIALOG_CONTENT_EMPTY = 2;

	/*
	 * private class level variables
	 */

	private InputMethodManager imm;

	private Cursor cursor;

	private AutoCompleteTextView actv;

	private Adapter listAdapter;

	private Peer recipient;

	@Override
    protected void onCreate(Bundle savedInstanceState) {

		if (V_LOG) {
			Log.v(TAG, "on create called");
		}

        super.onCreate(savedInstanceState);
		setContentView(R.layout.messages_list);

		imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		bindService(new Intent(this, PeerListService.class), svcConn,
				BIND_AUTO_CREATE);

		listAdapter = new Adapter(this);
		listAdapter.setNotifyOnChange(false);

		actv = (AutoCompleteTextView) findViewById(R.id.new_message_ui_txt_recipient);
		actv.setAdapter(listAdapter);
		actv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position,
					long id) {
				recipient = listAdapter.getItem(position);
			}
		});

		// get a reference to the list view
		ListView mListView = getListView();
		mListView.setOnItemClickListener(this);

		Button mButton = (Button) findViewById(R.id.messages_list_ui_btn_new);
		mButton.setOnClickListener(this);

		mButton = (Button) findViewById(R.id.test);
		mButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					List<SimpleMeshMS> messages = new ArrayList<SimpleMeshMS>();
					messages.add(new SimpleMeshMS(SubscriberId.randomSid(),
							Identities.getCurrentIdentity(), Integer
									.toString((int) (Math.random() * 100000)),
							Identities.getCurrentDid(), System
									.currentTimeMillis(),
							"Test message"));
					IncomingMeshMS.addMessages(messages);
				} catch (Exception e) {
					Log.e("BatPhone", e.getMessage(), e);
				}
			}
		});
	}

	/*
	 * get the required data and populate the cursor
	 */
	private Cursor populateList() {

		if (V_LOG) {
			Log.v(TAG, "get cursor called");
		}

		// get a content resolver
		ContentResolver mContentResolver = getApplicationContext()
				.getContentResolver();

		Uri mGroupedUri = MessagesContract.CONTENT_URI;
		Uri.Builder mUriBuilder = mGroupedUri.buildUpon();
		mUriBuilder.appendPath("grouped-list");
		mGroupedUri = mUriBuilder.build();

		cursor = mContentResolver.query(
				mGroupedUri,
				null,
				null,
				null,
				null);

		// define the map between columns and layout elements
		String[] mColumnNames = new String[3];
		mColumnNames[0] = ThreadsContract.Table.PARTICIPANT_PHONE;
		mColumnNames[1] = "COUNT_RECIPIENT_PHONE";
		mColumnNames[2] = "MAX_RECEIVED_TIME";

		int[] mLayoutElements = new int[3];
		mLayoutElements[0] = R.id.messages_list_item_title;
		mLayoutElements[1] = R.id.messages_list_item_count;
		mLayoutElements[2] = R.id.messages_list_item_time;

		MessagesListAdapter mDataAdapter = new MessagesListAdapter(
				this,
				R.layout.messages_list_item,
				cursor,
				mColumnNames,
				mLayoutElements);

		setListAdapter(mDataAdapter);

		return cursor;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {

		if (V_LOG) {
			Log.v(TAG, "on pause called");
		}

		// play nice and close the cursor
		if (cursor != null) {
			cursor.close();
			cursor = null;
		}

		// unbind service
		service.removeListener(listener);
		unbindService(svcConn);

		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onResume()
	 */
	@Override
	public void onResume() {

		if (V_LOG) {
			Log.v(TAG, "on resume called");
		}

		// get the data
		if (cursor != null) {
			cursor.close();
			cursor = null;
		}

		bindService(new Intent(this, PeerListService.class), svcConn,
				BIND_AUTO_CREATE);

		cursor = populateList();
		super.onResume();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.ListActivity#onDestroy()
	 */
	@Override
	public void onDestroy() {

		if (V_LOG) {
			Log.v(TAG, "on destroy called");
		}

		// play nice and close the cursor if necessary
		if (cursor != null) {
			cursor.close();
			cursor = null;
		}

		super.onDestroy();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {

		if (V_LOG) {
			Log.v(TAG, "item clicked at position: " + position);
		}

		// work out the id of the item
		if (cursor.moveToPosition(position) == true) {

			int mThreadId = cursor.getInt(
					cursor.getColumnIndex(ThreadsContract.Table._ID));
			String otherParty = cursor.getString(cursor
					.getColumnIndex(ThreadsContract.Table.PARTICIPANT_PHONE));

			if (V_LOG) {
				Log.v(TAG, "item in cursor has id: " + mThreadId);
			}

			Intent mIntent = new Intent(this,
					org.servalproject.messages.ShowConversationActivity.class);
			mIntent.putExtra("threadId", mThreadId);
			mIntent.putExtra("recipient", otherParty);
			startActivity(mIntent);

		} else {

			Log.e(TAG, "unable to match list item position to poi id");
			Toast.makeText(getApplicationContext(),
					R.string.messages_list_ui_toast_missing_id,
					Toast.LENGTH_LONG).show();

		}

	}

	@Override
	public void onClick(View v) {

		switch (v.getId()) {
		case R.id.messages_list_ui_btn_new:
			if (recipient == null || recipient.sid == null) {
				showDialog(DIALOG_RECIPIENT_EMPTY);
				return;
			}
			actv.setText("");
			actv.dismissDropDown();
			imm.hideSoftInputFromWindow(actv.getWindowToken(), 0);
			// show the show conversation activity
			Intent mIntent = new Intent(this,
					org.servalproject.messages.ShowConversationActivity.class);
			mIntent.putExtra("recipient", recipient.sid.toString());
			startActivity(mIntent);
			break;
		default:
			Log.w(TAG, "onClick called by an unrecognised view");
		}

	}

	/*
	 * callback method used to construct the required dialog (non-Javadoc)
	 *
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {

		// create the required alert dialog
		AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
		AlertDialog mDialog = null;

		switch (id) {
		case DIALOG_RECIPIENT_EMPTY:
			mBuilder.setMessage(R.string.new_message_ui_dialog_recipient_empty)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			mDialog = mBuilder.create();
			break;

		case DIALOG_RECIPIENT_INVALID:
			mBuilder.setMessage(
					R.string.new_message_ui_dialog_recipient_invalid)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			mDialog = mBuilder.create();
			break;

		case DIALOG_CONTENT_EMPTY:
			mBuilder.setMessage(R.string.new_message_ui_dialog_content_empty)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			mDialog = mBuilder.create();

			break;
		default:
			mDialog = null;
		}

		return mDialog;
	}

	private IPeerListListener listener = new IPeerListListener() {
		@Override
		public void peerChanged(final Peer p) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (listAdapter.getPosition(p) < 0) {
						// new peer so add it to the list
						listAdapter.add(p);
						listAdapter.sort(new Comparator<Peer>() {
							@Override
							public int compare(Peer r1, Peer r2) {
								return r1.getSortString().compareTo(
										r2.getSortString());
							}
						});
						listAdapter.notifyDataSetChanged();
					}
				}
			});
		}
	};

	private IPeerListMonitor service = null;
	private ServiceConnection svcConn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className,
				IBinder binder) {
			Log.i(TAG, "service created");
			service = (IPeerListMonitor) binder;
			service.registerListener(listener);
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			Log.i(TAG, "service disconnected");
			service = null;
		}
	};

	class Adapter extends ArrayAdapter<Peer> {
		public Adapter(Context context) {
			super(context, R.layout.message_recipient,
					R.id.recipient_number);
		}

		@Override
		public View getView(final int position, View convertView,
				ViewGroup parent) {
			View ret = super.getView(position, convertView, parent);

			Peer r = listAdapter.getItem(position);

			TextView displayName = (TextView) ret
					.findViewById(R.id.recipient_name);
			displayName.setText(r.getContactName());

			TextView displaySid = (TextView) ret
					.findViewById(R.id.recipient_number);
			displaySid.setText(r.did);

			ImageView type = (ImageView) ret.findViewById(R.id.recipient_type);
			type.setBackgroundResource(R.drawable.ic_24_serval);

			return ret;
		}

	}
}
