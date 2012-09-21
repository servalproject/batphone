package org.servalproject.servald;

import org.servalproject.account.AccountService;

import android.content.Context;

public class DnaResult implements IPeer {
	public final Peer peer;
	public String did;
	public String name;
	public String uri;

	public DnaResult(Peer peer) {
		this.peer = peer;
	}

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

	@Override
	public SubscriberId getSubscriberId() {
		return peer.getSubscriberId();
	}

	@Override
	public long getContactId() {
		return peer.getContactId();
	}

	@Override
	public void addContact(Context context) {
		if (peer.contactId == -1) {
			peer.contactId = AccountService.addContact(
					context, name, getSubscriberId(),
					did);
		}
	}

	@Override
	public boolean hasName() {
		return name != null && !name.equals("");
	}

	@Override
	public String getSortString() {
		return hasName() ? name : peer.name +
				did +
				peer.sid;
	}
}
