package org.servalproject.system;

public enum WifiMode {
	Adhoc(120), Client(90), Ap(45), Off(5 * 60);

	int sleepTime;

	WifiMode(int sleepTime) {
		this.sleepTime = sleepTime;
	}

	private static WifiMode values[] = WifiMode.values();

	public static WifiMode nextMode(WifiMode m) {
		// return the next wifi mode
		if (m == null || m.ordinal() + 1 == values.length)
			return values[0];

		return values[m.ordinal() + 1];
	}
}
