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

import java.io.FileInputStream;
import java.io.InputStream;

import org.servalproject.ServalBatPhoneApplication;

public enum WifiMode {
	Adhoc(120, "Adhoc"), Client(90, "Client"), Ap(45, "Access Point"), Off(
			5 * 60, "Off"), Unknown(0, "Unknown");

	int sleepTime;
	String display;

	static {
		System.loadLibrary("iwstatus");
	}

	// The native iwstatus (iwconfig read-only command) here doesn't work,
	// even though the same code from the same library works from the command
	// line.
	// public static native String iwstatus(String s);
	public static String iwstatus() {


		// Run /data/data/org.servalproject/bin/iwconfig and capture output to a
		// file,
		// and read it in.
		CoreTask coretask = ServalBatPhoneApplication.context.coretask;
		try {
			coretask
					.runRootCommand(
							"/system/bin/rm /data/data/org.servalproject/var/iwconfig.out ; /data/data/org.servalproject/bin/iwconfig > /data/data/org.servalproject/var/iwconfig.out",
							true /* do wait */
					);
			InputStream in = new FileInputStream(
					"/data/data/org.servalproject/var/iwconfig.out");
			int byteCount = in.available();
			byte[] buffer = new byte[byteCount];
			// read the text file as a stream, into the buffer
			in.read(buffer);
			in.close();
			return new String(buffer);
		} catch (Exception e) {
			return "";
		}
	}

	public static native String ifstatus(String s);

	WifiMode(int sleepTime, String display) {
		this.sleepTime = sleepTime;
		this.display = display;
	}

	public String getDisplay() {
		return display;
	}

	private static WifiMode values[] = WifiMode.values();

	public static WifiMode nextMode(WifiMode m) {
		// return the next wifi mode
		if (m == null || m.ordinal() + 1 == values.length)
			return values[0];

		return values[m.ordinal() + 1];
	}

	public static WifiMode getWiFiMode() {
		// find out what mode the wifi interface is in by asking iwconfig
		String iw = iwstatus();
		if (iw.contains("Mode:")) {
			int b = iw.indexOf("Mode:") + 5;
			int e = iw.substring(b).indexOf(" ");
			String mode = iw.substring(b, b + e);

			if (mode.toLowerCase().contains("adhoc"))
				return WifiMode.Adhoc;
			if (mode.toLowerCase().contains("ad-hoc"))
				return WifiMode.Adhoc;
			if (mode.toLowerCase().contains("client"))
				return WifiMode.Client;
			if (mode.toLowerCase().contains("managed"))
				return WifiMode.Client;
			if (mode.toLowerCase().contains("master"))
				return WifiMode.Ap;

			// Found, but unrecognised = unknown
			return WifiMode.Unknown;
		}

		// Not found, so off
		return WifiMode.Off;
	}
}
