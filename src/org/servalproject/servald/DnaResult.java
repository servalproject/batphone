package org.servalproject.servald;

import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.account.AccountService;
import org.servalproject.batphone.CallHandler;
import org.servalproject.servaldna.AbstractId.InvalidHexException;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.SubscriberId;

import java.io.IOException;

public class DnaResult implements IPeer {
	private static final String TAG = "DnaResult";
	public final Peer peer;
	public boolean local = true;
	public String did;
	public String name;
	public final Uri uri;

	public DnaResult(ServalDCommand.LookupResult result) throws InvalidHexException {
		this.name = result.name;
		this.did = result.did;
		this.uri = Uri.parse(result.uri);
		if ("sid".equals(this.uri.getScheme())) {
			SubscriberId sid = new SubscriberId(this.uri.getHost());
			this.peer = PeerListService.getPeer(sid);

			boolean local = false;
			for (String s : this.uri.getPathSegments()) {
				if (s.equals("local")) {
					local = true;
				} else if (s.equals("external")) {
					local = false;
				} else {
					this.did = s;
					// only set the local flag if we get a phone number in the
					// response
					this.local = local;
				}
			}
		} else
			throw new IllegalArgumentException();

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
		return this.uri.equals(other.uri);
	}

	@Override
	public int hashCode() {
		return uri.hashCode();
	}

	@Override
	public SubscriberId getSubscriberId() {
		return peer.getSubscriberId();
	}

	@Override
	public long getContactId() {
		// prevent adding an android contact for gateway services
		if (local)
			return peer.getContactId();
		else
			return Long.MAX_VALUE;
	}

	@Override
	public void addContact(Context context) throws RemoteException,
			OperationApplicationException {
		if (local && peer.contactId == -1) {
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

	@Override
	public String getDid() {
		return did;
	}

	@Override
	public boolean isReachable() {
		return peer.isReachable();
	}

	public void call() throws IOException {
		CallHandler.dial(this);
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()){
			case R.id.call:
				try {
					call();
				} catch (IOException e) {
					Log.e(TAG, e.getMessage(), e);
					ServalBatPhoneApplication.context.displayToastMessage(e.getMessage());
				}
				return;
		}
		peer.onClick(view);
	}
}
