/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.rhizome;

import android.util.Log;
import android.os.Bundle;

/**
 * Represents a Rhizome MeshMS manifest, with methods to serialise to/from a byte stream for storage
 * on disk.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifest_MeshMS extends RhizomeManifest {

	public final static String SERVICE = "MeshMS";

	private String mSender;
	private String mRecipient;

	/** Construct a Rhizome MeshMS manifest from its byte-stream representation.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeManifest_MeshMS fromByteArray(byte[] bytes) throws RhizomeManifestParseException {
		RhizomeManifest manifest = RhizomeManifest.fromByteArray(bytes);
		if (!(manifest instanceof RhizomeManifest_MeshMS))
			throw new RhizomeManifestParseException("not a MeshMS manifest, service='" + manifest.getService() + "'");
		return (RhizomeManifest_MeshMS) manifest;
	}

	/** Construct a Rhizome MeshMS manifest from a bundle of named fields.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeManifest_MeshMS fromBundle(Bundle b, byte[] signatureBlock) throws RhizomeManifestParseException {
		String service = parseNonEmpty("service", b.getString("service"));
		if (!service.equalsIgnoreCase(SERVICE))
			throw new RhizomeManifestParseException("mismatched service '" + service + "'");
		return new RhizomeManifest_MeshMS(b, signatureBlock);
	}

	/** Construct a Rhizome MeshMS manifest from an Android Bundle containing various manifest fields.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected RhizomeManifest_MeshMS(Bundle b, byte[] signatureBlock) throws RhizomeManifestParseException {
		super(b, signatureBlock);
		mSender = parseSID("sender", b.getString("sender"));
		mRecipient = parseSID("recipient", b.getString("recipient"));
	}

	/** Construct a Rhizome MeshMS manifest from minimal required field values.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected RhizomeManifest_MeshMS(String id, String sender, String recipient, String date, String version, String filesize, String filehash)
		throws RhizomeManifestParseException
	{
		super(id, date, version, filesize, filehash);
		mSender = parseSID("sender", sender);
		mRecipient = parseSID("recipient", recipient);
	}

	/** Return the service field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public String getService() {
		return this.SERVICE;
	}

	protected void makeBundle() {
		super.makeBundle();
		if (mSender != null) mBundle.putString("sender", mSender);
		if (mRecipient != null) mBundle.putString("recipient", mRecipient);
	}

	/** Return the 'sender' field (SID).
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public String getSender() throws MissingField {
		missingIfNull("sender", mSender);
		return mSender;
	}

	/** Return the 'recipient' field (SID).
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public String getRecipient() throws MissingField {
		missingIfNull("recipient", mRecipient);
		return mRecipient;
	}

}
