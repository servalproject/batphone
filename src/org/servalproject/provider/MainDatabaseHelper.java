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

import java.io.File;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * main database helper class to be used by the MainContentProvider
 */
public class MainDatabaseHelper extends SQLiteOpenHelper {

	/*
	 * public class level constants
	 */
	public static final String DB_NAME = "serval-mesh.db";
	public static final int DB_VERSION = 1;

	/*
	 * private class level constants
	 */
	private static final String THREAD_CREATE = "CREATE TABLE "
			+ ThreadsContract.Table.TABLE_NAME + " ("
			+ ThreadsContract.Table._ID + " INTEGER PRIMARY KEY, "
			+ ThreadsContract.Table.PARTICIPANT_PHONE + " TEXT)";

	private static final String MESSAGES_CREATE = "CREATE TABLE "
			+ MessagesContract.Table.TABLE_NAME + " ("
			+ MessagesContract.Table._ID + " INTEGER PRIMARY KEY, "
			+ MessagesContract.Table.THREAD_ID + " INTEGER, "
			+ MessagesContract.Table.RECIPIENT_PHONE + " TEXT, "
			+ MessagesContract.Table.SENDER_PHONE + " TEXT, "
			+ MessagesContract.Table.MESSAGE + " TEXT, "
			+ MessagesContract.Table.RECEIVED_TIME + " INTEGER, "
			+ MessagesContract.Table.SENT_TIME + " INTEGER, "
			+ MessagesContract.Table.READ + " INTEGER DEFAULT "
			+ MessagesContract.IS_NOT_READ + ", "
			+ MessagesContract.Table.NEW + " INTEGER DEFAULT "
			+ MessagesContract.IS_NEW + ") ";

	/**
	 * Constructs a new MainDatabaseHelper object
	 *
	 * @param context
	 *            in which the database should be used
	 * @param path
	 *            the path to be used as part of the database name
	 */
	MainDatabaseHelper(Context context, File path) {

		// context, database name, factory, db version
		super(context, new File(path, DB_NAME).getPath(), null, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase database) {

		// create the table as required
		database.execSQL(THREAD_CREATE);
		database.execSQL(MESSAGES_CREATE);

	}

	@Override
	public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub

	}

}
