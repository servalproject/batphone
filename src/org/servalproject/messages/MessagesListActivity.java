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

import org.servalproject.R;
import org.servalproject.provider.MessagesContract;
import org.servalproject.provider.ThreadsContract;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
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

	/*
	 * private class level variables
	 */
	private Cursor cursor;

	@Override
    protected void onCreate(Bundle savedInstanceState) {

		if (V_LOG) {
			Log.v(TAG, "on create called");
		}

        super.onCreate(savedInstanceState);
		setContentView(R.layout.messages_list);

		// get a reference to the list view
		ListView mListView = getListView();
		mListView.setOnItemClickListener(this);

		Button mButton = (Button) findViewById(R.id.messages_list_ui_btn_new);
		mButton.setOnClickListener(this);
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

			if (V_LOG) {
				Log.v(TAG, "item in cursor has id: " + mThreadId);
			}

			Intent mIntent = new Intent(this,
					org.servalproject.messages.ShowConversationActivity.class);
			mIntent.putExtra("threadId", mThreadId);
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
			// show the new message activity
			Intent mIntent = new Intent(this,
					org.servalproject.messages.NewMessageActivity.class);
			startActivity(mIntent);
			break;
		default:
			Log.w(TAG, "onClick called by an unrecognised view");
		}

	}
}
