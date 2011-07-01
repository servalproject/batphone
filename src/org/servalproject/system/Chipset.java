package org.servalproject.system;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

public class Chipset {
	File detectScript;
	public String chipset;
	Set<WifiMode> supportedModes;
	String adhocOn;
	String adhocOff;
	boolean detected = false;

	Chipset() {
		chipset = "Unknown";
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
}
