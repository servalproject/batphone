package org.servalproject.messages;

import java.text.DateFormat;

import org.servalproject.R;
import org.servalproject.provider.MessagesContract;
import org.servalproject.servald.Identities;
import org.servalproject.servald.SubscriberId;

import android.content.Context;
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
	private final String TAG = "MessagesListAdapter";

	/*
	 * private class level variables
	 */
	private Context context;

	private SubscriberId selfIdentity;

	private LayoutInflater layoutInflater;

	private int layout;

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
	public ShowConversationListAdapter(Context context, int layout, Cursor c,
			String[] from, int[] to) {
		super(context, layout, c, from, to);

		layoutInflater = LayoutInflater.from(context);

		this.context = context;
		this.layout = layout;

		this.selfIdentity = Identities.getCurrentIdentity();

		// get current date
		t = new Time();
		t.setToNow();

	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {

		for (String s : cursor.getColumnNames()) {
			Log.i(TAG, "column name " + s);
		}

		View view;
		TextView messageText;
		TextView timeText;
		// check to see if this is a sent or received message
		String senderSid = cursor.getString(cursor
				.getColumnIndex(MessagesContract.Table.SENDER_PHONE));
		if (senderSid != null
				&& selfIdentity.equals(new SubscriberId(senderSid))) {
			view = layoutInflater.inflate(R.layout.show_conversation_item_us, parent, false);
			messageText = (TextView) view
					.findViewById(R.id.show_conversation_item_content_us);
			timeText = (TextView) view
					.findViewById(R.id.show_conversation_item_time_us);
		} else {
			view = layoutInflater.inflate(R.layout.show_conversation_item_them, parent, false);
			messageText = (TextView) view
					.findViewById(R.id.show_conversation_item_content_them);
			timeText = (TextView) view
					.findViewById(R.id.show_conversation_item_time_them);
		}

		// get the message text
		messageText.setText(cursor.getString(cursor
				.getColumnIndex(MessagesContract.Table.MESSAGE)));

		// format the date and time
		String mDate = (String) DateUtils.formatSameDayTime(
				cursor.getLong(cursor
						.getColumnIndex(MessagesContract.Table.RECEIVED_TIME)),
				t.toMillis(false), DateFormat.MEDIUM, DateFormat.SHORT);
		timeText.setText(mDate);

		MessageHolder holder = new MessageHolder();
		holder.messageView = messageText;
		holder.timeView = timeText;
		view.setTag(holder);

		return view;
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {

		MessageHolder holder = (MessageHolder) view.getTag();

		// TextView mTextView;
		//
		// // check to see if this is a sent or received message
		// if (selfPhoneNumber.equals(cursor.getString(cursor
		// .getColumnIndex(MessagesContract.Table.SENDER_PHONE))) == true) {
		//
		// row = inflater.inflate(R.layout.show_conversation_item, parent,
		// false);
		//
		// // this is a message sent by the user
		// mTextView = (TextView) view
		// .findViewById(R.id.show_conversation_item_title);
		// mTextView.setText(selfPhoneNumber);
		//
		// populatePhoto(mImageView, cursor, selfPhoneNumber);
		//
		// } else {
		//
		// // this is a message received by the user
		// mTextView = (TextView) view
		// .findViewById(R.id.show_conversation_item_title);
		// mTextView.setText(cursor.getString(cursor
		// .getColumnIndex(MessagesContract.Table.RECIPIENT_PHONE)));
		//
		// populatePhoto(mImageView, cursor, cursor.getString(cursor
		// .getColumnIndex(MessagesContract.Table.RECIPIENT_PHONE)));
		// }
		//
		// int mFlags = 0;
		// mFlags |= DateUtils.FORMAT_SHOW_DATE;
		// mFlags |= DateUtils.FORMAT_ABBREV_MONTH;
		//
		// // format the date and time
		// // TODO handle sent times when available
		// String mDate = DateUtils.formatDateTime(
		// context,
		// cursor.getLong(cursor
		// .getColumnIndex(MessagesContract.Table.RECEIVED_TIME)),
		// mFlags);
		//
		// mTextView = (TextView) view
		// .findViewById(R.id.show_conversation_item_time);
		// mTextView.setText(mDate);
		//
		// // add the content of the message
		// mTextView = (TextView) view
		// .findViewById(R.id.show_conversation_item_content);
		// mTextView.setText(cursor.getString(cursor
		// .getColumnIndex(MessagesContract.Table.MESSAGE)));
	}

	private static class MessageHolder {
		TextView messageView;
		TextView timeView;
	}
}
