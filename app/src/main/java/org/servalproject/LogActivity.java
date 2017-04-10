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
/**
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *  You should have received a copy of the GNU General Public License along with
 *  this program; if not, see <http://www.gnu.org/licenses/>.
 *  Use this application at your own risk.
 *
 *  Copyright (c) 2009 by Harald Mueller and Seth Lemons.
 */

package org.servalproject;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.servalproject.system.ChipsetDetection;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

public class LogActivity extends Activity {

	public static final String MSG_TAG = "ADHOC -> AccessControlActivity";

	private static final String HEADER = "<html><head><title>background-color</title> "+
	 	"<style type=\"text/css\"> "+
	 	"body { background-color:#181818; font-family:Arial; font-size:100%; color: #ffffff } "+
	 	".date { font-family:Arial; font-size:80%; font-weight:bold} "+
			".done { font-family:Arial; font-size:80%; color: #859554} "
			+
			".failed { font-family:Arial; font-size:80%; color: #859554} "
			+
			".heading { font-family:Arial; font-size:100%; font-weight: bold; color: #859554} "
			+
			".skipped { font-family:Arial; font-size:80%; color: #859554} " +
	 	"</style> "+
	 	"</head><body>";
	private static final String FOOTER = "</body></html>";

	private WebView webView = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.logview);

        this.webView = (WebView) findViewById(R.id.webviewLog);
        this.webView.getSettings().setJavaScriptEnabled(false);
        this.webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        this.webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        this.webView.getSettings().setSupportMultipleWindows(false);
        this.webView.getSettings().setSupportZoom(false);
        this.setWebViewContent();
    }

    private void setWebViewContent() {
    	this.webView.loadDataWithBaseURL("fake://fakeme",HEADER+this.readLogfile()+FOOTER , "text/html", "UTF-8", "fake://fakeme");
    }

    private String readLogfile(){
        FileInputStream fis = null;
        InputStreamReader isr = null;
        String data = "";

		List<String> logfiles = ChipsetDetection
				.getList("/data/data/org.servalproject/conf/logfiles.list");

        for (String l : logfiles) {
			if (l.indexOf(":") == -1)
				continue;

			String logfile = l.substring(0, l.indexOf(":"));
			String description = l.substring(l.indexOf(":") + 1, l.length());

			data = data
				+ "<div class=\"heading\">"+description+"</div>\n";
			try {
				File file = new File("/data/data/org.servalproject/var/"
						+ logfile + ".log");
				fis = new FileInputStream(file);
				isr = new InputStreamReader(fis, "utf-8");
				char[] buff = new char[(int) file.length()];
				isr.read(buff);
				data = data + new String(buff);
			} catch (Exception e) {
				// We don't need to display anything, just put a message in the
				// log
				data = data + "<div class=\"failed\">No messages</div>";
				// this.application.displayToastMessage("Unable to open log-File!");
			} finally {
        	 try {
        		 if (isr != null)
        			 isr.close();
        		 if (fis != null)
        			 fis.close();
        	 } catch (Exception e) {
        		 // nothing
        	 }
         }
		}
		return data;
    }

	public static void logMessage(String logname, String message,
			boolean failedP) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(
					"/data/data/org.servalproject/var/" + logname + ".log",
					true), 256);
			Calendar currentDate = Calendar.getInstance();
			SimpleDateFormat formatter = new SimpleDateFormat(
					"yyyy/MMM/dd HH:mm:ss");
			String dateNow = formatter.format(currentDate.getTime());
			writer.write("<div class=\"date\">" + dateNow + "</div>\n");
			writer.write("<div class=\"action\">" + message + "</div>\n");
			writer.write("<div class=\"");
			if (failedP)
				writer.write("failed\">failed");
			else
				writer.write("done\">done");
			writer.write("</div><hr>\n");
			writer.close();
		} catch (IOException e) {
			// Should we do something here?
			try {
				if (writer != null)
					writer.close();
			} catch (IOException e2) {
			}
		}
		return;
	}

	public static void logErase(String logname) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(
					"/data/data/org.servalproject/var/" + logname + ".log",
					false), 256);
			writer.close();
		} catch (IOException e) {
			// Should we do something here?
			try {
				if (writer != null)
					writer.close();
			} catch (IOException e2) {
			}
		}
		return;
	}

}
