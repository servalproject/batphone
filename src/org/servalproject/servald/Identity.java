package org.servalproject.servald;

import java.util.ArrayList;
import java.util.List;

import org.servalproject.Control;
import org.servalproject.servald.AbstractId.InvalidHexException;

import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

public class Identity {
	public final SubscriberId sid;
	private String name;
	private String did;
	private boolean main;

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
		id.main = identities.size() == 0;
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
					id.main = identities.size() == 0;
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

	public void setDetails(Context context, String did, String name)
			throws ServalDFailureException {

		if (did == null || !did.matches("[0-9+*#]{5,31}"))
			throw new IllegalArgumentException(
					"The phone number must contain only 0-9+*# and be at least 5 characters in length");

		if (PhoneNumberUtils.isEmergencyNumber(did) || did.startsWith("11"))
			throw new IllegalArgumentException(
					"That number cannot be dialed as it will be redirected to a cellular emergency service.");

		ServalDResult result = ServalD.command("set", "did", sid.toString(),
				did == null ? "" : did,
				name == null ? "" : name);
		result.failIfStatusError();

		this.did = "".equals(did) ? null : did;
		this.name = "".equals(name) ? null : name;

		Control.reloadConfig();

		if (main) {
			Intent intent = new Intent("org.servalproject.SET_PRIMARY");
			intent.putExtra("did", did);
			intent.putExtra("sid", sid.toString());
			context.sendStickyBroadcast(intent);
		}
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
