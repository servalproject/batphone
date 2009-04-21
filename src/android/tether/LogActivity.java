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

package android.tether;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class LogActivity extends Activity {

	public static final String MSG_TAG = "TETHER -> AccessControlActivity";
	
	private static final String HEADER = "<html><head><title>background-color</title> "+
	 	"<style type=\"text/css\"> "+
	 	"body { background-color:#181818; font-family:Arial; font-size:100%; color: #ffffff } "+
	 	".date { font-family:Arial; font-size:80%; font-weight:bold} "+
	 	".done { font-family:Arial; font-size:80%; color: #2ff425} "+
	 	".failed { font-family:Arial; font-size:80%; color: #ff3636} "+
	 	".skipped { font-family:Arial; font-size:80%; color: #6268e5} "+
	 	"</style> "+
	 	"</head><body>";
	private static final String FOOTER = "</body></html>";
	
	private WebView webView = null;
	
	private TetherApplication application;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.logview);
        
        // Init Application
        this.application = (TetherApplication)this.getApplication();
        
        this.webView = (WebView) findViewById(R.id.webviewLog);
        this.webView.getSettings().setJavaScriptEnabled(false);
        this.webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        this.webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        this.webView.getSettings().setPluginsEnabled(false);
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
        try{
	             File file = new File(application.coretask.DATA_FILE_PATH+"/var/tether.log");
                 fis = new FileInputStream(file);
                 isr = new InputStreamReader(fis, "utf-8");
                 char[] buff = new char[(int) file.length()];
                 isr.read(buff);
                 data = new String(buff);
         }
         catch (Exception e) {      
        	 this.application.displayToastMessage("Unable to open log-File!");
         }
         finally {
        	 try {
        		 if (isr != null)
        			 isr.close();
        		 if (fis != null)
        			 fis.close();
        	 } catch (Exception e) {
        		 // nothing
        	 }
         }
         return data;
    }
}
