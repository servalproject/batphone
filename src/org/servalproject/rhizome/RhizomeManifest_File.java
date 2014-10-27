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

import android.os.Bundle;
import android.webkit.MimeTypeMap;

/**
 * Represents a Rhizome File manifest, with methods to serialise to/from a byte stream for storage
 * on disk.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifest_File extends RhizomeManifest {

	public final static String SERVICE = "file";

	private String mName;

	@Override
	public RhizomeManifest_File clone() throws CloneNotSupportedException {
		return (RhizomeManifest_File) super.clone();
	}

	/** Construct an empty Rhizome manifest.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public RhizomeManifest_File() throws RhizomeManifestParseException {
		super(SERVICE);
		mName = null;
	}

	/** Construct a Rhizome manifest from an Android Bundle containing various manifest fields.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected RhizomeManifest_File(Bundle b, byte[] signatureBlock) throws RhizomeManifestParseException {
		super(b, signatureBlock);
		mName = b.getString("name");
	}

	@Override
	protected void makeBundle() {
		super.makeBundle();
		if (mName != null) mBundle.putString("name", mName);
	}

	/** Return the 'name' field.
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public String getName() {
		return mName;
	}

	public void setName(String name) {
		mName = name;
	}

	@Override
	public String getDisplayName() {
		if (mName != null && !"".equals(mName))
			return mName;
		return super.getDisplayName();
	}

	public void setField(String name, String value) throws RhizomeManifestParseException {
		if ("name".equalsIgnoreCase(name))
			setName(value);
		else
			super.setField(name, value);
	}

	@Override
	public String getMimeType(){
		String ext = mName.substring(mName.lastIndexOf(".") + 1);
		String contentType = MimeTypeMap.getSingleton()
				.getMimeTypeFromExtension(ext);
		if (contentType==null || "".equals(contentType))
			return super.getMimeType();
		return contentType;
	}
}
