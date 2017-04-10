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

import android.util.Log;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class Chipset implements Comparable<Chipset> {
	private static final String strMustExist = "exists";
	private static final String strMustNotExist = "missing";
	private static final String strandroid = "androidversion";
	private static final String strCapability = "capability";
	private static final String strExperimental = "experimental";
	private static final String strNoWirelessExtensions = "nowirelessextensions";
	private static final String strNl80211 = "nl80211";
	private static final String strProduct = "productmatches";
	private static final String TAG = "Chipset";

	File detectScript;
	public String chipset;
	public Set<WifiMode> supportedModes;
	String adhocOn;
	String interfaceUp;
	String adhocOff;
	boolean detected = false;
	boolean experimental = false;
	public boolean unknown = false;
	boolean noWirelessExtensions = false;
	boolean nl80211 = false;

	List<String> mustExist = new ArrayList<String>();
	List<String> mustNotExist = new ArrayList<String>();
	List<String> productList = new ArrayList<String>();

	int androidVersion =-1;
	String androidOperator = "";

	Chipset() {
		supportedModes = EnumSet.noneOf(WifiMode.class);
	}

	private Chipset(File detectScript) {
		this.detectScript = detectScript;
		String filename = detectScript.getName();
		this.chipset = filename.substring(0, filename.lastIndexOf('.'));
		supportedModes = EnumSet.noneOf(WifiMode.class);
	}

	public void SaveTo(File detectScript) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(detectScript, false),
				256);
		writer.write(strCapability + " Adhoc " + this.adhocOn + " " + this.adhocOff + " " + this.interfaceUp + "\n");
		if (experimental)
			writer.write(strExperimental + "\n");
		if (nl80211)
			writer.write(strNl80211 + "\n");
		if (noWirelessExtensions)
			writer.write(strNoWirelessExtensions + "\n");
		for (String exist : mustExist)
			writer.write(strMustExist + " " + exist + "\n");
		for (String notExist : mustNotExist)
			writer.write(strMustNotExist + " " + notExist + "\n");
		writer.close();
		this.detectScript = detectScript;
	}

	public static Chipset FromFile(File detectScript){
		try {
			if (!detectScript.exists())
				return null;

			Chipset chipset = new Chipset(detectScript);

			chipset.supportedModes.clear();
			chipset.detected = false;
			chipset.experimental = false;

			FileInputStream fstream = new FileInputStream(
					chipset.detectScript);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			String strLine;
			// Read File Line By Line
			while ((strLine = in.readLine()) != null) {
				if (strLine.startsWith("#") || strLine.equals(""))
					continue;

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
				} else if (arChipset[0].equals(strMustExist)) {
					chipset.mustExist.add(arChipset[1]);
				} else if (arChipset[0].equals(strMustNotExist)){
					chipset.mustNotExist.add(arChipset[1]);
				}else if (arChipset[0].equals(strandroid)) {
					chipset.androidVersion = Integer.parseInt(arChipset[2]);
					chipset.androidOperator = arChipset[1];
				}else if (arChipset[0].equals(strProduct)) {
					for (int i = 2; i < arChipset.length; i++)
						chipset.productList.add(arChipset[i]);
				} else {
					Log.v(TAG, "Unhandled line in " + chipset
							+ " detect script " + strLine);
				}
			}

			in.close();

			return chipset;
		} catch (IOException e) {
			Log.e(TAG, e.toString(), e);
		}
		return null;
	}

	@Override
	public String toString() {
		return chipset;
	}

	@Override
	public int compareTo(Chipset another) {
		if (this.experimental != another.experimental)
			return this.experimental ? 1 : -1;
		if (this.chipset==null && another.chipset==null)
			return 0;
		if (this.chipset==null)
			return 1;
		if (another.chipset==null)
			return -1;
		return this.chipset.compareToIgnoreCase(another.chipset);
	}

	public boolean isExperimental() {
		return experimental;
	}

	public boolean lacksWirelessExtensions() {
		return noWirelessExtensions;
	}
}
