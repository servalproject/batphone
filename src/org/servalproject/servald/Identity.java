package org.servalproject.servald;

import java.util.ArrayList;
import java.util.List;

import org.servalproject.servald.AbstractId.InvalidHexException;

import android.util.Log;

public class Identity {
	public final SubscriberId sid;
	private String name;
	private String did;

	private static List<Identity> identities;

	public static Identity createIdentity() throws InvalidHexException,
			ServalDFailureException {
		Identity id = null;
		ServalDResult result = ServalD.command("keyring", "add", "");
		result.failIfStatusError();

		for (int i = 0; i + 1 < result.outv.length; i += 2) {
			String outvi = new String(result.outv[i]);
			if (outvi.equals("sid"))
				id = new Identity(new SubscriberId(new String(result.outv[i + 1])));
			else if (id != null && outvi.equals("did") && result.outv[i + 1].length != 0)
				id.did = new String(result.outv[i + 1]);
			else if (id != null && outvi.equals("name") && result.outv[i + 1].length != 0)
				id.name = new String(result.outv[i + 1]);
		}
		if (id == null)
			throw new ServalDFailureException("Failed to create new identity");
		identities.add(id);
		return id;
	}

	public static List<Identity> getIdentities() {
		if (identities == null) {
			identities = new ArrayList<Identity>();
			// TODO provide list of unlock pins
			ServalDResult result = ServalD.command("keyring", "list", "");
			for (int i = 0; i + 2 < result.outv.length; i += 3) {
				try {
					Identity id = new Identity(new SubscriberId(new String(result.outv[i])));
					if (result.outv[i + 1].length != 0)
						id.did = new String(result.outv[i + 1]);
					if (result.outv[i + 2].length != 0)
						id.name = new String(result.outv[i + 2]);
					identities.add(id);
				} catch (InvalidHexException e) {
					Log.e("Identities", e.toString(), e);
				}
			}
		}
		return identities;
	}

	public static Identity getMainIdentity() {
		getIdentities();
		if (identities.size() < 1)
			return null;
		return identities.get(0);
	}

	private Identity(SubscriberId sid) {
		this.sid = sid;
	}

	public String getName() {
		return name;
	}

	public String getDid() {
		return did;
	}

	public void setDetails(String did, String name)
			throws ServalDFailureException {
		ServalDResult result = ServalD.command("set", "did", sid.toString(),
				did == null ? "" : did,
				name == null ? "" : name);
		result.failIfStatusError();
		this.did = "".equals(did) ? null : did;
		this.name = "".equals(name) ? null : name;
	}

	@Override
	public int hashCode() {
		return sid.hashCode();
	}

	@Override
	public String toString() {
		return sid.toString() + " " + did + " " + name;
	}

}
