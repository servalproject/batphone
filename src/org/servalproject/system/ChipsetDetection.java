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

import android.os.Build;
import android.util.Log;

import org.servalproject.LogActivity;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.shell.CommandLog;
import org.servalproject.shell.Shell;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ChipsetDetection {

	private static final String strAh_on_tag = "#Insert_Adhoc_on";
	private static final String strAh_off_tag = "#Insert_Adhoc_off";

	private static final String TAG = "Chipset";

	private String logFile;
	private String detectPath;
	private String edifyPath;
	private String edifysrcPath;

	private ServalBatPhoneApplication app;
	private Chipset wifichipset;

	private String manufacturer;
	private String brand;
	private String model;
	private String name;

	private ChipsetDetection() {
		this.app = ServalBatPhoneApplication.context;
		this.logFile = app.coretask.DATA_FILE_PATH + "/var/wifidetect.log";
		this.detectPath = app.coretask.DATA_FILE_PATH + "/conf/wifichipsets/";
		this.edifyPath = app.coretask.DATA_FILE_PATH + "/conf/adhoc.edify";
		this.edifysrcPath = app.coretask.DATA_FILE_PATH
				+ "/conf/adhoc.edify.src";

		manufacturer = app.coretask.getProp("ro.product.manufacturer");
		brand = app.coretask.getProp("ro.product.brand");
		model = app.coretask.getProp("ro.product.model");
		name = app.coretask.getProp("ro.product.name");
	}

	private static ChipsetDetection detection;

	public static ChipsetDetection getDetection() {
		if (detection == null)
			detection = new ChipsetDetection();
		return detection;
	}

	private HashMap<String, Boolean> existsTests = new HashMap<String, Boolean>();

	// Check if the corresponding file exists
	private boolean fileExists(String filename) {
		// Check if the specified file exists during wifi chipset detection.
		// Record the result in a dictionary or similar structure so that if
		// we fail to detect a phone, we can create a bundle of information
		// that can be sent back to the Serval Project developers to help them
		// add support for the phone.
		Boolean result = existsTests.get(filename);
		if (result == null) {
			result = (new File(filename)).exists();
			existsTests.put(filename, result);
		}
		return result;
	}

	private boolean isSymLink(File file) throws IOException {
		File p = file.getParentFile();
		File t = p == null ? file : new File(p.getCanonicalFile(), file.getName());
		return !t.getCanonicalFile().equals(t.getAbsoluteFile());
	}

	private void scan(File folder, List<File> results,
			Set<String> insmodCommands) {
		File files[] = folder.listFiles();
		if (files == null)
			return;
		for (File file : files) {
			try {
				if (file.isDirectory()) {
					if (!isSymLink(file))
						scan(file, results, insmodCommands);
				} else {
					String path = file.getCanonicalPath();
					if (path.contains("wifi") || path.endsWith(".ko")) {
						results.add(file);
					}
					// Only look in small files, and stop looking if a file is
					// binary
					if (insmodCommands != null && file.length() < 16384
							&& ((file.getName().endsWith(".so") == false))
							&& ((file.getName().endsWith(".ttf") == false))
							&& ((file.getName().endsWith(".ogg") == false))
							&& ((file.getName().endsWith(".odex") == false))
							&& ((file.getName().endsWith(".apk") == false))) {
						BufferedReader b = new BufferedReader(new FileReader(
								file));
						try {
							String line = null;
							String dmp = null;
							while ((line = b.readLine()) != null) {
								// Stop looking if the line seems to be binary
								if (line.length() > 0
										&& (line.charAt(0) > 0x7d || line
												.charAt(0) < 0x09)) {
									// LogActivity.logMessage("guess", file
									// + " seems to be binary", false);
									break;
								}
								if (line.startsWith("DRIVER_MODULE_PATH="))
									dmp = line.substring(19);
								if (dmp != null
										&& line
												.startsWith("DRIVER_MODULE_ARG=")) {
									insmodCommands.add("insmod " + dmp + " \""
											+ line.substring(18) + "\"");
									dmp = null;
								}
								if (line.contains("insmod ")) {
									// Ooh, an insmod command.
									// Let's see if it is interesting.
									insmodCommands.add(line);
								}
							}
							b.close();
						} catch (IOException e) {
							b.close();
						} finally {
							b.close();
						}
					}
				}
			} catch (IOException e) {
				// ignore
			}
		}
	}

	private List<File> interestingFiles = null;

	private List<File> findModules(Set<String> insmodCommands) {
		if (interestingFiles == null || insmodCommands != null) {
			interestingFiles = new ArrayList<File>();
			scan(new File("/system"), interestingFiles, insmodCommands);
			scan(new File("/lib"), interestingFiles, insmodCommands);
			scan(new File("/wifi"), interestingFiles, insmodCommands);
			scan(new File("/etc"), interestingFiles, insmodCommands);
		}
		return interestingFiles;
	}

	public Chipset getWifiChipset() {
		return wifichipset;
	}

	public String getChipset() {
		if (wifichipset == null)
			return null;
		return wifichipset.chipset;
	}

	public void setChipsetName(String value) {
		setChipset(Chipset.FromFile(new File(detectPath, value + ".detect")));
	}

	public boolean testChipset(Shell shell){
		if (wifichipset.detected)
			return true;
		return testChipset(wifichipset, hasNl80211(shell));
	}

	private boolean testChipset(Chipset chipset, boolean hasNetlink){
		if (chipset.detected)
			return true;
		if (chipset.nl80211 && !hasNetlink)
			return false;
		for(String filename : chipset.mustExist) {
			if (!fileExists(filename))
				return false;
		}
		for(String filename : chipset.mustNotExist) {
			if (fileExists(filename))
				return false;
		}
		if (chipset.androidVersion>0) {
			int sdkVersion = Build.VERSION.SDK_INT;

			if (chipset.androidOperator=="<") {
				if (chipset.androidVersion >= sdkVersion)
					return false;
			}else if (chipset.androidOperator=="<=") {
				if (chipset.androidVersion > sdkVersion)
					return false;
			}else if (chipset.androidOperator==">") {
				if (chipset.androidVersion <= sdkVersion)
					return false;
			}else if (chipset.androidOperator==">=") {
				if (chipset.androidVersion < sdkVersion)
					return false;
			}else if (chipset.androidOperator=="==") {
				if (chipset.androidVersion != sdkVersion)
					return false;
			}else {
				return false;
			}
		}
		if (!chipset.productList.isEmpty()) {
			boolean found = false;
			for (String product : chipset.productList) {
				if (Build.PRODUCT.contains(product)) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		chipset.detected = true;
		return true;
	}

	private void appendFile(FileOutputStream out, String path)
			throws IOException {
		DataInputStream input = new DataInputStream(new FileInputStream(path));
		String strLineinput;
		while ((strLineinput = input.readLine()) != null) {
			out.write((strLineinput + "\n").getBytes());
		}
		input.close();
	}

	private boolean hasNl80211(Shell shell) {
		try {
			CommandLog c = new CommandLog(app.coretask.DATA_FILE_PATH
					+ "/bin/iw list");
			shell.add(c);
			return c.exitCode() == 0;
		}catch (Exception e){
			Log.e(TAG, e.getMessage(), e);
			return false;
		}
	}

	private void inventSupport(Set<Chipset> detected_chipsets, boolean hasNetlink) {
		// Make a wild guess for a script that MIGHT work
		// Start with list of kernel modules
		// XXX we should search for files containing insmod to see if there are
		// any parameters that might be needed (as is the case on the IDEOS
		// U8150)

		Set<String> insmodCommands = new HashSet<String>();

		List<String> knownModules = getList(this.detectPath
				+ "known-wifi.modules");
		List<String> knownNonModules = getList(this.detectPath
				+ "non-wifi.modules");
		List<File> candidatemodules = findModules(insmodCommands);
		List<File> modules = new ArrayList<File>();
		int guesscount = 0;
		// First, let's just try only known modules.
		// XXX - These are the wrong search methods
		for (File module : candidatemodules) {
			if (module.getName().endsWith(".ko"))
				if (!knownNonModules.contains(module.getName()))
					if (knownModules.contains(module.getName()))
						modules.add(module);
		}

		if (modules.isEmpty()) {
			// We didn't find any on our strict traversal, so try again
			// allowing any non-black-listed modules
			for (File module : candidatemodules) {
				if (module.getName().endsWith(".ko"))
					if (!knownNonModules.contains(module.getName()))
						modules.add(module);
			}
		}

		if (modules.isEmpty()) {
			// Blast. Couldn't find any modules.
			// Well, let's just try ifconfig and iwconfig anyway, as they
			// might just work.
		}

		// Now that we have the list of modules, we could have a look to see
		// if there are any sample insmod commands
		// that we can find in any system files for clues on what parameters
		// to pass when loading the module, e.g.,
		// any firmware blobs or nvram.txt or other options.
		// XXX - Rather obviously we have not implemented this yet.

		LogActivity.logErase("guess");

		String profilename = "failed";

		for (File m : modules) {
			String path = m.getPath();
			insmodCommands.add("insmod " + path + " \"\"");

		}

		for (String s : insmodCommands) {
			String module = null;
			String args = null;
			String modname = "noidea";
			int i;

			i = s.indexOf("insmod ");
			if (i == -1)
				continue;
			i += 7;
			module = getNextShellArg(s.substring(i));
			i += module.length() + 1;
			if (i < s.length())
				args = getNextShellArg(s.substring(i));
			else
				args = "\"\"";
			if (args.charAt(0) != '\"')
				args = "\"" + args + "\"";

			modname = module;
			if (modname.lastIndexOf(".") > -1)
				modname = modname.substring(1, modname.lastIndexOf("."));
			if (modname.lastIndexOf("/") > -1)
				modname = modname.substring(1 + modname.lastIndexOf("/"));

			guesscount++;
			profilename = "guess-" + guesscount + "-" + modname + "-"
					+ args.length();

			// The actual edify script consists of the insmod commands
			// Thus this code does not work with unusual chipsets like the
			// tiwlan drivers that use
			// funny configuration commands. Oh well. One day we might add some
			// cleverness for that.

			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(this.detectPath
						+ profilename + ".adhoc.edify", false), 256);

				// Write out edify command to load the module
				writer.write("module_loaded(\"" + modname
						+ "\") || log(insmod(\"" + module + "\"," + args
						+ "),\"Loading " + module + " module\");\n");

				writer.close();
			} catch (IOException e) {
				Log.e(TAG, e.toString(), e);
			}

			// Finally to turn off wifi let's just unload all the modules we
			// loaded earlier.
			// Crude but fast and effective.
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(this.detectPath
						+ profilename + ".off.edify", false), 256);

				// Write out edify command to load the module
				writer.write("module_loaded(\"" + modname + "\") && rmmod(\""
						+ modname + "\");\n");
				writer.close();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}

			// Now write out a detect script for this device.
			// Mark it experimental because we can't be sure that it will be any
			// good. This means that users will have to actively choose it from
			// the
			// wifi settings menu. We could offer it if no non-experimental
			// chipsets match, but that is best done as a general
			// policy in the way the chipset selection works.
			Chipset ret = new Chipset();
			ret.experimental = true;
			ret.adhocOn = profilename + ".adhoc.edify " + profilename + ".edify";
			ret.adhocOff = profilename + ".adhoc.edify " + profilename + ".off.edify";
			ret.interfaceUp = (hasNetlink ? "iw" : "iwconfig") + ".adhoc.edify";
			if (hasNetlink){
				ret.nl80211 = true;
				ret.noWirelessExtensions = true;
			}
			if (module.contains("/"))
				ret.mustExist.add(module);
			try {
				ret.SaveTo(new File(this.detectPath + profilename + ".detect"));
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}

			if (testChipset(ret, hasNetlink))
				detected_chipsets.add(ret);

			LogActivity
					.logMessage("guess", "Created best-guess support scripts "
							+ profilename + " based on kernel module "
							+ modname + ".", false);

		}
	}

	private String getNextShellArg(String s) {
		int i = 0;
		boolean quoteMode = false;
		boolean escMode = false;

		// Skip leading white space
		while (i < s.length() && s.charAt(i) <= ' ')
			i++;
		// Get arg
		while (i < s.length()) {
			if (escMode)
				escMode = false;
			if (quoteMode) {
				if (s.charAt(i) == '"')
					quoteMode = false;
				else if (s.charAt(i) == '\\')
					escMode = true;
			} else if (s.charAt(i) <= ' ') {
				// End of arg
				return s.substring(0, i);
			} else if (s.charAt(i) == '\"')
				quoteMode = true;
			else if (s.charAt(i) == '\\')
				escMode = true;
			i++;
		}
		// No word breaks, so return whole thing
		return s;
	}

	public static List<String> getList(String filename) {
		// Read lines from file into a list
		List<String> l = new ArrayList<String>();
		String line;
		try {
			BufferedReader f = new BufferedReader(new FileReader(filename));
			while ((line = f.readLine()) != null) {
				l.add(line);
			}
			f.close();
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		return l;
	}

	private void logMore() {
		// log other interesting modules/files
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(logFile,
					true), 256);

			writer.write("\nHandset Type;\n");
			writer.write("Manufacturer: " + manufacturer + "\n");
			writer.write("Brand: " + brand + "\n");
			writer.write("Model: " + model + "\n");
			writer.write("Name: " + name + "\n");
			writer.write("Software Version: " + ServalBatPhoneApplication.version + "\n");
			writer.write("Android Version: " + Build.VERSION.RELEASE + " (API "
					+ Build.VERSION.SDK_INT + ")\n");
			writer.write("Kernel Version: " + app.coretask.getKernelVersion()
					+ "\n");

			writer.write("\nInteresting modules;\n");
			for (File path : findModules(null)) {
				writer.write(path.getCanonicalPath() + "\n");
			}
			writer.close();
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}

	public File getAdhocAttemptFile(Chipset chipset) {
		return new File(app.coretask.DATA_FILE_PATH + "/var/attempt_"
				+ chipset.chipset);
	}

	// set chipset configuration
	public void setChipset(Chipset chipset) {
		if (chipset == null) {
			chipset = new Chipset();
			chipset.unknown = true;
		}

		// add support for modes via SDK if available
		if (!chipset.supportedModes.contains(WifiMode.Ap)
				&& WifiApControl.isApSupported())
			chipset.supportedModes.add(WifiMode.Ap);

		if (!chipset.supportedModes.contains(WifiMode.Client))
			chipset.supportedModes.add(WifiMode.Client);
		if (!chipset.supportedModes.contains(WifiMode.Off))
			chipset.supportedModes.add(WifiMode.Off);

		// make sure we have root permission for adhoc support
		if (chipset.supportedModes.contains(WifiMode.Adhoc)) {
			if (getAdhocAttemptFile(chipset).exists()) {
				chipset.supportedModes.remove(WifiMode.Adhoc);
				Log.v(TAG, "Adhoc mode has previously failed and cannot be supported.");
			} else if (!app.coretask.hasRootPermission()) {
				chipset.supportedModes.remove(WifiMode.Adhoc);
				Log.v(TAG, "Unable to support adhoc mode without root permission");
			}
		}

		wifichipset = chipset;

		if (chipset.supportedModes.contains(WifiMode.Adhoc)) {
			try {
				FileOutputStream out = new FileOutputStream(edifyPath);
				FileInputStream fstream = new FileInputStream(edifysrcPath);
				// Get the object of DataInputStream
				DataInputStream in = new DataInputStream(fstream);
				String strLine;
				// Read File Line By Line
				while ((strLine = in.readLine()) != null) {
					if (strLine.startsWith(strAh_on_tag)) {
						if (chipset.adhocOn != null)
							appendFile(out, detectPath + chipset.adhocOn);
						if (chipset.interfaceUp != null)
							appendFile(out, detectPath + chipset.interfaceUp);
					} else if (strLine.startsWith(strAh_off_tag)) {
						if (chipset.adhocOff != null)
							appendFile(out, detectPath + chipset.adhocOff);
					} else
						out.write((strLine + "\n").getBytes());
				}
				in.close();
				out.close();
			} catch (IOException exc) {
				Log.e(TAG, exc.getMessage(),
						exc);
			}
		}
	}

	public boolean isModeSupported(WifiMode mode) {
		if (mode == null)
			return true;
		if (wifichipset == null)
			return false;

		return wifichipset.supportedModes.contains(mode);
	}

	public Set<Chipset> getDetectedChipsets(Shell shell, LogOutput log) {
		log.log("Scanning for known android hardware");

		Set<Chipset> detected_chipsets = new TreeSet<Chipset>();
		boolean hasNetlink = hasNl80211(shell);
		boolean foundSupported = false;

		File detectScripts = new File(detectPath);
		if (!detectScripts.isDirectory())
			return null;

		for (File script : detectScripts.listFiles()) {
			if (!script.getName().endsWith(".detect"))
				continue;

			Chipset c = Chipset.FromFile(script);
			if (testChipset(c, hasNetlink)) {
				detected_chipsets.add(c);
				if (!c.experimental)
					foundSupported = true;
			}
		}

		if (!foundSupported) {
			log.log("Hardware may be unknown, scanning for wifi modules");
			inventSupport(detected_chipsets, hasNetlink);
		}
		return detected_chipsets;
	}

}
