package org.servalproject.system;

public enum WifiMode {
	Adhoc(30), Client(30), Ap(30), Sleep(30);

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
