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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

/**
 * Copyright Paul Mutton
 * http://www.jibble.org/
 *
 */
public class RequestThread extends Thread {

    public RequestThread(Socket socket, File rootDir) {
        _socket = socket;
        _rootDir = rootDir;
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
    }

	private String appName(PackageManager packageManager, PackageInfo info) {
		ApplicationInfo appInfo = info.applicationInfo;
		if (appInfo == null
				|| (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
			return null;

		String name = appInfo.name;
		if (name == null) {
			name = appInfo.loadLabel(packageManager).toString();
		}
		return name;
	}

	private class HTTPException extends Exception {
		private static final long serialVersionUID = 1L;

		int code;

		HTTPException(int code, String text) {
			super(text);
			this.code = code;
		}
	}

    @Override
	public void run() {
		try {
			BufferedReader in = null;
			BufferedOutputStream out = null;

			try {

				_socket.setSoTimeout(30000);
				in = new BufferedReader(new InputStreamReader(
						_socket.getInputStream()), 256);
				out = new BufferedOutputStream(_socket.getOutputStream(), 256);

				String request = in.readLine();
				if (request == null
						|| !request.startsWith("GET ")
						|| !(request.endsWith(" HTTP/1.0") || request
								.endsWith("HTTP/1.1"))) {
					// Invalid request type (no "GET")
					throw new HTTPException(500, "Invalid Method.");
				}
				String path = request.substring(4, request.length() - 9);

				if (path.equals("/packages")) {

					final PackageManager packageManager = ServalBatPhoneApplication.context
							.getPackageManager();
					List<PackageInfo> packages = packageManager
							.getInstalledPackages(0);
					Set<PackageInfo> sortedPackages = new TreeSet<PackageInfo>(
							new Comparator<PackageInfo>() {
								@Override
								public int compare(PackageInfo object1,
										PackageInfo object2) {
									String name1 = appName(packageManager,
											object1);
									if (name1 == null)
										return -1;
									String name2 = appName(packageManager,
											object2);
									if (name2 == null)
										return 1;
									return name1.compareTo(name2);
								}
							});

					for (PackageInfo info : packages) {
						ApplicationInfo appInfo = info.applicationInfo;
						if (appInfo == null
								|| (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
							continue;
						sortedPackages.add(info);
					}

					sendHeader(out, 200, "text/html", -1,
							System.currentTimeMillis());
					String title = "Index of " + path;
					out.write(("<html><head><title>" + title
							+ "</title></head><body><h3>Index of " + path + "</h3><p>\n")
							.getBytes());

					for (PackageInfo info : sortedPackages) {
						ApplicationInfo appInfo = info.applicationInfo;
						if (appInfo == null
								|| (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
							continue;

						out.write(("<a href=\"/packages/" + appInfo.packageName
								+ ".apk\">" + appName(packageManager, info)
								+ "</a> " + info.versionName + "<br>\n")
								.getBytes());
					}

					out.write(("</p><hr><p>" + SimpleWebServer.VERSION + "</p></body><html>")
							.getBytes());

				} else {
					File file;
					String contentType = null;

					if (path.startsWith("/packages/")) {
						final PackageManager packageManager = ServalBatPhoneApplication.context
								.getPackageManager();

						PackageInfo info = packageManager.getPackageInfo(
								path.substring(path.lastIndexOf('/') + 1,
										path.lastIndexOf('.')), 0);
						ApplicationInfo appInfo = info.applicationInfo;
						file = new File(appInfo.sourceDir).getCanonicalFile();
						contentType = SimpleWebServer.MIME_TYPES.get("apk");
					} else {
						file = new File(_rootDir, URLDecoder.decode(path,
								"UTF-8")).getCanonicalFile();

						if (!file.toString().startsWith(_rootDir.toString())) {
							// Uh-oh, it looks like some lamer is trying to take
							// a peek outside of our web root directory.
							throw new HTTPException(403, "Permission Denied.");
						}
					}

					if (file.isDirectory()) {
						// Check to see if there is an index file in the
						// directory.
						File indexFile = new File(file, "index.html");
						if (indexFile.exists() && !indexFile.isDirectory()) {
							file = indexFile;
						}
					}

					if (!file.exists())
						throw new HTTPException(404, "File Not Found.");

					if (file.isDirectory()) {
						// print directory listing
						if (!path.endsWith("/")) {
							path = path + "/";
						}

						File[] files = file.listFiles();
						Set<File> sortedFiles=new TreeSet<File>();
						for (int i = 0; i < files.length; i++) {
							sortedFiles.add(files[i]);
						}

						sendHeader(out, 200, "text/html", -1,
								System.currentTimeMillis());
						String title = "Index of " + path;
						out.write(("<html><head><title>" + title
								+ "</title></head><body><h3>Index of " + path + "</h3><p>\n")
								.getBytes());
						for (File f : sortedFiles) {
							String filename = f.getName();
							String description = "";
							if (file.isDirectory()) {
								description = "&lt;DIR&gt;";
							}
							out.write(("<a href=\"" + path + filename + "\">"
									+ filename + "</a> " + description + "<br>\n")
									.getBytes());
						}
						out.write(("</p><hr><p>" + SimpleWebServer.VERSION + "</p></body><html>")
								.getBytes());
					} else {
						InputStream reader = new BufferedInputStream(
								new FileInputStream(
								file));
						try {
							if (contentType == null)
								contentType = SimpleWebServer.MIME_TYPES
										.get(SimpleWebServer.getExtension(file));

							if (contentType == null) {
								contentType = "application/octet-stream";
							}

							sendHeader(out, 200, contentType, file.length(),
									file.lastModified());

							byte[] buffer = new byte[4096];
							int bytesRead;
							while ((bytesRead = reader.read(buffer)) != -1) {
								out.write(buffer, 0, bytesRead);
							}
						} finally {
							reader.close();
						}
					}
				}

			} catch (NameNotFoundException e) {
				sendError(out, 404, "File Not Found.");
			} catch (HTTPException e) {
				sendError(out, e.code, e.getMessage());
			} catch (Exception e) {
				Log.v("BatPhone", e.toString(), e);
				sendError(out, 500, e.toString());
			} finally {
				if (out != null) {
					out.flush();
					out.close();
				}
			}
		} catch (IOException e) {
			Log.v("BatPhone", e.toString(), e);
		}
    }

    private File _rootDir;
    private Socket _socket;

}