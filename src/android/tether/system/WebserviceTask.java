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

import java.io.IOException;
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
}
