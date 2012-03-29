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

import android.content.Context;
import android.database.Cursor;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class MessagesListAdapter extends SimpleCursorAdapter {

	/*
	 * private class level variables
	 */
	private Context context;
	private int layout;

	/**
	 * constructor for the class
	 *
	 * @param context
	 * @param layout
	 * @param c
	 * @param from
	 * @param to
	 */
	public MessagesListAdapter(Context context, int layout, Cursor c,
			String[] from, int[] to) {
		super(context, layout, c, from, to);

		this.context = context;
		this.layout = layout;
	}

	// @Override
	// public View newView(Context context, Cursor cursor, ViewGroup parent) {
	//
	// // inflate the view
	// final LayoutInflater mInflater = LayoutInflater.from(context);
	// View mView = mInflater.inflate(layout, parent, false);
	//
	// return populateView(mView, context, cursor);
	// }

	@Override
	public void bindView(View v, Context context, Cursor c) {

		populateView(v, context, c);

	}

	private View populateView(View view, Context context, Cursor cursor) {

		// populate the views
		TextView mTextView = (TextView) view
				.findViewById(R.id.messages_list_item_title);
		mTextView.setText(cursor.getString(cursor
				.getColumnIndex(MessagesContract.Table.RECIPIENT_PHONE)));

		mTextView = (TextView) view
				.findViewById(R.id.messages_list_item_count);
		mTextView.setText(cursor.getString(cursor
				.getColumnIndex("COUNT_RECIPIENT_PHONE")));

		int mFlags = 0;
		mFlags |= DateUtils.FORMAT_SHOW_DATE;
		mFlags |= DateUtils.FORMAT_ABBREV_MONTH;

		// format the date and time
		String mDate = DateUtils.formatDateTime(
				context,
				cursor.getLong(cursor
						.getColumnIndex("MAX_RECEIVED_TIME")),
				mFlags);

		mTextView = (TextView) view.findViewById(R.id.messages_list_item_time);
		mTextView.setText(mDate);

		return view;
	}

}
