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
	private long timeout = 2000;
	private ArrayList<String> stdOutLines;
	private int exitCode = -1;

	public ExecuteProcess() {
		this.runAsRoot = false;	//Default
		this.timeout = 2000; 	//Default
	}
	
	public ExecuteProcess(boolean runAsRoot) {
		this.runAsRoot = runAsRoot;
	}
	
	public ExecuteProcess(boolean runAsRoot, long timeout) {
		this.runAsRoot = runAsRoot;
		this.timeout = timeout;
	}
	
	public boolean isRunAsRoot() {
		return runAsRoot;
	}

	public void setRunAsRoot(boolean runAsRoot) {
		this.runAsRoot = runAsRoot;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public int getExitCode() {
		return this.exitCode;
	}
	
	public ArrayList<String> getStdOutLines() {
		return this.stdOutLines;
	}
	
	public void execute(String command, boolean runAsRoot, long timeout) {
		this.runAsRoot = runAsRoot;
		this.timeout = timeout;
		this.execute(command);
	}
	
	public void execute(String command) {
		this.stdOutLines = new ArrayList<String>();
		this.exitCode = -1;
		CommandHandler commandHandler = null;
		try {
			commandHandler = new CommandHandler(this.runAsRoot, command);
			commandHandler.start();
			commandHandler.join(this.timeout);
	        if (commandHandler.isAlive()) {
	        	Log.d(MSG_TAG, "TIMEOUT! Running command '"+command+"'!");
        		commandHandler.interrupt();
        	}
		}
    	catch (Exception ex) {
    		Log.d(MSG_TAG, "Exception happend - Here is what I know: "+ex.getMessage());
    		commandHandler = null;
    	}
    	if (commandHandler != null) {
    		this.stdOutLines = commandHandler.getStdOutLines();
    		this.exitCode = commandHandler.getExitCode();
    	}
    	commandHandler = null;
	}
}

class CommandHandler extends Thread {
	
	public static final String MSG_TAG = "TETHER -> ExecuteProcess";
	
	private Process process = null;
	private String command;
	private Runtime runtime;
	private ArrayList<String> stdOutLines;
	private int exitCode = -1;
	private boolean runAsRoot = false;

	CommandHandler(String command) {
		this.command = command;
		this.runAsRoot = false;
		this.runtime = Runtime.getRuntime();
	}

	CommandHandler(boolean runAsRoot, String command) {
		this.command = command;
		this.runAsRoot = runAsRoot;
		this.runtime = Runtime.getRuntime();
	}
	
	public int getExitCode() {
		return this.exitCode;
	}
	
	public ArrayList<String> getStdOutLines() {
		return this.stdOutLines;
	}
	
	public void destroy() {
		try {
			if (this.process != null) {
				this.process.destroy();
			}
			this.interrupt();
		}
		catch (Exception ex) {
			// nothing
		}
	}
	
	public void run() {
		DataOutputStream os = null;
    	InputStream stderr = null;
    	InputStream stdout = null;
    	String line;
    	
    	this.stdOutLines = new ArrayList<String>();
    	Log.d(MSG_TAG, "Executing command (root:"+this.runAsRoot+"): " + command);
    	try {
    		if (this.runAsRoot) {
    			this.process = this.runtime.exec("su");
    		}
    		else {
    			this.process = this.runtime.exec(command);
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
    			stdOutLines.add(line.trim());
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