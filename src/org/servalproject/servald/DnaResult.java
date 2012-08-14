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

	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof DnaResult))
			return false;
		DnaResult other = (DnaResult) o;
		return this.peer.sid.equals(other.peer.sid);
	}

	@Override
	public int hashCode() {
		return peer.hashCode();
	}
}
