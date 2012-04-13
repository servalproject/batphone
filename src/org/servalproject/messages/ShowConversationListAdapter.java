package org.servalproject.messages;

import org.servalproject.R;
import org.servalproject.dna.DataFile;
import org.servalproject.provider.MessagesContract;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
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
	// private int layout;

	private String selfPhoneNumber;

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

		this.context = context;
		// this.layout = layout;

		this.selfPhoneNumber = DataFile.getDid(0);
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {

		TextView mTextView;

		ImageView mImageView = (ImageView) view
				.findViewById(R.id.show_conversation_item_image);

		// check to see if this is a sent or received message
		if (selfPhoneNumber.equals(cursor.getString(cursor
				.getColumnIndex(MessagesContract.Table.SENDER_PHONE))) == true) {

			// this is a message sent by the user
			mTextView = (TextView) view
					.findViewById(R.id.show_conversation_item_title);
			mTextView.setText(selfPhoneNumber);

			populatePhoto(mImageView, cursor, selfPhoneNumber);

		} else {

			// this is a message received by the user
			mTextView = (TextView) view
					.findViewById(R.id.show_conversation_item_title);
			mTextView.setText(cursor.getString(cursor
					.getColumnIndex(MessagesContract.Table.RECIPIENT_PHONE)));

			populatePhoto(mImageView, cursor, cursor.getString(cursor
					.getColumnIndex(MessagesContract.Table.RECIPIENT_PHONE)));
		}

		int mFlags = 0;
		mFlags |= DateUtils.FORMAT_SHOW_DATE;
		mFlags |= DateUtils.FORMAT_ABBREV_MONTH;

		// format the date and time
		// TODO handle sent times when available
		String mDate = DateUtils.formatDateTime(
				context,
				cursor.getLong(cursor
						.getColumnIndex(MessagesContract.Table.RECEIVED_TIME)),
				mFlags);

		mTextView = (TextView) view
				.findViewById(R.id.show_conversation_item_time);
		mTextView.setText(mDate);

		// add the content of the message
		mTextView = (TextView) view
				.findViewById(R.id.show_conversation_item_content);
		mTextView.setText(cursor.getString(cursor
				.getColumnIndex(MessagesContract.Table.MESSAGE)));
	}

	private void populatePhoto(ImageView imageView, Cursor cursor, String lookup) {

		// see if this phone number has a contact record associated with it
		long mContactId = MessageUtils.lookupPhotoId(context, lookup);

		// if a contact record exists, get the photo associated with it
		// if there is one
		if (mContactId != -1) {

			Bitmap mPhoto = MessageUtils.loadContactPhoto(context, mContactId);

			// use photo if found else use default image
			if (mPhoto != null) {
				imageView.setImageBitmap(mPhoto);
			} else {
				imageView.setImageResource(R.drawable.ic_contact_picture);
			}

		} else {
			// use the default image
			imageView.setImageResource(R.drawable.ic_contact_picture_3);
		}
	}
}
