package org.servalproject.system;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

public class Chipset implements Comparable<Chipset> {
	File detectScript;
	public String chipset;
	public Set<WifiMode> supportedModes;
	String adhocOn;
	String adhocOff;
	boolean detected = false;
	boolean experimental = false;
	boolean unknown = false;

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
		return this.chipset.compareToIgnoreCase(another.chipset);
	}
}
