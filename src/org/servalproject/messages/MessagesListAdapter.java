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

import java.text.DateFormat;

import org.servalproject.R;
import org.servalproject.provider.ThreadsContract;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.View;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class MessagesListAdapter extends SimpleCursorAdapter {

	/*
	 * private class level constants
	 */
	private final String TAG = "MessagesListAdapter";

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

	@Override
	public void bindView(View view, Context context, Cursor cursor) {

		int titleLength = 27;

		// populate the views
		TextView mTextView = (TextView) view
				.findViewById(R.id.messages_list_item_title);
		String title = cursor.getString(cursor
				.getColumnIndex(ThreadsContract.Table.PARTICIPANT_PHONE));
		if (title.length() > titleLength) {
			title = title.substring(0, titleLength - 1);
		}
		mTextView.setText(title);

		mTextView = (TextView) view
				.findViewById(R.id.messages_list_item_count);
		mTextView.setText("(" + cursor.getString(cursor
				.getColumnIndex("COUNT_RECIPIENT_PHONE")) + ")");

		// get current date
		Time t = new Time();
		t.setToNow();

		// format the date and time
		String mDate = (String) DateUtils.formatSameDayTime(
				cursor.getLong(cursor
						.getColumnIndex("MAX_RECEIVED_TIME")),
				t.toMillis(false), DateFormat.MEDIUM, DateFormat.SHORT);

		mTextView = (TextView) view.findViewById(R.id.messages_list_item_time);
		mTextView.setText(mDate);

		ImageView mImageView = (ImageView) view
				.findViewById(R.id.messages_list_item_image);

		// see if this phone number has a contact record associated with it
		long mContactId = MessageUtils.lookupPhotoId(context,
				cursor.getString(
						cursor.getColumnIndex(
								ThreadsContract.Table.PARTICIPANT_PHONE)
						)
				);

		// if a contact record exists, get the photo associated with it
		// if there is one
		if (mContactId != -1) {

			Bitmap mPhoto = MessageUtils.loadContactPhoto(context, mContactId);

			// use photo if found else use default image
			if (mPhoto != null) {
				mImageView.setImageBitmap(mPhoto);
			} else {
				mImageView.setImageResource(R.drawable.ic_contact_picture);
			}

		} else {
			// use the default image
			mImageView.setImageResource(R.drawable.ic_contact_picture_3);
		}
	}



}
