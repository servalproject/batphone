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

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;

import android.util.Log;

/**
 * Copyright Paul Mutton
 * http://www.jibble.org/
 *
 */
public class SimpleWebServer extends Thread {

    public static final String VERSION = "SimpleWebServer  http://www.jibble.org/";
    public static final Hashtable<String,String> MIME_TYPES = new Hashtable<String,String>();

    static {
        String image = "image/";
        MIME_TYPES.put(".gif", image + "gif");
        MIME_TYPES.put(".jpg", image + "jpeg");
        MIME_TYPES.put(".jpeg", image + "jpeg");
        MIME_TYPES.put(".png", image + "png");
        String text = "text/";
        MIME_TYPES.put(".html", text + "html");
        MIME_TYPES.put(".htm", text + "html");
        MIME_TYPES.put(".txt", text + "plain");
    }

    public SimpleWebServer(File rootDir, int port) throws IOException {
        _rootDir = rootDir.getCanonicalFile();
        if (!_rootDir.isDirectory()) {
            throw new IOException("Not a directory.");
        }
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
                RequestThread requestThread = new RequestThread(socket, _rootDir);
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

    // Work out the filename extension.  If there isn't one, we keep
    // it as the empty string ("").
    public static String getExtension(java.io.File file) {
        String extension = "";
        String filename = file.getName();
        int dotPos = filename.lastIndexOf(".");
        if (dotPos >= 0) {
            extension = filename.substring(dotPos);
        }
        return extension.toLowerCase();
    }

    public static void main(String[] args) {
        try {
            @SuppressWarnings("unused")
			SimpleWebServer server = new SimpleWebServer(new File("./"), 80);
        }
        catch (IOException e) {
            System.out.println(e);
        }
    }

    private File _rootDir;
    private ServerSocket _serverSocket;
    private boolean _running = true;

}