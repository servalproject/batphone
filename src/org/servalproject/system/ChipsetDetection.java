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
	private static final String strMustExist = "exists";
	private static final String strMustNotExist = "missing";
	private static final String strandroid = "androidversion";
	private static final String strCapability = "capability";
	private static final String strExperimental = "experimental";
	private static final String strNoWirelessExtensions = "nowirelessextensions";
	private static final String strNl80211 = "nl80211";
	private static final String strAh_on_tag = "#Insert_Adhoc_on";
	private static final String strAh_off_tag = "#Insert_Adhoc_off";
	private static final String strProduct = "productmatches";
	private static final String strNotProduct = "productisnt";

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

	private void scan(File folder, List<File> results,
			Set<String> insmodCommands) {
		File files[] = folder.listFiles();
		if (files == null)
			return;
		for (File file : files) {
			try {
				if (file.isDirectory()) {
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
				continue;
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

	public Set<Chipset> detected_chipsets = null;

	public Chipset getWifiChipset() {
		return wifichipset;
	}

	public String getChipset() {
		if (wifichipset == null)
			return null;
		return wifichipset.chipset;
	}

	public boolean testAndSetChipset(String value) {
		File script = new File(detectPath, value + ".detect");
		if (script.exists()) {
			Chipset c = new Chipset(script);
			if (testForChipset(c)) {
				setChipset(c);
				return true;
			}
		}
		return false;
	}

	/* Check if the chipset matches with the available chipsets */
	private boolean testForChipset(Chipset chipset) {
		// Read
		// /data/data/org.servalproject/conf/wifichipsets/"+chipset+".detect"
		// and see if we can meet the criteria.
		// This method needs to interpret the lines of that file as test
		// instructions
		// that can do everything that the old big hairy if()else() chain did.
		// This largely consists of testing for the existence of files.

		// use fileExists() to test for the existence of files so that we can
		// generate
		// a report for this phone in case it is not supported.

		// XXX Stub}
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(logFile,
					true), 256);

			writer.write("trying " + chipset + "\n");

			boolean reject = false;
			int matches = 0;
			chipset.supportedModes.clear();
			chipset.detected = false;
			chipset.experimental = false;

			try {
				FileInputStream fstream = new FileInputStream(
						chipset.detectScript);
				// Get the object of DataInputStream
				DataInputStream in = new DataInputStream(fstream);
				String strLine;
				// Read File Line By Line
				while ((strLine = in.readLine()) != null) {
					if (strLine.startsWith("#") || strLine.equals(""))
						continue;

					writer.write("# " + strLine + "\n");
					String arChipset[] = strLine.split(" ");

					if (arChipset[0].equals(strCapability)) {
						for (String mode : arChipset[1].split(",")) {
							try {
								WifiMode m = WifiMode.valueOf(mode);
								if (m != null)
									chipset.supportedModes.add(m);
							} catch (IllegalArgumentException e) {
							}
						}
						if (arChipset.length >= 3)
							chipset.adhocOn = arChipset[2];
						if (arChipset.length >= 4)
							chipset.adhocOff = arChipset[3];
						if (arChipset.length >= 5)
							chipset.interfaceUp = arChipset[4];
					} else if (arChipset[0].equals(strExperimental)) {
						chipset.experimental = true;
					} else if (arChipset[0].equals(strNoWirelessExtensions)) {
						chipset.noWirelessExtensions = true;
					} else if (arChipset[0].equals(strNl80211)) {
						chipset.nl80211 = true;
					} else {

						boolean lineMatch = false;

						if (arChipset[0].equals(strMustExist)
								|| arChipset[0].equals(strMustNotExist)) {
							boolean exist = fileExists(arChipset[1]);
							boolean wanted = arChipset[0].equals(strMustExist);
							writer.write((exist ? "exists" : "missing") + " "
									+ arChipset[1] + "\n");
							lineMatch = (exist == wanted);
						} else if (arChipset[0].equals(strandroid)) {
							int sdkVersion = Build.VERSION.SDK_INT;
							writer.write(strandroid + " = "
									+ Build.VERSION.SDK_INT + "\n");
							int requestedVersion = Integer
									.parseInt(arChipset[2]);

							if (arChipset[1].indexOf('!') >= 0) {
								lineMatch = (sdkVersion != requestedVersion);
							} else
								lineMatch = ((arChipset[1].indexOf('=') >= 0 && sdkVersion == requestedVersion)
										|| (arChipset[1].indexOf('<') >= 0 && sdkVersion < requestedVersion) || (arChipset[1]
										.indexOf('>') >= 0 && sdkVersion > requestedVersion));
						} else if (arChipset[0].equals(strProduct)) {
							writer.write(strProduct + " = "
									+ Build.PRODUCT + "\n");
							lineMatch = false;
							for (int i = 2; i < arChipset.length; i++)
								if (Build.PRODUCT.contains(arChipset[i]))
									lineMatch = true;
						} else if (arChipset[0].equals(strNotProduct)) {
							lineMatch = true;
							for (int i = 2; i < arChipset.length; i++)
								if (Build.PRODUCT.contains(arChipset[i]))
									lineMatch = false;
						} else {
							Log.v("BatPhone", "Unhandled line in " + chipset
									+ " detect script " + strLine);
							continue;
						}

						if (lineMatch)
							matches++;
						else
							reject = true;
					}
				}

				in.close();

			} catch (IOException e) {
				Log.i("BatPhone", e.toString(), e);
				writer.write("Exception Caught in testForChipset" + e + "\n");
				reject = true;
			}

			if (reject)
				writer.write("isnot " + chipset + "\n");
			else {
				chipset.detected = true;
				Log.i("BatPhone", "identified chipset " + chipset
						+ (chipset.experimental ? " (experimental)" : ""));
				writer.write("is " + chipset + "\n");
				LogActivity.logMessage("detect",
						"Detected this handset as a " + chipset, false);
			}
			writer.close();
			return !reject;
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
			return false;
		}
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

	private static int nl80211 = 0;

	public boolean hasNl80211() {
		if (nl80211 == 0) {
			try {
				CommandLog c = new CommandLog(app.coretask.DATA_FILE_PATH
						+ "/bin/iw list");
				Shell shell = new Shell();
				try {
					shell.add(c);
					if (c.exitCode() == 0)
						nl80211 = 1;
					else
						nl80211 = -1;
				} finally {
					shell.waitFor();
				}
			} catch (Exception e) {
				Log.e("ChipsetDetection", e.getMessage(), e);
			}
		}
		return nl80211 == 1;
	}

	public void inventSupport() {
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
		boolean nl80211Support = hasNl80211();
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

			// Now write out a detect script for this device.
			// Mark it experimental because we can't be sure that it will be any
			// good. This means that users will have to actively choose it from
			// the
			// wifi settings menu. We could offer it if no non-experimental
			// chipsets match, but that is best done as a general
			// policy in the way the chipset selection works.
			BufferedWriter writer;
			File detectFile = new File(this.detectPath
					+ profilename + ".detect");
			try {
				writer = new BufferedWriter(new FileWriter(detectFile, false),
						256);
				writer.write(strCapability + " Adhoc " + profilename
						+ ".adhoc.edify " + profilename + ".off.edify " +
						(nl80211Support ? "iw" : "iwconfig") + ".adhoc.edify\n");
				writer.write(strExperimental + "\n");
				if (nl80211Support)
					writer.write(strNl80211 + "\n" +
							strNoWirelessExtensions + "\n");
				if (module.contains("/")) {
					// XXX We have a problem if we don't know the full path to
					// the module
					// for ensuring specificity for choosing this option.
					// Will think about a nice solution later.
					writer.write("exists " + module + "\n");
				}
				writer.close();
			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}

			// The actual edify script consists of the insmod commands
			// Thus this code does not work with unusual chipsets like the
			// tiwlan drivers that use
			// funny configuration commands. Oh well. One day we might add some
			// cleverness for that.

			try {
				writer = new BufferedWriter(new FileWriter(this.detectPath
						+ profilename + ".adhoc.edify", false), 256);

				// Write out edify command to load the module
				writer.write("module_loaded(\"" + modname
						+ "\") || log(insmod(\"" + module + "\"," + args
						+ "),\"Loading " + module + " module\");\n");

				writer.close();
			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}

			// Finally to turn off wifi let's just unload all the modules we
			// loaded earlier.
			// Crude but fast and effective.
			try {
				writer = new BufferedWriter(new FileWriter(this.detectPath
						+ profilename + ".off.edify", false), 256);

				// Write out edify command to load the module
				writer.write("module_loaded(\"" + modname + "\") && rmmod(\""
						+ modname + "\");\n");
				writer.close();
			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
			}
			Chipset c = new Chipset(detectFile);
			if (this.testForChipset(c))
				this.detected_chipsets.add(c);
			LogActivity
					.logMessage("guess", "Creating best-guess support scripts "
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

			if (detected_chipsets == null || detected_chipsets.size() == 0)
				chipset.chipset = "Unsupported - " + brand + " " + model + " "
						+ name;

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
				Log.v("BatPhone",
						"Adhoc mode has previously failed and cannot be supported.");
			} else if (!app.coretask.hasRootPermission()) {
				chipset.supportedModes.remove(WifiMode.Adhoc);
				Log.v("BatPhone",
						"Unable to support adhoc mode without root permission");
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
				Log.e("Exception caught at set_Adhoc_mode", exc.getMessage(),
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

	public Set<Chipset> getDetectedChipsets() {
		if (detected_chipsets == null) {
			detected_chipsets = new TreeSet<Chipset>();

			File detectScripts = new File(detectPath);
			if (!detectScripts.isDirectory())
				return null;

			for (File script : detectScripts.listFiles()) {
				if (!script.getName().endsWith(".detect"))
					continue;
				Chipset c = new Chipset(script);
				if (testForChipset(c)) {
					detected_chipsets.add(c);
				}
			}
		}
		return detected_chipsets;
	}

}
