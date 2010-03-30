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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.util.Log;

public class ExecuteProcess {

	public static final String MSG_TAG = "TETHER -> ExecuteProcess";
	
	private boolean runAsRoot;
	private ArrayList<String> stdOutLines;
	private int exitCode = -1;
	private Process process = null;

	public ExecuteProcess() {
		this.runAsRoot = false;	//Default
	}
	
	public ExecuteProcess(boolean runAsRoot) {
		this.runAsRoot = runAsRoot;
	}
	
	public boolean isRunAsRoot() {
		return runAsRoot;
	}

	public void setRunAsRoot(boolean runAsRoot) {
		this.runAsRoot = runAsRoot;
	}

	public int getExitCode() {
		return this.exitCode;
	}
	
	public ArrayList<String> getStdOutLines() {
		return this.stdOutLines;
	}
	
	public synchronized void execute(String command, boolean runAsRoot) {
		this.runAsRoot = runAsRoot;
		this.execute(command);
	}
	
	private void execute(String command) {
		
		DataOutputStream os = null;
    	InputStream stderr = null;
    	InputStream stdout = null;
    	String line;
    	
    	this.stdOutLines = new ArrayList<String>();
    	Log.d(MSG_TAG, "Executing command (root:"+this.runAsRoot+"): " + command);
    	try {
    		if (this.runAsRoot) {
    			this.process = Runtime.getRuntime().exec("su");
    		}
    		else {
    			this.process = Runtime.getRuntime().exec(command);
    		}
    		stderr = this.process.getErrorStream();
    		stdout = this.process.getInputStream();
    		BufferedReader errBr = new BufferedReader(new InputStreamReader(stderr), 8192);
    		BufferedReader inputBr = new BufferedReader(new InputStreamReader(stdout), 8192);
    		if (this.runAsRoot) {
	    		os = new DataOutputStream(process.getOutputStream());
		        os.writeBytes(command+"\n");
		        os.flush();
		        os.writeBytes("exit\n");
		        os.flush();
    		}
    		while ((line = inputBr.readLine()) != null) {
    			this.stdOutLines.add(line.trim());
    		}
    		while ((line = errBr.readLine()) != null);
    		this.exitCode = this.process.waitFor();
    	} catch (Exception e) {
    		Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
    	}
    	finally {
    		// Closing streams
			try {
				if (os != null)
					os.close();
				if (stderr != null)
					stderr.close();
				if (stdout != null)
					stdout.close();
			} catch (Exception ex) {;}
			// Destroy process
			try {
				this.process.destroy();
			} catch (Exception e) {;}
    	}
	}
}