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

package org.jibble.simplewebserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Date;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

/**
 * Copyright Paul Mutton
 * http://www.jibble.org/
 *
 */
public class RequestThread extends Thread {
    
	public RequestThread(Socket socket, File rootDir, String stringIP) {
        _socket = socket;
        _rootDir = rootDir;
        _stringIP = stringIP;
    }
    
    private static void sendHeader(BufferedOutputStream out, int code, String contentType, long contentLength, long lastModified) throws IOException {
        out.write(("HTTP/1.0 " + code + " OK\r\n" + 
                   "Date: " + new Date().toString() + "\r\n" +
                   "Server: JibbleWebServer/1.0\r\n" +
                   "Content-Type: " + contentType + "\r\n" +
                   "Expires: Thu, 01 Dec 1994 16:00:00 GMT\r\n" +
                   ((contentLength != -1) ? "Content-Length: " + contentLength + "\r\n" : "") +
                   "Last-modified: " + new Date(lastModified).toString() + "\r\n" +
                   "\r\n").getBytes());
    }
    
    private static void sendError(BufferedOutputStream out, int code, String message) throws IOException {
        message = message + "<hr>" + SimpleWebServer.VERSION;
        sendHeader(out, code, "text/html", message.length(), System.currentTimeMillis());
        out.write(message.getBytes());
        out.flush();
        out.close();
    }
    
    public void run() {
        InputStream reader = null;
        try {
            _socket.setSoTimeout(30000);
            BufferedReader in = new BufferedReader(new InputStreamReader(_socket.getInputStream()));
            BufferedOutputStream out = new BufferedOutputStream(_socket.getOutputStream());
            
            String request = in.readLine();
            if (request == null || !request.startsWith("GET ") || !(request.endsWith(" HTTP/1.0") || request.endsWith("HTTP/1.1"))) {
                // Invalid request type (no "GET")
                sendError(out, 500, "Invalid Method.");
                return;
            }            
            String path = request.substring(4, request.length() - 9);            
            File file = new File(_rootDir, URLDecoder.decode(path, "UTF-8")).getCanonicalFile();
            
            if (file.isDirectory()) {
                // Check to see if there is an index file in the directory.
                File indexFile = new File(file, "index.html");
                if (indexFile.exists() && !indexFile.isDirectory()) {
                    file = indexFile;
                }
            }

            if (!file.toString().startsWith(_rootDir.toString())) {
                // Uh-oh, it looks like some lamer is trying to take a peek
                // outside of our web root directory.
                sendError(out, 403, "Permission Denied.");
            }
            else if (!file.exists()) {
                // The file was not found.
                sendError(out, 404, "File Not Found.");
            }
            else if (file.isDirectory()) {
                // print directory listing
                if (!path.endsWith("/")) {
                    path = path + "/";
                }
                File[] files = file.listFiles();
                               
                sendHeader(out, 200, "text/html", -1, System.currentTimeMillis());
                for (int i = 0; i < files.length; i++) {
                    file = files[i];
                    String filename = file.getName();                    
                    // Write only the manifest files
                    if (filename.endsWith("manifest"))
                    out.write(("\nhttp://"+_stringIP+"/"+filename+"\n").getBytes());
                }
            }
            else {
                reader = new BufferedInputStream(new FileInputStream(file));
            
                String contentType = (String)SimpleWebServer.MIME_TYPES.get(SimpleWebServer.getExtension(file));
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                
                sendHeader(out, 200, contentType, file.length(), file.lastModified());
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = reader.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                reader.close();
            }
            out.flush();
            out.close();
        }
        catch (IOException e) {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (Exception anye) {
                    // Do nothing.
                }
            }
        }
    }
    
    private File _rootDir;
    private Socket _socket;
    private String _stringIP;
}