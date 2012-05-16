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
 * Represents a Rhizome File manifest, with methods to serialise to/from a byte stream for storage
 * on disk.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifest_File extends RhizomeManifest {

	public final static String SERVICE = "file";

	private String mName;

	/** Construct a Rhizome File manifest from its byte-stream representation.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeManifest_File fromByteArray(byte[] bytes) throws RhizomeManifestParseException {
		RhizomeManifest manifest = RhizomeManifest.fromByteArray(bytes);
		if (!(manifest instanceof RhizomeManifest_File))
			throw new RhizomeManifestParseException("not a File manifest, service='" + manifest.getService() + "'");
		return (RhizomeManifest_File) manifest;
	}

	/** Construct a Rhizome File manifest from a bundle of named fields.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeManifest_File fromBundle(Bundle b, byte[] signatureBlock) throws RhizomeManifestParseException {
		String service = parseNonEmpty("service", b.getString("service"));
		if (!service.equalsIgnoreCase(SERVICE))
			throw new RhizomeManifestParseException("mismatched service '" + service + "'");
		return new RhizomeManifest_File(b, signatureBlock);
	}

	/** Construct a Rhizome manifest from an Android Bundle containing various manifest fields.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected RhizomeManifest_File(Bundle b, byte[] signatureBlock) throws RhizomeManifestParseException {
		super(b, signatureBlock);
		mName = parseNonEmpty("name", b.getString("name"));
	}

	/** Construct a Rhizome manifest from minimal required field values.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected RhizomeManifest_File(String id, String name, String date, String version, String filesize, String filehash) throws RhizomeManifestParseException {
		super(id, date, version, filesize, filehash);
		mName = parseNonEmpty("name", name);
	}

	/** Return the service field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public String getService() {
		return this.SERVICE;
	}

	protected void makeBundle() {
		super.makeBundle();
		if (mName != null) mBundle.putString("name", mName);
	}

	/** Return the 'name' field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public String getName() throws MissingField {
		missingIfNull("name", mName);
		return mName;
	}

}
