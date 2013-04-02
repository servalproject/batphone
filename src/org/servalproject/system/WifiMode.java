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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.shell.CommandCapture;
import org.servalproject.shell.Shell;

import android.util.Log;

public enum WifiMode {
	Adhoc(120, "Adhoc"),
	Direct(0, "Wifi-Direct"),
	Client(90, "Client"),
	Ap(45, "Access Point"),
	Off(5 * 60, "Off"),
	Unsupported(0, "Unsupported"),
	Unknown(0, "Unknown");

	int sleepTime;
	String display;

	static {
		System.loadLibrary("iwstatus");
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

	public static String lastIwconfigOutput;

	public static WifiMode getWiFiMode(Shell rootShell, String interfaceName,
			String ipAddr) {
		if (rootShell == null)
			throw new NullPointerException();

		NetworkInterface networkInterface = null;
		lastIwconfigOutput = null;

		try {
			networkInterface = NetworkInterface
					.getByName(interfaceName);

			// interface doesn't exist? must be off.
			if (networkInterface == null)
				return WifiMode.Off;
		} catch (Exception e) {
			Log.e("BatPhone/WifiMode", e.toString(), e);
		}

		if (ChipsetDetection.getDetection().getWifiChipset()
				.lacksWirelessExtensions() && ipAddr != null) {

			// We cannot use iwstatus, so see if our interface/IP is available.
			// IP address is probably the safest option.

			try {
				if (ipAddr.contains("/")) {
					ipAddr = ipAddr.substring(0, ipAddr.indexOf('/'));
				}

				for (Enumeration<InetAddress> enumIpAddress = networkInterface
						.getInetAddresses(); enumIpAddress
						.hasMoreElements();) {
					InetAddress iNetAddress = enumIpAddress.nextElement();
					if (!iNetAddress.isLoopbackAddress()) {
						// Check if this matches
						if (ipAddr.equals(iNetAddress.getHostAddress())) {
							return WifiMode.Unknown;
						}
					}
				}
			} catch (Exception e) {
				Log.e("BatPhone/WifiMode", e.toString(), e);
			}

			return WifiMode.Off;

		} else {
			try {
				// find out what mode the wifi interface is in by asking
				// iwconfig
				// The native iwstatus (iwconfig read-only command) here doesn't
				// work,
				// even though the same code from the same library works from
				// the command
				// line (this is because iwconfig requires root to READ the wifi
				// mode).
				// public static native String iwstatus(String s);
				CoreTask coretask = ServalBatPhoneApplication.context.coretask;
				CommandCapture c = new CommandCapture(
						coretask.DATA_FILE_PATH + "/bin/iwconfig "
								+ interfaceName);
				rootShell.run(c);

				String iw = c.toString();
				lastIwconfigOutput = iw;

				if (iw.contains("Mode:")) {
					// not sure why, but if not run as root, mode is
					// incorrect
					// (this is because iwconfig needs to be run as root to
					// correctly
					// return the wifi mode -- this is probably a linux
					// kernel/wifi
					// driver bug).
					if (rootShell.isRoot) {
						int b = iw.indexOf("Mode:") + 5;
						int e = iw.substring(b).indexOf(" ");
						String mode = iw.substring(b, b + e).toLowerCase();

						if (mode.contains("adhoc")
								|| mode.contains("ad-hoc"))
							return WifiMode.Adhoc;
						if (mode.contains("client")
								|| mode.contains("managed"))
							return WifiMode.Client;
						if (mode.contains("master"))
							return WifiMode.Ap;
					}

					// Found, but unrecognised = unknown
					return WifiMode.Unknown;
				}

				return WifiMode.Off;
			} catch (Exception e) {
				Log.e("WifiMode", e.getMessage(), e);
				return WifiMode.Unknown;
			}
		}
	}
}
