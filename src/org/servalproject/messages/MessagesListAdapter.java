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
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servald.SubscriberId;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
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
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {

		// populate the views
		final TextView titleView = (TextView) view
				.findViewById(R.id.messages_list_item_title);
		String title = cursor.getString(cursor
				.getColumnIndex(ThreadsContract.Table.PARTICIPANT_PHONE));

		final ImageView mImageView = (ImageView) view
				.findViewById(R.id.messages_list_item_image);
		// initially use the default image, we'll update it when the peer is
		// resolved
		mImageView.setImageResource(R.drawable.ic_contact_picture_3);

		// attempt to resolve the peer
		try {
			SubscriberId sid = new SubscriberId(title);
			final Peer peer = PeerListService
					.getPeer(context.getContentResolver(), sid);

			updateDisplay(peer, titleView, mImageView);

			if (peer.cacheUntil < SystemClock.elapsedRealtime()) {
				new AsyncTask<Void, Void, Void>() {

					@Override
					protected void onPostExecute(Void result) {
						updateDisplay(peer, titleView, mImageView);
					}

					@Override
					protected Void doInBackground(Void... params) {
						PeerListService.resolve(peer);
						return null;
					}
				}.execute();
			}

		} catch (SubscriberId.InvalidHexException e) {
			Log.e("VoMPCall", "Intent contains invalid SID: " + title, e);
			return;
		}

		TextView countText = (TextView) view
				.findViewById(R.id.messages_list_item_count);
		countText.setText("(" + cursor.getString(cursor
				.getColumnIndex("COUNT_RECIPIENT_PHONE")) + ")");

		// get current date
		Time t = new Time();
		t.setToNow();

		// format the date and time
		String mDate = (String) DateUtils.formatSameDayTime(
				cursor.getLong(cursor
						.getColumnIndex("MAX_RECEIVED_TIME")),
				t.toMillis(false), DateFormat.MEDIUM, DateFormat.SHORT);

		TextView timeView = (TextView) view
				.findViewById(R.id.messages_list_item_time);
		timeView.setText(mDate);

	}

	/**
	 * Update the name and contact image views after the peer has been resolved.
	 *
	 * @param peer
	 * @param titleView
	 * @param mImageView
	 */
	private void updateDisplay(Peer peer, TextView titleView,
			ImageView mImageView) {

		titleView.setText(peer.toString());

		// if a contact record exists, get the photo associated with it
		// if there is one
		if (peer.contactId != -1) {

			Bitmap mPhoto = MessageUtils.loadContactPhoto(context,
					peer.contactId);

			// use photo if found else use default image
			if (mPhoto != null) {
				mImageView.setImageBitmap(mPhoto);
			} else {
				mImageView.setImageResource(R.drawable.ic_contact_picture);
			}

		}
	}

}
