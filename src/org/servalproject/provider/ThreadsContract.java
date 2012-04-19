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
public class ThreadsContract implements BaseColumns {

	/**
	 * path component of the URI
	 */
	public static final String CONTENT_URI_PATH = "threads";

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
	 * table definition
	 */
	public static final class Table implements BaseColumns {

		/**
		 * table name
		 */
		public static final String TABLE_NAME =
				ThreadsContract.CONTENT_URI_PATH;

		/**
		 * unique id column
		 */
		public static final String _ID = BaseColumns._ID;

		/**
		 * participants phone number
		 */
		public static final String PARTICIPANT_PHONE = "phone_number";
	}
}
