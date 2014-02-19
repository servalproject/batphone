/**
 * Copyright (C) 2011 The Serval Project
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

package org.servalproject.servald;

import android.database.Cursor;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.ServalDFailureException;
import org.servalproject.servaldna.SubscriberId;

/**
 * Low-level class for invoking servald JNI command-line operations.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class ServalD
{

	public static final String TAG = "ServalD";
	private static long started = -1;
	static boolean log = false;

	private ServalD() {
	}

	/** Start the servald server process if it is not already running.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void serverStart(String execPath)
			throws ServalDFailureException {
		ServalDCommand.Status result = ServalDCommand.serverStart(execPath);
		started = System.currentTimeMillis();
		Log.i(ServalD.TAG, "Server " + (result.getResult() == 0 ? "started" : "already running") + ", pid=" + result.pid);
	}

	public static void serverStart() throws ServalDFailureException {
		serverStart(ServalBatPhoneApplication.context.coretask.DATA_FILE_PATH
				+ "/bin/servald");
	}
	/** Stop the servald server process if it is running.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void serverStop() throws ServalDFailureException {
		ServalDCommand.Status result = ServalDCommand.serverStop();
		started = -1;
		Log.i(ServalD.TAG, "server " + (result.getResult() == 0 ? "stopped, pid=" + result.pid : "not running"));
	}

	/** Query the servald server process status.
	 *
	 * @return	True if the process is running
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean serverIsRunning() throws ServalDFailureException {
		ServalDCommand.Status result = ServalDCommand.serverStatus();
		return result.getResult() == 0;
	}

	public static long uptime() {
		if (started == -1)
			return -1;
		return System.currentTimeMillis() - started;
	}

	public static Cursor rhizomeList(final String service, final String name, final SubscriberId sender, final SubscriberId recipient)
			throws ServalDFailureException
	{
		return new ServalDCursor(){
			@Override
			void fillWindow(CursorWindowJniResults window, int offset, int numRows) throws ServalDFailureException{
				ServalDCommand.rhizomeList(window, service, name, sender, recipient, offset, numRows);
			}
		};
	}

	public static boolean isRhizomeEnabled() {
		return ServalDCommand.getConfigItemBoolean("rhizome.enable", true);
	}

	// MeshMS API
	public static Cursor listConversations(final SubscriberId sender)
			throws ServalDFailureException
	{
		return new ServalDCursor() {
			@Override
			void fillWindow(CursorWindowJniResults window, int offset, int numRows) throws ServalDFailureException {
				ServalDCommand.listConversations(window, sender, offset, numRows);
			}
		};
	}

	public static Cursor listMessages(final SubscriberId sender, final SubscriberId recipient)
			throws ServalDFailureException
	{
		return new ServalDCursor() {
			@Override
			void fillWindow(CursorWindowJniResults window, int offset, int numRows) throws ServalDFailureException {
				if (offset!=0 || numRows!=-1)
					throw new ServalDFailureException("Only one window supported");
				Log.v(TAG, "running meshms list messages "+sender+", "+recipient);
				ServalDCommand.listMessages(window, sender, recipient);
			}
		};
	}
}
