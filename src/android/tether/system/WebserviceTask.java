/**
 *  This program is free software; you can redistribute it and/or modify it under 
 *  the terms of the GNU General Public License as published by the Free Software 
 *  Foundation; either version 3 of the License, or (at your option) any later 
 *  version.
 *  You should have received a copy of the GNU General Public License along with 
 *  this program; if not, see <http://www.gnu.org/licenses/>. 
 *  Use this application at your own risk.
 *
 *  Copyright (c) 2009 by Harald Mueller, Seth Lemons and Ben Buxton.
 */

package android.tether.system;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.util.Log;

public class WebserviceTask {
	
	public static final String MSG_TAG = "TETHER -> WebserviceTask";
	public static final String DOWNLOAD_FILEPATH = "/sdcard/download";
	
	public Properties queryForProperty(String url) {
		Properties properties = null; 
		HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(String.format(url));
        try {
            HttpResponse response = client.execute(request);

            StatusLine status = response.getStatusLine();
            Log.d(MSG_TAG, "Request returned status " + status);
            if (status.getStatusCode() == 200) {
	            HttpEntity entity = response.getEntity();
	            properties = new Properties();
	            properties.load(entity.getContent());
            }
        } catch (IOException e) {
        	Log.d(MSG_TAG, "Can't get property '"+url+"'.");
        }
		return properties;
	}
	
	public boolean downloadUpdateFile(String downloadFileUrl, String destinationFilename) {
		if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED) == false) {
			return false;
		}
		File downloadDir = new File(DOWNLOAD_FILEPATH);
		if (downloadDir.exists() == false) {
			downloadDir.mkdirs();
		}
		else {
			File downloadFile = new File(DOWNLOAD_FILEPATH+"/"+destinationFilename);
			if (downloadFile.exists()) {
				downloadFile.delete();
			}
		}
		return this.downloadFile(downloadFileUrl, DOWNLOAD_FILEPATH, destinationFilename);
	}
	
	public boolean downloadFile(String url, String destinationDirectory, String destinationFilename) {
		boolean filedownloaded = true;
		HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(String.format(url));
        try {
            HttpResponse response = client.execute(request);
            StatusLine status = response.getStatusLine();
            Log.d(MSG_TAG, "Request returned status " + status);
            if (status.getStatusCode() == 200) {
	            HttpEntity entity = response.getEntity();
	            InputStream instream = entity.getContent();
	            FileOutputStream out = new FileOutputStream(new File(destinationDirectory+"/"+destinationFilename));
	            byte buf[] = new byte[1024];
	            int len;
	            while((len = instream.read(buf)) > 0) {
	            	out.write(buf,0,len);
            	}
	            out.close();
            }
            else {
            	throw new IOException();
            }
        } catch (IOException e) {
        	Log.d(MSG_TAG, "Can't download file '"+url+"'.");
        	filedownloaded = false;
        }
        return filedownloaded;
	}
}
