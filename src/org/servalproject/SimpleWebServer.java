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

/*
Copyright Paul James Mutton, 2001-2004, http://www.jibble.org/

This file is part of Mini Wegb Server / SimpleWebServer.

This software is dual-licensed, allowing you to choose between the GNU
General Public License (GPL) and the www.jibble.org Commercial License.
Since the GPL may be too restrictive for use in a proprietary application,
a commercial license is also provided. Full license information can be
found at http://www.jibble.org/licenses/

$Author: pjm2 $
$Id: ServerSideScriptEngine.java,v 1.4 2004/02/01 13:37:35 pjm2 Exp $

 */

package org.servalproject;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import android.util.Log;

/**
 * Copyright Paul Mutton
 * http://www.jibble.org/
 *
 */
public class SimpleWebServer extends Thread {

	private ServerSocket _serverSocket;
	private boolean _running = true;

	public SimpleWebServer(int port) throws IOException {
        _serverSocket = new ServerSocket(port);
        start();
    }

    @Override
	public void interrupt() {
    	_running=false;
		try {
			_serverSocket.close();
		} catch (IOException e) {
			Log.e("BatPhone WebServer", e.toString(), e);
		}
		super.interrupt();
	}

	@Override
	public void run() {
        while (_running) {
        	try {
				Socket socket = _serverSocket.accept();
				RequestThread requestThread = new RequestThread(socket);
                requestThread.start();
        	}catch (IOException e) {
            	Log.e("BatPhone WebServer",e.toString(),e);
            }
        }
		try {
			_serverSocket.close();
		} catch (IOException e) {
			Log.e("BatPhone WebServer", e.toString(), e);
		}
		_serverSocket = null;
    }
}