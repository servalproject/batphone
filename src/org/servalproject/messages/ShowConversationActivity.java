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

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

/**
 * activity to show a conversation thread
 *
 */
public class ShowConversationActivity extends ListActivity implements
		OnItemClickListener {

	/*
	 * private class level constants
	 */
	private final boolean V_LOG = true;
	private final String TAG = "ShowConversationActivity";

	/*
	 * private class level variables
	 */
	private Cursor cursor;
	private int threadId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		if (V_LOG) {
			Log.v(TAG, "on create called");
		}

		// get the thread id from the intent
		Intent mIntent = getIntent();
		threadId = mIntent.getIntExtra("threadId", -1);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.show_conversation);

		// get a reference to the list view
		ListView mListView = getListView();
		mListView.setOnItemClickListener(this);
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

		Uri mUri = MessagesContract.CONTENT_URI;

		String mSelection = MessagesContract.Table.THREAD_ID + " = ?";
		String[] mSelectionArgs = new String[1];
		mSelectionArgs[0] = Integer.toString(threadId);

		String mOrderBy = MessagesContract.Table.RECEIVED_TIME + " DESC";

		cursor = mContentResolver.query(
				mUri,
				null,
				mSelection,
				mSelectionArgs,
				mOrderBy);

		// zero length arrays required by list adapter constructor,
		// manual matching to views & columns will occur in the bindView method
		String[] mColumnNames = new String[0];
		int[] mLayoutElements = new int[0];

		ShowConversationListAdapter mDataAdapter = new ShowConversationListAdapter(
				this,
				R.layout.show_conversation_item,
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
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		// TODO Auto-generated method stub

	}


}
