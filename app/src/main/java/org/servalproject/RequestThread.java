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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.util.Log;

/**
 * Derived from; http://www.jibble.org/ Copyright Paul Mutton
 */
public class RequestThread extends Thread {

	private final Socket _socket;

	public static final Hashtable<String, String> MIME_TYPES = new Hashtable<String, String>();

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
		MIME_TYPES.put(".css", text + "css");
		MIME_TYPES.put(".apk", "application/vnd.android.package-archive");
	}

	public RequestThread(Socket socket) {
        _socket = socket;
    }

	private void sendHeader(OutputStream out, int code, String contentType,
			long contentLength, long lastModified) throws IOException {
		String header = "HTTP/1.0 "
				+ code
				+ " OK\n" +
				"Date: "
				+ new Date().toString()
				+ "\n" +
				"Content-Type: "
				+ contentType
				+ "\n" +
				"Connection: close\n" +
				"Expires: Thu, 01 Dec 1994 16:00:00 GMT\n" +
				"Cache-Control: no-cache\n" +
				((contentLength != -1) ? "Content-Length: " + contentLength
						+ "\n" : "") +
				"Last-modified: " + new Date(lastModified).toString() + "\n\n";
		Log.v("BatPhone", "Returning header\n" + header);
		writeString(out, header);
    }

	private void sendError(OutputStream out, int code, String message)
			throws IOException {
        sendHeader(out, code, "text/html", message.length(), System.currentTimeMillis());
		writeString(out, message);
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

	private void writeString(OutputStream out, String str) throws IOException {
		out.write(str.getBytes());
	}

	private void listPackages(String path, OutputStream out) throws IOException {
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
		writeString(out, "<html><head><title>Index of " + path
				+ "</title></head><body><h3>Index of " + path + "</h3><p>\n");

		for (PackageInfo info : sortedPackages) {
			ApplicationInfo appInfo = info.applicationInfo;
			if (appInfo == null
					|| (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
				continue;

			writeString(out, "<a href=\"/packages/" + appInfo.packageName
					+ ".apk\">" + appName(packageManager, info)
					+ "</a> " + info.versionName + "<br>\n");
		}
		writeString(out, "</p></body></html>");
	}

    @Override
	public void run() {
		BufferedReader in = null;
		OutputStream out = null;
		InputStream content = null;

		try {
			_socket.setSoTimeout(30000);
			_socket.setSendBufferSize(4096);
			in = new BufferedReader(new InputStreamReader(
					_socket.getInputStream()), 256);
			out = _socket.getOutputStream();

			String request = in.readLine();
			if (request == null
					|| !request.startsWith("GET ")
					|| !(request.endsWith(" HTTP/1.0") || request
							.endsWith("HTTP/1.1"))) {
				// Invalid request type (no "GET")
				throw new HTTPException(500, "Invalid Method.");
			}
			String path = request.substring(4, request.length() - 9);
			Log.v("BatPhone", request);
			while (!request.equals("")) {
				request = in.readLine();
				Log.v("BatPhone", request);
			}
			String contentType = null;
			long contentLength = -1;
			long contentModified = System.currentTimeMillis();

			if (path.equals("/packages")) {
				listPackages(path, out);
				return;
			}

			if (path.startsWith("/packages/")) {
				final PackageManager packageManager = ServalBatPhoneApplication.context
						.getPackageManager();

				PackageInfo info = packageManager.getPackageInfo(
						path.substring(path.lastIndexOf('/') + 1,
								path.lastIndexOf('.')), 0);
				ApplicationInfo appInfo = info.applicationInfo;
				File file = new File(appInfo.sourceDir).getCanonicalFile();
				if (!file.exists())
					throw new HTTPException(404, "File Not Found.");
				Log.v("BatPhone", "Serving file " + file);

				contentType = MIME_TYPES.get(".apk");
				contentLength = file.length();
				contentModified = file.lastModified();
				content = new BufferedInputStream(new FileInputStream(file),
						4096);
			} else {
				AssetManager am = ServalBatPhoneApplication.context.getAssets();
				if (path.indexOf('?') >= 0)
					path = path.substring(0, path.indexOf('?'));
				if (path.equals("/"))
					path = "/index.html";

				int ext = path.lastIndexOf('.');
				if (path.lastIndexOf('/') > ext)
					ext = -1;
				if (ext >= 0)
					contentType = MIME_TYPES.get(path
							.substring(ext).toLowerCase());
				content = am.open(path.substring(1));
				Log.v("BatPhone", "Serving asset " + path.substring(1));
			}

			if (content == null)
				throw new HTTPException(404, "File Not Found.");

			if (contentType == null)
				contentType = "application/octet-stream";

			sendHeader(out, 200, contentType, contentLength, contentModified);

			byte[] buffer = new byte[256];
			int bytesRead;
			while ((bytesRead = content.read(buffer)) != -1) {
				Log.v("BatPhone", "read " + bytesRead + " bytes");
				if (bytesRead > 0) {
					out.write(buffer, 0, bytesRead);
					Log.v("BatPhone", "written");
				}
			}
			_socket.shutdownInput();
			_socket.shutdownOutput();
			Log.v("BatPhone", "Done");
		} catch (NameNotFoundException e) {
			try {
				sendError(out, 404, "File Not Found.");
			} catch (IOException e1) {
				Log.v("BatPhone", e1.getMessage(), e1);
			}
		} catch (HTTPException e) {
			try {
				sendError(out, e.code, e.getMessage());
			} catch (IOException e1) {
				Log.v("BatPhone", e1.getMessage(), e1);
			}
		} catch (IOException e) {
			Log.v("BatPhone", e.getMessage(), e);
		} catch (Exception e) {
			Log.v("BatPhone", e.getMessage(), e);
			try {
				sendError(out, 500, e.getMessage());
			} catch (IOException e1) {
				Log.v("BatPhone", e1.getMessage(), e1);
			}
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e1) {
				}
			}
			if (content != null) {
				try {
					content.close();
				} catch (IOException e1) {
				}
			}
		}
    }


}