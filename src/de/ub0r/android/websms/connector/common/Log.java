/*
 * Copyright (C) 2010 Felix Bechstein
 * 
 * This file is part of WebSMS.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.android.websms.connector.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

/**
 * @author flx
 */
public final class Log {
	/** Tag for output. */
	public static final String TAG = "WebSMS";

	/** Packagename of SendLog. */
	private static final String SENDLOG_PACKAGE_NAME = "org.l6n.sendlog";

	/** Classname of SendLog. */
	// private static final String SENDLOG_CLASS_NAME = ".SendLog";

	/** Priority constant for the println method. */
	public static final int ASSERT = android.util.Log.ASSERT;
	/** Priority constant for the println method; use Log.d. */
	public static final int DEBUG = android.util.Log.DEBUG;
	/** Priority constant for the println method; use Log.e. */
	public static final int ERROR = android.util.Log.ERROR;
	/** Priority constant for the println method; use Log.i. */
	public static final int INFO = android.util.Log.INFO;
	/** Priority constant for the println method; use Log.v. */
	public static final int VERBOSE = android.util.Log.VERBOSE;
	/** Priority constant for the println method; use Log.w. */
	public static final int WARN = android.util.Log.WARN;

	/**
	 * Fire a given {@link Intent}.
	 * 
	 * @author flx
	 */
	private static class FireIntent implements DialogInterface.OnClickListener {
		/** {@link Activity}. */
		private final Activity a;
		/** {@link Intent}. */
		private final Intent i;

		/**
		 * Default Constructor.
		 * 
		 * @param activity
		 *            {@link Activity}
		 * @param intent
		 *            {@link Intent}
		 */
		public FireIntent(final Activity activity, final Intent intent) {
			this.a = activity;
			this.i = intent;
		}

		/**
		 * {@inheritDoc}
		 */
		public void onClick(final DialogInterface dialog, // .
				final int whichButton) {
			this.a.startActivity(this.i);
		}
	}

	/**
	 * Default Constructor.
	 */
	private Log() {

	}

	/**
	 * Send a DEBUG log message.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 */
	public static void d(final String tag, final String msg) {
		android.util.Log.d(TAG, tag + ": " + msg);
	}

	/**
	 * Send a DEBUG log message and log the exception.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 * @param tr
	 *            An exception to log.
	 */
	public static void d(final String tag, final String msg, // .
			final Throwable tr) {
		android.util.Log.d(TAG, tag + ": " + msg, tr);
	}

	/**
	 * Send a DEBUG log message.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 * @param array
	 *            array to print
	 */
	public static void d(final String tag, final String msg,
			final String[] array) {
		if (array == null) {
			d(tag, msg);
		}
		final StringBuilder buf = new StringBuilder();
		final int l = array.length;
		for (int i = 0; i < l; i++) {
			buf.append(array[i]);
			if (i < l - 1) {
				buf.append("; ");
			}
		}
		android.util.Log.d(TAG, tag + ": " + msg + buf.toString());
	}

	/**
	 * Send a ERROR log message.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 */
	public static void e(final String tag, final String msg) {
		android.util.Log.e(TAG, tag + ": " + msg);
	}

	/**
	 * Send a ERROR log message and log the exception.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 * @param tr
	 *            An exception to log.
	 */
	public static void e(final String tag, final String msg, // .
			final Throwable tr) {
		android.util.Log.e(TAG, tag + ": " + msg, tr);
	}

	/**
	 * Send a INFO log message.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 */
	public static void i(final String tag, final String msg) {
		android.util.Log.i(TAG, tag + ": " + msg);
	}

	/**
	 * Send a INFO log message and log the exception.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 * @param tr
	 *            An exception to log.
	 */
	public static void i(final String tag, final String msg, // .
			final Throwable tr) {
		android.util.Log.i(TAG, tag + ": " + msg, tr);
	}

	/**
	 * Send a VERBOSE log message.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 */
	public static void v(final String tag, final String msg) {
		android.util.Log.v(TAG, tag + ": " + msg);
	}

	/**
	 * Send a VERBOSE log message and log the exception.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 * @param tr
	 *            An exception to log.
	 */
	public static void v(final String tag, final String msg, // .
			final Throwable tr) {
		android.util.Log.v(TAG, tag + ": " + msg, tr);
	}

	/**
	 * Send a WARN log message.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 */
	public static void w(final String tag, final String msg) {
		android.util.Log.w(TAG, tag + ": " + msg);
	}

	/**
	 * Send a WARN log message and log the exception.
	 * 
	 * @param tag
	 *            Used to identify the source of a log message. It usually
	 *            identifies the class or activity where the log call occurs.
	 * @param msg
	 *            The message you would like logged.
	 * @param tr
	 *            An exception to log.
	 */
	public static void w(final String tag, final String msg, // .
			final Throwable tr) {
		android.util.Log.w(TAG, tag + ": " + msg, tr);
	}

	/**
	 * Collect and send Log.
	 * 
	 * @param activity
	 *            {@link Activity}.
	 */
	public static void collectAndSendLog(final Activity activity) {
		final PackageManager packageManager = activity.getPackageManager();
		Intent intent = packageManager
				.getLaunchIntentForPackage(SENDLOG_PACKAGE_NAME);
		final String pkg = activity.getPackageName();
		int title, message;
		if (intent == null) {
			intent = new Intent(Intent.ACTION_VIEW, Uri
					.parse("market://search?q=pname:" + SENDLOG_PACKAGE_NAME));
			title = activity.getResources().getIdentifier("sendlog_install_",
					"string", pkg);
			message = activity.getResources().getIdentifier("sendlog_install",
					"string", pkg);
		} else {
			intent.putExtra("filter", TAG + ":D *:W");
			intent.setType("0||android@ub0r.de");
			title = activity.getResources().getIdentifier("sendlog_run_",
					"string", pkg);
			message = activity.getResources().getIdentifier("sendlog_run",
					"string", pkg);
		}
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		final AlertDialog.Builder b = new AlertDialog.Builder(activity);
		b.setIcon(android.R.drawable.ic_dialog_info);
		b.setTitle(title);
		b.setMessage(message);
		b.setPositiveButton(android.R.string.ok, new FireIntent(activity,
				intent));
		b.setNegativeButton(android.R.string.cancel, null);
		b.show();
	}
}
