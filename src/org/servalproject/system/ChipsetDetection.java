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

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.WifiApControl;

import android.os.Build;
import android.util.Log;

public class ChipsetDetection {
	private static final String strMustExist = "exists";
	private static final String strMustNotExist = "missing";
	private static final String strandroid = "androidversion";
	private static final String strCapability = "capability";
	private static final String strExperimental = "experimental";
	private static final String strAh_on_tag = "#Insert_Adhoc_on";
	private static final String strAh_off_tag = "#Insert_Adhoc_off";

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

		if (app.getState() != State.Installing) {
			boolean detected = false;
			try {
				String hardwareFile = app.coretask.DATA_FILE_PATH
						+ "/var/hardware.identity";
				DataInputStream in = new DataInputStream(new FileInputStream(
						hardwareFile));
				String chipsetName = in.readLine();
				in.close();
				if (chipsetName != null) {
					// read the detect script again to make sure we have the
					// right supported modes etc.
					Chipset chipset = new Chipset(new File(detectPath
							+ chipsetName + ".detect"));
					detected = testForChipset(chipset);
					if (detected)
						setChipset(chipset);
				}
			} catch (Exception e) {
				Log.v("BatPhone", edifyPath.toString(), e);
			}
			if (!detected)
				this.setUnknownChipset();
		}
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

	public Set<Chipset> getChipsets() {
		Set<Chipset> chipsets = new TreeSet<Chipset>();

		File detectScripts = new File(detectPath);
		if (!detectScripts.isDirectory())
			return null;

		for (File script : detectScripts.listFiles()) {
			if (!script.getName().endsWith(".detect"))
				continue;
			chipsets.add(new Chipset(script));
		}
		return chipsets;
	}

	private void scan(File folder, List<String> results) {
		File files[] = folder.listFiles();
		if (files == null)
			return;
		for (File file : files) {
			try {
				if (file.isDirectory()) {
					scan(file, results);
				} else {
					String path = file.getCanonicalPath();
					if (path.contains("wifi") || path.endsWith(".ko")) {
						results.add(path);
					}
				}
			} catch (IOException e) {
				continue;
			}
		}
	}

	private List<String> findModules() {
		List<String> results = new ArrayList<String>();
		scan(new File("/system"), results);
		scan(new File("/lib"), results);
		scan(new File("/wifi"), results);
		scan(new File("/etc"), results);
		return results;
	}

	private final static String BASE_URL = "http://developer.servalproject.org/";

	private boolean downloadIfModified(String url, File destination)
			throws IOException {

		HttpClient httpClient = new DefaultHttpClient();
		HttpContext httpContext = new BasicHttpContext();
		HttpGet httpGet = new HttpGet(url);
		if (destination.exists()) {
			httpGet.addHeader("If-Modified-Since",
					DateUtils.formatDate(new Date(destination.lastModified())));
		}

		try {
			Log.v("BatPhone", "Fetching: " + url);
			HttpResponse response = httpClient.execute(httpGet, httpContext);
			int code = response.getStatusLine().getStatusCode();
			switch (code - code % 100) {
			case 200:
				HttpEntity entity = response.getEntity();
				FileOutputStream output = new FileOutputStream(destination);
				entity.writeTo(output);
				output.close();

				Header modifiedHeader = response
						.getFirstHeader("Last-Modified");
				if (modifiedHeader != null) {
					try {
						destination.setLastModified(DateUtils.parseDate(
								modifiedHeader.getValue()).getTime());
					} catch (DateParseException e) {
						Log.v("BatPhone", e.toString(), e);
					}
				}
				Log.v("BatPhone", "Saved to " + destination);
				return true;
			case 300:
				Log.v("BatPhone", "Not Changed");
				// not changed
				return false;
			default:
				throw new IOException(response.getStatusLine().toString());
			}
		} catch (ClientProtocolException e) {
			throw new IOException(e.toString());
		}
	}

	public boolean downloadNewScripts() {
		try {
			File f = new File(app.coretask.DATA_FILE_PATH + "/conf/chipset.zip");
			if (this.downloadIfModified(BASE_URL + "chipset.zip", f)) {
				Log.v("BatPhone", "Extracting archive");
				app.coretask.extractZip(new FileInputStream(f),
						app.coretask.DATA_FILE_PATH + "/conf/wifichipsets");
				return true;
			}
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		return false;
	}

	private String getUrl(URL url) throws IOException {
		Log.v("BatPhone", "Fetching " + url);
		URLConnection conn = url.openConnection();
		InputStream in = conn.getInputStream();
		return new Scanner(in).useDelimiter("\\A").next();
	}

	private String uploadFile(File f, String name, URL url) throws IOException {
		final String boundary = "*****";

		Log.v("BatPhone", "Uploading file " + f.getName() + " to " + url);

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		conn.setDoInput(true);
		conn.setDoOutput(true);
		conn.setUseCaches(false);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Connection", "Keep-Alive");
		conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="
				+ boundary);

		DataOutputStream out = new DataOutputStream(conn.getOutputStream());
		out.writeBytes("--" + boundary + "\n");
		out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\""
				+ name + "\"\nContent-Type: text/plain\n\n");
		{
			FileInputStream in = new FileInputStream(f);
			try {
				byte buff[] = new byte[4 * 1024];
				int read;
				while ((read = in.read(buff)) > 0) {
					out.write(buff, 0, read);
				}
			} finally {
				in.close();
			}
			out.writeBytes("\n--" + boundary + "\n");
			out.flush();
		}

		InputStream in = conn.getInputStream();
		return new Scanner(in).useDelimiter("\\A").next();
	}

	public boolean uploadLog() {
		try {
			String logName = manufacturer + "_" + brand + "_" + model + "_"
					+ name;

			String result = getUrl(new URL(BASE_URL
					+ "upload_v1_exists.php?name=" + logName));
			Log.v("BatPhone", result);
			if (result.equals("Ok.")) {
				result = uploadFile(new File(this.logFile), logName, new URL(
						BASE_URL + "upload_v1_log.php"));
				Log.v("BatPhone", result);
			}
			return true;
		} catch (Exception e) {
			Log.e("BatPhone", e.toString(), e);
			return false;
		}
	}

	/* Function to identify the chipset and log the result */
	public String identifyChipset() {
		int count = 0;
		Chipset detected = null;
		do{
			// start a new log file
			new File(logFile).delete();

			count = 0;
			for (Chipset chipset : getChipsets()) {
				// skip experimental chipset
				if (testForChipset(chipset) && !chipset.experimental) {
					detected = chipset;
					count++;
				}
			}

			if (count==1) break;

			if (!downloadNewScripts()) {
				logMore();
				uploadLog();
				break;
			}

		} while (true);

		if (count != 1) {
			setUnknownChipset();
		} else {
			setChipset(detected);
		}
		return wifichipset.chipset;
	}

	public Chipset getWifiChipset() {
		return wifichipset;
	}

	public String getChipset() {
		if (wifichipset == null)
			return null;
		return wifichipset.chipset;
	}

	/* Check if the chipset matches with the available chipsets */
	public boolean testForChipset(Chipset chipset) {
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
					} else if (arChipset[0].equals(strExperimental)) {
						chipset.experimental = true;
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

				if (matches < 1)
					reject = true;

				// Return our final verdict
				if (!reject) {
					Log.i("BatPhone", "identified chipset " + chipset
							+ (chipset.experimental ? " (experimental)" : ""));
					writer.write("is " + chipset + "\n");
					chipset.detected = true;
				}

			} catch (IOException e) {
				Log.i("BatPhone", e.toString(), e);
				writer.write("Exception Caught in testForChipset" + e + "\n");
				reject = true;
			}

			writer.write("isnot " + chipset + "\n");

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

	private void setUnknownChipset() {
		Chipset unknownChipset = new Chipset();

		unknownChipset.chipset = "Unsupported - " + brand + " " + model + " "
				+ name;
		unknownChipset.unknown = true;
		setChipset(unknownChipset);
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
			writer.write("Software Version: " + app.getVersionName() + "\n");
			writer.write("Android Version: " + Build.VERSION.RELEASE + " (API "
					+ Build.VERSION.SDK_INT + ")\n");
			writer.write("Kernel Version: " + app.coretask.getKernelVersion()
					+ "\n");

			writer.write("\nInteresting modules;\n");
			for (String path : findModules()) {
				writer.write(path + "\n");
			}
			writer.close();
		} catch (IOException e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}

	// set chipset configuration
	public void setChipset(Chipset chipset) {

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
			if (!app.coretask.hasRootPermission()) {
				chipset.supportedModes.remove(WifiMode.Adhoc);
				Log.v("BatPhone",
						"Unable to support adhoc mode without root permission");
			}
		}

		wifichipset = chipset;

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
				} else if (strLine.startsWith(strAh_off_tag)) {
					if (chipset.adhocOff != null)
						appendFile(out, detectPath + chipset.adhocOff);
				} else
					out.write((strLine + "\n").getBytes());
			}
			in.close();
			out.close();
		} catch (IOException exc) {
			Log.e("Exception caught at set_Adhoc_mode", exc.toString(), exc);
		}

		File identity = new File(app.coretask.DATA_FILE_PATH
				+ "/var/hardware.identity");
		if (wifichipset.unknown) {
			identity.delete();
		} else {
			// write out the detected chipset
			try {
				FileOutputStream out = new FileOutputStream(identity);
				out.write(wifichipset.chipset.getBytes());
				out.close();
			} catch (IOException e) {
				Log.e("BatPhone", e.toString(), e);
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

}
