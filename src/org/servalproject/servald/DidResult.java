package org.servalproject.servald;

public class DidResult {
	public SubscriberId sid;
	public String did;
	public String name;

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof DidResult))
			return false;
		DidResult other = (DidResult) o;
		return (this.sid.equals(other.sid) && this.did.equals(other.did) && this.name
				.equals(other.name));
	}

	@Override
	public String toString() {
		if (name != null)
			return name;
		if (did != null)
			return did;
		return sid.toString().substring(0, 8) + "...";
	}
}
