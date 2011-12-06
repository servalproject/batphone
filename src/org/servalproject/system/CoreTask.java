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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.util.Log;

public class CoreTask {

	public static final String MSG_TAG = "BatPhone";

	public String DATA_FILE_PATH;

	private Hashtable<String, String> runningProcesses = new Hashtable<String, String>();

	public void setPath(String path) {
		this.DATA_FILE_PATH = path;
	}

	private Object systemProperties;
	private Method getProp;

	public CoreTask() {
		try {
			ClassLoader c = CoreTask.class.getClassLoader();
			Class<?> cls = c.loadClass("android.os.SystemProperties");
			getProp = cls.getMethod("get", String.class);
		} catch (Exception e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}

	public boolean chmod(String file, String mode) {
		try {
			if (runCommand("chmod " + mode + " " + file) == 0) {
				return true;
			}
		} catch (Exception e) {
		}
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
			while ((line = br.readLine()) != null) {
				lines.add(line.trim());
			}
		} catch (Exception e) {
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "
					+ e.getMessage());
		} finally {
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
		Log.d(MSG_TAG, "Writing " + lines.length() + " bytes to file: "
				+ filename);
		try {
			out = new FileOutputStream(filename);
			out.write(lines.getBytes());
			out.flush();
		} catch (Exception e) {
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "
					+ e.getMessage());
		} finally {
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
	private String suLocation;

	public boolean testRootPermission() {
		try {
			suLocation = "/system/bin/su";
			File su = new File(suLocation);
			if (!su.exists()) {
				suLocation = "/system/xbin/su";
				File su2 = new File(suLocation);
				if (!su2.exists())
					throw new IOException("Su not found");
			}

			// run an empty command until it succeeds, it should only fail if
			// the user fails to accept the su prompt or permission was denied
			long now = System.currentTimeMillis();
			int retries = 10;
			while (runRootCommand("") != 0) {
				long then = System.currentTimeMillis();
				if (then - now < 5000) {
					Log.v("Batphone", "Root access failed too quickly?");
					retries--;
					if (retries <= 0)
						throw new IOException(
								"Permission denied too many times");
				}
				now = then;
			}
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

	// TODO: better exception type?
	public int runRootCommand(String command) throws IOException {
		return runCommandForOutput(true, true, command, null);
	}

	public int runCommand(String command) throws IOException {
		return runCommandForOutput(false, true, command, null);
	}

	public int runCommandForOutput(boolean root, boolean wait, String command,
			StringBuilder out) throws IOException {

		if (root) {
			if (!"".equals(command) && !hasRootPermission())
				throw new IOException("Permission denied");

			this.writeLinesToFile(DATA_FILE_PATH + "/sucmd",
					"#!/system/bin/sh\n" + command);
			this.chmod(DATA_FILE_PATH + "/sucmd", "755");
			command = DATA_FILE_PATH + "/sucmd";
		}

		Process proc;
		ProcessBuilder pb = new ProcessBuilder();
		pb.command((root ? suLocation : "/system/bin/sh"), "-c", command);
		pb.redirectErrorStream(true);
		proc = pb.start();

		if (!wait)
			return 0;

		BufferedReader stdOut = new BufferedReader(new InputStreamReader(
				proc.getInputStream()), 256);

		try {
			String line = null;
			while ((line = stdOut.readLine()) != null) {
				if (out != null)
					out.append(line).append('\n');
				else
					Log.v(MSG_TAG, line);
			}
		} finally {
			stdOut.close();
		}

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

	public void killProcess(String processName, boolean root)
			throws IOException {
		// try to kill running processes by name
		int pid, lastPid = -1;
		while ((pid = getPid(processName)) >= 0) {
			if (pid != lastPid) {
				try {
					Log.v("BatPhone", "Killing " + processName + " pid " + pid);
					runCommandForOutput(root, true, "kill " + pid, null);
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
		if (this.getProp != null) {
			try {
				return (String) getProp.invoke(systemProperties, property);
			} catch (Exception e) {
				Log.e("BatPhone", e.toString(), e);
			}
		}
		return null;
	}

	public long[] getDataTraffic(String device) {
		// Returns traffic usage for all interfaces starting with 'device'.
		long[] dataCount = new long[] { 0, 0 };
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
		extractZip(asset, folder, null);
	}

	public void extractZip(InputStream asset, String folder,
			Map<String, Character> extract) throws IOException {
		ZipInputStream str = new ZipInputStream(asset);
		try {
			ZipEntry ent;
			while ((ent = str.getNextEntry()) != null) {
				try {
					String filename = ent.getName();
					if (ent.isDirectory()) {
						File dir = new File(folder + "/" + filename + "/");
						if (!dir.exists())
							if (!dir.mkdirs())
								Log.v("BatPhone", "Failed to create path "
										+ filename);
					} else {
						if (extract == null || extract.get(filename) != null) {
							// try to write the file directly
							writeFile(folder + "/" + filename, str, ent
									.getTime());

							if (filename.indexOf("bin/") >= 0
									|| filename.indexOf("lib/") >= 0
									|| filename.indexOf("libs/") >= 0
									|| filename.indexOf("conf/") >= 0)
								runCommand("chmod 755 " + folder + "/"
										+ filename);
							if (extract != null)
								Log.v("BatPhone", "Extracted " + filename);
						}
					}
				} catch (Exception e) {
					Log.v("BatPhone", e.toString(), e);
				}
				str.closeEntry();
			}
		} finally {
			str.close();
		}
	}

}
