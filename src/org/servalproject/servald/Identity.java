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
	public final SubscriberId subscriberId;
	private String name;
	private String did;
	private boolean main;
	private String profileData = "profile data";

	private static List<Identity> identities;

	public static Identity createIdentity() throws InvalidHexException, ServalDFailureException {
		ServalD.KeyringAddResult result = ServalD.keyringAdd(); // TODO provide identity PIN
		Identity id = new Identity(result.subscriberId);
		id.did = result.did;
		id.name = result.name;
		id.main = Identity.identities.size() == 0;
		id.profileData = result.profileData;
		Identity.identities.add(id);
		return id;
	}

	public static List<Identity> getIdentities() {
		if (identities == null) {
			identities = new ArrayList<Identity>();
			try {
				// TODO provide list of unlock PINs
				ServalD.KeyringListResult result = ServalD.keyringList();
				for (ServalD.KeyringListResult.Entry ent: result.entries) {
					Identity id = new Identity(ent.subscriberId);
					id.did = ent.did;
					id.name = ent.name;
					id.main = identities.size() == 0;
					id.profileData = ent.profileData;
					identities.add(id);
				}
			}
			catch (ServalDFailureException e) {
				Log.e("Identities", e.toString(), e);
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
		this.subscriberId = sid;
	}

	public String getName() {
		return name;
	}

	public String getDid() {
		return did;
	}

	public String getProfileData() {
		return profileData == null ? "" : profileData;
	}

	public void setProfileData(String profileData) {
		this.profileData = profileData;
	}

	public void setDetails(Context context, String did, String name,
			String profileData)
			throws ServalDFailureException {

		if (did == null || !did.matches("[0-9+*#]{5,31}"))
			throw new IllegalArgumentException(
					"The phone number must contain only 0-9+*# and be at least 5 characters in length");

		if (PhoneNumberUtils.isEmergencyNumber(did) || did.startsWith("11"))
			throw new IllegalArgumentException(
					"That number cannot be dialed as it will be redirected to a cellular emergency service.");

		ServalD.KeyringAddResult result = ServalD.keyringSetDidName(
				this.subscriberId, did == null ? "" : did, name == null ? ""
						: name, profileData == null ? "" : profileData);
		this.did = result.did;
		this.name = result.name;
		this.profileData = result.profileData;

		Control.reloadConfig();

		if (main) {
			Intent intent = new Intent("org.servalproject.SET_PRIMARY");
			intent.putExtra("did", this.did);
			intent.putExtra("sid", this.subscriberId.toString());
			intent.putExtra("profileData", this.profileData);
			context.sendStickyBroadcast(intent);
		}
	}

	@Override
	public int hashCode() {
		return this.subscriberId.hashCode();
	}


	@Override
	public String toString() {
		return this.subscriberId.toString() + " " + did + " " + name + " "
				+ profileData;
	}

}
