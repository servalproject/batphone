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

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

public class Chipset implements Comparable<Chipset> {
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

	Chipset() {
		supportedModes = EnumSet.noneOf(WifiMode.class);
	}

	Chipset(File detectScript) {
		this.detectScript = detectScript;
		String filename = detectScript.getName();
		this.chipset = filename.substring(0, filename.lastIndexOf('.'));
		supportedModes = EnumSet.noneOf(WifiMode.class);
	}

	@Override
	public String toString() {
		return chipset;
	}

	@Override
	public int compareTo(Chipset another) {
		if (this.experimental != another.experimental)
			return this.experimental ? 1 : -1;
		return this.chipset.compareToIgnoreCase(another.chipset);
	}

	public boolean isExperimental() {
		return experimental;
	}

	public boolean lacksWirelessExtensions() {
		return noWirelessExtensions;
	}
}
