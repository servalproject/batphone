package org.servalproject.system;

public abstract class NetworkConfiguration implements
		Comparable<NetworkConfiguration> {
	public final String SSID;

	public NetworkConfiguration(String SSID) {
		this.SSID = SSID;
	}

	@Override
	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;
		NetworkConfiguration other = (NetworkConfiguration) o;
		return SSID.equals(other.SSID);
	}

	@Override
	public int hashCode() {
		return SSID.hashCode();
	}

	@Override
	public String toString() {
		return SSID;
	}

	@Override
	public int compareTo(NetworkConfiguration o) {
		return SSID.compareTo(SSID);
	}
}