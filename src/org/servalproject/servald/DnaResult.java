package org.servalproject.servald;

public class DnaResult {
	public Peer peer;
	public String did;
	public String name;
	public String uri;

	@Override
	public String toString() {
		if (name != null && !name.equals(""))
			return name;
		if (did != null && !did.equals(""))
			return did;
		return peer.toString();
	}
}
