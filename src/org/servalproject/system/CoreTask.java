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

package org.servalproject.system;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.util.Log;

public class CoreTask {

	public static final String MSG_TAG = "BatPhone";

	public String DATA_FILE_PATH;

	private Hashtable<String,String> runningProcesses = new Hashtable<String,String>();

	public void setPath(String path){
		this.DATA_FILE_PATH = path;
	}

	public class TiWlanConf {
	    /*
	     * Handle operations on the TiWlan.conf file.
	     */
	    public synchronized boolean write(Hashtable<String,String> values) {
	    	String filename = DATA_FILE_PATH+"/conf/tiwlan.ini";
	    	ArrayList<String> valueNames = Collections.list(values.keys());

	    	String fileString = "";

	    	ArrayList<String> inputLines = readLinesFromFile(filename);
	    	for (String line : inputLines) {
	    		for (String name : valueNames) {
	        		if (line.contains(name)){
		    			line = name+" = "+values.get(name);
		    			break;
		    		}
	    		}
	    		line+="\n";
	    		fileString += line;
	    	}
	    	return writeLinesToFile(filename, fileString);
	    }
	}

	public class AdhocConfig extends HashMap<String, String> {

		private static final long serialVersionUID = 1L;

		public HashMap<String, String> read() {
			String filename = DATA_FILE_PATH + "/conf/adhoc.conf";
			this.clear();
			for (String line : readLinesFromFile(filename)) {
				if (line.startsWith("#"))
					continue;
				if (!line.contains("="))
					continue;
				String[] data = line.split("=");
				if (data.length > 1) {
					this.put(data[0], data[1]);
				}
				else {
					this.put(data[0], "");
				}
			}
			return this;
		}

		public boolean write() {
			String lines = new String();
			for (String key : this.keySet()) {
				lines += key + "=" + this.get(key) + "\n";
			}
			return writeLinesToFile(DATA_FILE_PATH + "/conf/adhoc.conf", lines);
		}
	}

    public boolean chmod(String file, String mode) {
    	try {
			if (runCommand("chmod "+ mode + " " + file) == 0) {
				return true;
			}
		} catch (Exception e) {}
    	return false;
    }

    public ArrayList<String> readLinesFromFile(String filename) {
    	String line = null;
    	BufferedReader br = null;
    	InputStream ins = null;
    	ArrayList<String> lines = new ArrayList<String>();
    	File file = new File(filename);
    	if (file.canRead() == false)
    		return lines;
    	try {
    		ins = new FileInputStream(file);
    		br = new BufferedReader(new InputStreamReader(ins), 256);
    		while((line = br.readLine())!=null) {
    			lines.add(line.trim());
    		}
    	} catch (Exception e) {
    		Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
    	}
    	finally {
    		try {
    			ins.close();
    			br.close();
    		} catch (Exception e) {
    			// Nothing.
    		}
    	}
    	return lines;
    }

    public boolean writeLinesToFile(String filename, String lines) {
		OutputStream out = null;
		boolean returnStatus = false;
		Log.d(MSG_TAG, "Writing " + lines.length() + " bytes to file: " + filename);
		try {
			out = new FileOutputStream(filename);
        	out.write(lines.getBytes());
        	out.flush();
		} catch (Exception e) {
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
		}
		finally {
        	try {
        		if (out != null)
        			out.close();
        		returnStatus = true;
			} catch (IOException e) {
				returnStatus = false;
			}
		}
		return returnStatus;
    }

    public boolean isNatEnabled() {
    	ArrayList<String> lines = readLinesFromFile("/proc/sys/net/ipv4/ip_forward");
    	return lines.contains("1");
    }

    public String getKernelVersion() {
        ArrayList<String> lines = readLinesFromFile("/proc/version");
        String version = lines.get(0).split(" ")[2];
        Log.d(MSG_TAG, "Kernel version: " + version);
        return version;
    }

	public boolean isProcessRunning(String processName) throws IOException {
		return getPid(processName) >= 0;
	}

	public int getPid(String processName) throws IOException {
		int pid = -1;
		Hashtable<String, String> cmdLineCache = new Hashtable<String, String>();
    	File procDir = new File("/proc");
    	FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
                try {
                    Integer.parseInt(name);
                } catch (NumberFormatException ex) {
                    return false;
                }
                return true;
            }
        };
    	File[] processes = procDir.listFiles(filter);
    	for (File process : processes) {
    		// Checking if this is a already known process
			String processPath = process.getAbsolutePath();
			String cmdLine = this.runningProcesses.get(processPath);

			if (cmdLine == null) {
				ArrayList<String> cmdlineContent = this
						.readLinesFromFile(processPath + "/cmdline");
				if (cmdlineContent != null && cmdlineContent.size() > 0)
    				cmdLine = cmdlineContent.get(0);
				else
					cmdLine = "";
    		}
    		// Adding to tmp-Hashtable
			cmdLineCache.put(processPath, cmdLine);

    		// Checking if processName matches
    		if (cmdLine.contains(processName)) {
				pid = Integer.parseInt(process.getName());
    		}
    	}
		// Make sure runningProcesses only contains process that are still there
		// (still a chance that a pid will be reused between calls)
		this.runningProcesses = cmdLineCache;
		return pid;
    }

	// test for su permission, remember the result of this test until the next
	// reboot / force restart
	// some phones don't keep root on reboot...
	private static int hasRoot = 0;

	public boolean testRootPermission() {
		try {
			File su = new File("/system/bin/su");
			if (!su.exists()) {
				File su2 = new File("/system/xbin/su");
				if (!su2.exists())
					throw new IOException("Su not found");
			}

			// run an empty command until it succeeds, it should only fail if
			// the user fails to accept the su prompt or permission was denied
			while (runRootCommand("") != 0)
				;
			hasRoot = 1;
			return true;
		} catch (IOException e) {
			Log.e("BatPhone", "Unable to get root permission", e);
			hasRoot = -1;
			return false;
		}
    }

	public boolean hasRootPermission() {
		if (hasRoot == 0)
			testRootPermission();
		return hasRoot == 1;
	}

    //TODO: better exception type?
	public int runRootCommand(String command) throws IOException {
		return runRootCommand(command, true);
	}

	public int runRootCommand(String command, boolean wait) throws IOException {
		this.writeLinesToFile(DATA_FILE_PATH + "/sucmd", "#!/system/bin/sh\n"
				+ command);
		this.chmod(DATA_FILE_PATH + "/sucmd", "755");
		return runCommand(true, wait, DATA_FILE_PATH + "/sucmd");
    }

	public int runCommand(String command) throws IOException {
		return runCommand(false, true, command);
	}

	public int runCommand(boolean root, boolean wait, String command) throws IOException {
		Process proc = new ProcessBuilder()
				.command((root ? "/system/bin/su" : "/system/bin/sh"), "-c",
						command)
				.redirectErrorStream(true).start();

		if (!wait)
			return 0;

		BufferedReader stdOut = new BufferedReader(new InputStreamReader(
				proc.getInputStream()), 256);

		while (true) {
			String line = stdOut.readLine();
			if (line == null)
				break;
			Log.v(MSG_TAG, line);
		}

		stdOut.close();

		try {
			proc.waitFor();
		} catch (InterruptedException e) {
			Log.v(MSG_TAG, "Interrupted", e);
		}

		int returncode = proc.exitValue();
    	if (returncode != 0)
			Log.d(MSG_TAG, "Command error, return code: " + returncode);
		return returncode;
    }

	public void killProcess(String processName, boolean root) throws IOException {
		// try to kill running processes by name
		int pid, lastPid = -1;
		while ((pid = getPid(processName)) >= 0) {
			if (pid != lastPid) {
				try {
					Log.v("BatPhone", "Killing pid " + pid);
					if (root)
						runRootCommand("kill " + pid);
					else
						runCommand("kill " + pid);
				} catch (IOException e) {
					Log.v("BatPhone", "kill failed");
				}
			}
			lastPid = pid;
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
			}
		}
    }

    public String getProp(String property) {
    	return NativeTask.getProp(property);
    }

    public long[] getDataTraffic(String device) {
    	// Returns traffic usage for all interfaces starting with 'device'.
    	long [] dataCount = new long[] {0, 0};
    	if (device == "")
    		return dataCount;
    	for (String line : readLinesFromFile("/proc/net/dev")) {
    		if (line.startsWith(device) == false)
    			continue;
    		line = line.replace(':', ' ');
    		String[] values = line.split(" +");
    		dataCount[0] += Long.parseLong(values[1]);
    		dataCount[1] += Long.parseLong(values[9]);
    	}
    	return dataCount;
    }

	private void writeFile(String path, ZipInputStream str, long modified)
			throws IOException {
		File outFile = new File(path);
		outFile.getParentFile().mkdirs();
		BufferedOutputStream out = new BufferedOutputStream(
				new FileOutputStream(outFile), 8 * 1024);
		int len;
		byte buff[] = new byte[1024];
		while ((len = str.read(buff)) > 0) {
			out.write(buff, 0, len);
		}
		out.close();

		outFile.setLastModified(modified);
	}

	public void extractZip(InputStream asset, String folder) throws IOException {
		ZipInputStream str = new ZipInputStream(asset);
		{
			while (true) {
				ZipEntry ent = str.getNextEntry();
				if (ent == null)
					break;
				try {
					String filename = ent.getName();
					if (ent.isDirectory()) {
						File dir = new File(folder + "/" + filename + "/");
						if (!dir.mkdirs())
							Log.v("BatPhone", "Failed to create path "
									+ filename);
					} else {
						// try to write the file directly
						writeFile(folder + "/" + filename, str,
								ent.getTime());

						if (filename.indexOf("bin/") >= 0
								|| filename.indexOf("lib/") >= 0
								|| filename.indexOf("libs/") >= 0
								|| filename.indexOf("conf/") >= 0)
							runCommand("chmod 755 " + folder + "/"
									+ filename);

					}
				} catch (Exception e) {
					Log.v("BatPhone", e.toString(), e);
				}
				str.closeEntry();
			}
			str.close();
		}
	}

}
