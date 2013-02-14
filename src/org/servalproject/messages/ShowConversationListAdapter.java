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
import org.servalproject.provider.MessagesContract;
import org.servalproject.servald.Identity;
import org.servalproject.servald.SubscriberId;

import android.database.Cursor;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class ShowConversationListAdapter extends SimpleCursorAdapter {

	/*
	 * private class level constants
	 */
	private final String TAG = "ShowConversationListAdapter";

	/*
	 * private class level variables
	 */

	private SubscriberId selfIdentity;

	private LayoutInflater layoutInflater;

	private Time t;

	/**
	 * constructor for the class
	 *
	 * @param context
	 * @param layout
	 * @param c
	 * @param from
	 * @param to
	 */
	public ShowConversationListAdapter(ShowConversationActivity context,
			int layout, Cursor c,
			String[] from, int[] to) {
		super(context, layout, c, from, to);

		layoutInflater = LayoutInflater.from(context);

		this.selfIdentity = Identity.getMainIdentity().subscriberId;

		// get current date
		t = new Time();
		t.setToNow();

	}

	@Override
	public int getItemViewType(int position) {
		Cursor cursor = getCursor();
		if (!cursor.moveToPosition(position))
			return IGNORE_ITEM_VIEW_TYPE;

		// check to see if this is a sent or received message
		String senderSid = cursor.getString(cursor
				.getColumnIndex(MessagesContract.Table.SENDER_PHONE));

		try {
			if (senderSid != null
					&& selfIdentity.equals(new SubscriberId(senderSid)))
				return 0;
			return 1;
		} catch (Exception e) {
			Log.e(TAG, e.toString(), e);
		}
		return IGNORE_ITEM_VIEW_TYPE;
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// re-fetch the underlying cursor as it could have changed
		Cursor cursor = getCursor();
		if (cursor.moveToPosition(position)) {
			MessageHolder viewHolder;
			TextView messageText = null;
			TextView timeText = null;

			if (convertView == null) {

				// check to see if this is a sent or received message
				String senderSid = cursor.getString(cursor
						.getColumnIndex(MessagesContract.Table.SENDER_PHONE));

				try {
					if (senderSid != null
							&& selfIdentity.equals(new SubscriberId(senderSid))) {
						convertView = layoutInflater.inflate(
								R.layout.show_conversation_item_us, parent,
								false);
						messageText = (TextView) convertView
								.findViewById(R.id.show_conversation_item_content_us);
						timeText = (TextView) convertView
								.findViewById(R.id.show_conversation_item_time_us);
					}
				} catch (SubscriberId.InvalidHexException e) {
					// If sender SID column is malformed, treat it as a received
					// message.
					convertView = null;
				}

				if (convertView == null) {
					convertView = layoutInflater
							.inflate(R.layout.show_conversation_item_them,
									parent, false);
					messageText = (TextView) convertView
							.findViewById(R.id.show_conversation_item_content_them);
					timeText = (TextView) convertView
							.findViewById(R.id.show_conversation_item_time_them);
				}

				viewHolder = new MessageHolder();
				viewHolder.messageView = messageText;
				viewHolder.timeView = timeText;
				convertView.setTag(viewHolder);

			}
			else {
				viewHolder = (MessageHolder) convertView.getTag();
			}

			// get the message text
			String msg = cursor.getString(cursor
					.getColumnIndex(MessagesContract.Table.MESSAGE));

			// format the date and time
			String mDate = (String) DateUtils
					.formatSameDayTime(
							cursor.getLong(cursor
									.getColumnIndex(MessagesContract.Table.RECEIVED_TIME)),
							t.toMillis(false), DateFormat.MEDIUM,
							DateFormat.SHORT);

			viewHolder.messageView.setText(msg);
			viewHolder.timeView.setText(mDate);
		}

		return convertView;
	}

	private static class MessageHolder {
		public TextView messageView;
		public TextView timeView;
	}
}
