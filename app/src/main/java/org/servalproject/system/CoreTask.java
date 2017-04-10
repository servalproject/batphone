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
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CoreTask {

	public static final String MSG_TAG = "BatPhone";

	public String DATA_FILE_PATH;

	public void setPath(String path) {
		this.DATA_FILE_PATH = path;
	}

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

	public String getKernelVersion() {
		ArrayList<String> lines = readLinesFromFile("/proc/version");
		String version = lines.get(0).split(" ")[2];
		Log.d(MSG_TAG, "Kernel version: " + version);
		return version;
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

	public void killProcesses(Shell shell, File binFolder)
			throws IOException {
		// try to kill running processes by name
		long timeout = SystemClock.elapsedRealtime() + 3000;

		List<Integer> interestingPids = new ArrayList<Integer>();
		String binPath = binFolder.getCanonicalPath();

		for (File process : new File("/proc").listFiles()) {
			try {
				int pid = Integer.parseInt(process.getName());
				String procPath = new File(process, "exe").getCanonicalPath();
				if (procPath.startsWith(binPath)){
					interestingPids.add(pid);
					Log.v(MSG_TAG, "Attempting to kill "+process.getName()+", "+procPath);
				}
			}
			catch (NumberFormatException ex) {}
			catch (IOException ex) {}
		}

		while(!interestingPids.isEmpty() && timeout <= SystemClock.elapsedRealtime()){
			Iterator<Integer> i = interestingPids.listIterator();
			CommandLog lastCmd = null;
			while(i.hasNext()){
				Integer pid = i.next();
				if (!new File("/proc",Integer.toString(pid)).exists()){
					i.remove();
					continue;
				}
				lastCmd = new CommandLog("kill " + pid);
				shell.add(lastCmd);
			}
			try {
				if (lastCmd!=null)
					lastCmd.exitCode();
				Thread.sleep(50);
			} catch (InterruptedException e) {}
		}
	}

	public String getProp(String property) {
		if (this.getProp != null) {
			try {
				return (String) getProp.invoke(null, property);
			} catch (Exception e) {
				Log.e(MSG_TAG, e.toString(), e);
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

	public void extractZip(InputStream asset, File folder, boolean pie) throws IOException {
		Shell shell = new Shell();
		try {
			extractZip(shell, asset, folder, null, pie);
		} finally {
			try {
				shell.waitFor();
			} catch (InterruptedException e) {
				Log.e("CoreTask", e.getMessage(), e);
			}
		}
	}

	public void extractZip(Shell shell, InputStream asset, File folder,
			Set<String> extract, boolean pie)
			throws IOException {

		ZipInputStream str = new ZipInputStream(asset);

		try {
			ZipEntry ent;
			while ((ent = str.getNextEntry()) != null) {
				try {
					String filename = ent.getName();
					String destFilename = filename;
					boolean isPie = filename.endsWith("-PIE");
					boolean isNonPie = filename.endsWith("-NOPIE");

					if (isPie || isNonPie) {
						if (isPie != pie)
							continue;
						destFilename = filename.substring(0,filename.lastIndexOf("-"));
					}

					File file = new File(folder, destFilename);
					if (ent.isDirectory()) {
						if (!file.exists())
							if (!file.mkdirs())
								Log.v("BatPhone", "Failed to create path "
										+ filename);
					} else {
						if (extract == null || extract.contains(filename)) {
							// try to write the file directly
							writeFile(file, str, ent.getTime());

							Log.v(MSG_TAG, "Extracted " + filename);

							if (filename.contains("bin/")
									|| filename.contains("lib/")
									|| filename.contains("libs/")
									|| filename.contains("conf/"))
								shell.add(new CommandLog("chmod 755", file
										.getCanonicalPath()));
						}
					}
				} catch (Exception e) {
					Log.v(MSG_TAG, e.getMessage(), e);
				}finally{
					str.closeEntry();
				}
			}
		} finally {
			str.close();
		}
	}

}
