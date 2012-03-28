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
package org.servalproject.provider;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * define the contract for integrating with the messaging table
 */
public class MessagesContract implements BaseColumns {

	/**
	 * path component of the URI
	 */
	public static final String CONTENT_URI_PATH = "messages";

	/**
	 * content URI for the locations data
	 */
	public static final Uri CONTENT_URI = Uri.parse("content://"
			+ MainContentProvider.AUTHORITY + "/" + CONTENT_URI_PATH);

	/**
	 * content type for a list of items
	 */
	public static final String CONTENT_TYPE_LIST =
			"vnd.android.cursor.dir/vnd.org.servalproject.provider."
			+ CONTENT_URI_PATH;

	/**
	 * content type for an individual item
	 */
	public static final String CONTENT_TYPE_ITEM =
			"vnd.android.cursor.item/vnd.org.servalproject.provider."
			+ CONTENT_URI_PATH;

	/**
	 * flag to indicate that a message is new
	 */
	public static final int IS_NEW = 1;

	/**
	 * flag to indicate that a message is not new
	 */
	public static final int IS_NOT_NEW = 0;

	/**
	 * flag to indicate that a message is read
	 */
	public static final int IS_READ = 1;

	/**
	 * flag to indicate that a message is not read
	 */
	public static final int IS_NOT_READ = 0;

	/**
	 * table definition
	 */
	public static final class Table implements BaseColumns {

		/**
		 * table name
		 */
		public static final String TABLE_NAME =
				MessagesContract.CONTENT_URI_PATH;

		/**
		 * unique id column
		 */
		public static final String _ID = BaseColumns._ID;

		/**
		 * unique id of thread
		 */
		public static final String THREAD_ID = "thread";

		/**
		 * senders phone number
		 */
		public static final String SENDER_PHONE = "phone";

		/**
		 * text of the message
		 */
		public static final String MESSAGE = "message_text";

		/**
		 * time the message was received
		 */
		public static final String RECEIVED_TIME = "received_time";

		/**
		 * time that the message was sent
		 */
		public static final String SENT_TIME = "sent_time";

		/**
		 * flag to indicate that a message has been read
		 */
		public static final String READ = "read_flag";

		/**
		 * flag to indicate that a message is new
		 */
		public static final String NEW = "new_flag";
	}

}
