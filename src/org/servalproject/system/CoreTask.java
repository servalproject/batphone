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

import android.content.SharedPreferences.Editor;
import android.os.SystemClock;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.shell.CommandLog;
import org.servalproject.shell.Shell;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.EOFException;
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
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

	private static final int ROOT_NOT_ALLOWED = -1;
	private static final int ROOT_UNKNOWN = 0;
	private static final int ROOT_ALLOWED = 1;
	private static final int ROOT_RETEST = 2;

	public void rootTested(boolean success) {
		Editor ed = ServalBatPhoneApplication.context.settings.edit();
		ed.putInt("has_root", success ? ROOT_ALLOWED : ROOT_NOT_ALLOWED);
		ed.commit();
	}

	public boolean hasRootPermission() {
		return ServalBatPhoneApplication.context.settings.getInt(
				"has_root", ROOT_UNKNOWN) == ROOT_ALLOWED;
	}

	public void killProcess(Shell shell, String processName)
			throws IOException {
		// try to kill running processes by name
		int pid, lastPid = -1;
		long timeout = SystemClock.elapsedRealtime() + 3000;
		while ((pid = getPid(processName)) >= 0) {
			if (timeout <= SystemClock.elapsedRealtime()) {
				Log.v("BatPhone", "Giving up");
				break;
			}
			if (pid != lastPid) {
				try {
					Log.v("BatPhone", "Killing " + processName + " pid "
							+ pid);
					CommandLog c = new CommandLog("kill " + pid);
					shell.add(c);
					c.exitCode();
				} catch (Exception e) {
					Log.v("BatPhone", "kill failed", e);
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

	public void writeFile(File outFile, InputStream str, long modified)
			throws IOException {
		outFile.getParentFile().mkdirs();
		// Remove file before writing, in case it is an executable file with a
		// running process (eg dna if wifi was left on when reinstalling, which
		// will probably always be the case for Rhizome-initiated updates)
		outFile.delete();
		BufferedOutputStream out = new BufferedOutputStream(
				new FileOutputStream(outFile), 8 * 1024);
		int len;
		byte buff[] = new byte[1024];
		while ((len = str.read(buff)) > 0) {
			out.write(buff, 0, len);
		}
		out.close();

		if (modified != 0)
			outFile.setLastModified(modified);
	}

	public String readLine(InputStream in) throws IOException {
		byte buff[] = new byte[1024];
		int offset = 0;
		while (true) {
			int value = in.read();
			if (value == -1) {
				if (offset > 0)
					break;
				throw new EOFException();
			}
			if (value == '\n')
				break;
			if (offset >= buff.length)
				break;
			buff[offset++] = (byte) value;
		}
		return new String(buff, 0, offset);
	}

	public String readToEnd(InputStream in) throws IOException {
		StringBuilder sb = new StringBuilder();
		byte buff[] = new byte[1024];
		int read;
		while ((read = in.read(buff)) >= 0) {
			sb.append(new String(buff, 0, read));
		}
		return sb.toString();
	}

	public void extractZip(InputStream asset, File folder) throws IOException {
		Shell shell = new Shell();
		try {
			extractZip(shell, asset, folder, null);
		} finally {
			try {
				shell.waitFor();
			} catch (InterruptedException e) {
				Log.e("CoreTask", e.getMessage(), e);
			}
		}
	}

	public void extractZip(Shell shell, InputStream asset, File folder,
			Set<String> extract)
			throws IOException {

		ZipInputStream str = new ZipInputStream(asset);

		try {
			ZipEntry ent;
			while ((ent = str.getNextEntry()) != null) {
				try {
					String filename = ent.getName();
					File file = new File(folder, filename);
					if (ent.isDirectory()) {
						if (!file.exists())
							if (!file.mkdirs())
								Log.v("BatPhone", "Failed to create path "
										+ filename);
					} else {
						if (extract == null || extract.contains(filename)) {
							// try to write the file directly
							writeFile(file, str, ent.getTime());

							if (filename.indexOf("bin/") >= 0
									|| filename.indexOf("lib/") >= 0
									|| filename.indexOf("libs/") >= 0
									|| filename.indexOf("conf/") >= 0)
								shell.add(new CommandLog("chmod 755", file
										.getCanonicalPath()));
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
