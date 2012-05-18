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

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.Identities;
import org.servalproject.servald.SubscriberId;
import org.servalproject.servald.ServalD.RhizomeListResult;
import org.servalproject.servald.ServalD.RhizomeAddFileResult;
import org.servalproject.servald.ServalD.RhizomeExtractManifestResult;
import org.servalproject.servald.ServalD.RhizomeExtractFileResult;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDInterfaceError;

import android.util.Log;
import android.os.Environment;

public class Rhizome {

	/** TAG for debugging */
	public static final String TAG = "R3";

	/** Display a toast message in a toast.
	 */
	public static void goToast(String text) {
		ServalBatPhoneApplication.context.displayToastMessage(text);
	}

	public static boolean appendMessage(SubscriberId senderSid, SubscriberId recipientSid, byte[] bytes) {
		Log.i(TAG, "Rhizome.appendMessage(senderSid=" + senderSid + ", recipientSid=" + recipientSid + ", bytes=[..." + bytes.length + "...])");
		File manifestFile = null;
		File payloadFile = null;
		try {
			File dir = getMeshmsStageDirectoryCreated();
			manifestFile = File.createTempFile("m", ".manifest", dir);
			payloadFile = File.createTempFile("m", ".payload", dir);
			RhizomeListResult found = ServalD.rhizomeList(RhizomeManifest_MeshMS.SERVICE, senderSid.toString(), recipientSid.toString(), -1, -1);
			RhizomeManifest_MeshMS man;
			if (found.list.length == 0) {
				FileOutputStream fos = new FileOutputStream(payloadFile);
				fos.write(bytes);
				fos.close();
				String skel = "sender=" + senderSid + "\nrecipient=" + recipientSid + "\n";
				FileWriter fw = new FileWriter(manifestFile);
				fw.write(skel, 0, skel.length());
				fw.close();
				man = new RhizomeManifest_MeshMS();
			} else {
				String manifestId;
				try {
					manifestId = found.list[0][found.columns.get("manifestid")];
				}
				catch (NullPointerException e) {
					throw new ServalDInterfaceError("missing 'manifestid' column", found);
				}
				ServalD.rhizomeExtractManifest(manifestId, manifestFile);
				if (manifestFile.length() > RhizomeManifest_File.MAX_MANIFEST_BYTES) {
					Log.e(Rhizome.TAG, "manifest file " + manifestFile + "is too long");
					return false;
				}
				FileInputStream fis = new FileInputStream(manifestFile);
				byte[] buf = new byte[(int) manifestFile.length()];
				fis.read(buf);
				fis.close();
				try {
					man = RhizomeManifest_MeshMS.fromByteArray(buf);
					if (man.getSender() != senderSid.toString()) {
						Log.e(Rhizome.TAG, "Cannot append message, senderSid=" + senderSid + " does not match existing manifest sender=" + man.getSender());
						return false;
					}
					if (man.getRecipient() != recipientSid.toString()) {
						Log.e(Rhizome.TAG, "Cannot append message, recipientSid=" + recipientSid + " does not match existing manifest recipient=" + man.getRecipient());
						return false;
					}
				}
				catch (RhizomeManifestParseException e) {
					Log.e(Rhizome.TAG, "Cannot parse existing manifest", e);
					return false;
				}
				catch (RhizomeManifest.MissingField e) {
					Log.e(Rhizome.TAG, "Cannot append message", e);
					return false;
				}
				ServalD.rhizomeExtractFile(manifestId, payloadFile);
				FileOutputStream fos = new FileOutputStream(payloadFile, true);
				fos.write(bytes);
				fos.close();
				man.unsetFilesize();
				man.unsetFilehash();
				//man.setVersion(man.getVersion() + 1);
				man.unsetVersion(); // servald will auto-generate a new version from current time
				man.unsetDateMillis();
			}
			FileOutputStream fos = new FileOutputStream(manifestFile);
			try {
				fos.write(man.toByteArrayUnsigned());
			}
			catch (RhizomeManifestSizeException e) {
				Log.e(Rhizome.TAG, "Cannot write new manifest", e);
				return false;
			}
			finally {
				fos.close();
			}
			ServalD.rhizomeAddFile(manifestFile, payloadFile, senderSid.toString(), null);
			return true;
		}
		catch (IOException e) {
			Log.e(Rhizome.TAG, "file operation failed", e);
			return false;
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
			return false;
		}
		finally {
			try {
				if (payloadFile != null)
					payloadFile.delete();
			}
			catch (SecurityException ee) {
				Log.w(Rhizome.TAG, "could not delete '" + payloadFile + "'", ee);
			}
			try {
				if (manifestFile != null)
					manifestFile.delete();
			}
			catch (SecurityException ee) {
				Log.w(Rhizome.TAG, "could not delete '" + manifestFile + "'", ee);
			}
		}
	}

	/** Add a file (payload) to the rhizome store, creating a basic manifest for it.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean addFile(File path) {
		Log.i(TAG, "Rhizome.addFile(path=" + path + ")");
		try {
			RhizomeAddFileResult res = ServalD.rhizomeAddFile(path, null, Identities.getCurrentIdentity().toString(), null);
			Log.i(TAG, "service=" + res.service);
			Log.i(TAG, "manifestId=" + res.manifestId);
			Log.i(TAG, "fileHash=" + res.fileHash);
			return true;
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
		}
		catch (ServalDInterfaceError e) {
			Log.e(Rhizome.TAG, "servald interface is broken", e);
		}
		return false;
	}

	/** Return the path of the directory where saved rhizome files are stored.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static File getSaveDirectory() {
		return new File(Environment.getExternalStorageDirectory(), "/serval/rhizome/saved");
	}

	/** Return the path of the directory where saved rhizome files are stored, after ensuring that
	 * the directory exists.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static File getSaveDirectoryCreated() throws IOException {
		File dir = getSaveDirectory();
		try {
			if (!dir.isDirectory() && !dir.mkdirs())
				throw new IOException("cannot mkdirs " + dir);
			return dir;
		}
		catch (SecurityException e) {
			throw new IOException("no permission to create " + dir);
		}
	}

	/** Return the path of the directory where manifest and payload files are staged.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static File getMeshmsStageDirectory() {
		return new File(ServalBatPhoneApplication.context.coretask.DATA_FILE_PATH, "meshms");
	}

	/** Return the path of the directory where manifest and payload files are staged.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static File getMeshmsStageDirectoryCreated() throws IOException {
		File dir = getMeshmsStageDirectory();
		try {
			if (!dir.isDirectory() && !dir.mkdirs())
				throw new IOException("cannot mkdirs " + dir);
			return dir;
		}
		catch (SecurityException e) {
			throw new IOException("no permission to create " + dir);
		}
	}

	/** Extract a manifest and its payload (a "bundle") from the rhizome database.  Stores them
	 * in a pair of files in the rhizome "saved" directory, overwriting any files that may
	 * already be there with the same name.  The "saved" directory is created if it does not yet
	 * exist.
	 *
	 * @param manifestId	The manifest ID of the bundle to extract
	 * @param name			The basename to give the payload file in the "saved" directory.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean extractFile(String manifestId, String name) {
		try {
			Rhizome.getSaveDirectoryCreated(); // create the directory
			File savedPayloadFile = savedPayloadFileFromName(name);
			File savedManifestFile = savedManifestFileFromName(name);
			// A manifest file without a payload file is ok, but not vice versa.  So always
			// delete manifest files last and create them first.
			savedPayloadFile.delete();
			savedManifestFile.delete();
			boolean done = false;
			try {
				RhizomeExtractManifestResult mres = ServalD.rhizomeExtractManifest(manifestId, savedManifestFile);
				RhizomeExtractFileResult fres = ServalD.rhizomeExtractFile(mres.fileHash, savedPayloadFile);
				if (!mres.fileHash.equals(fres.fileHash))
					Log.w(Rhizome.TAG, "extracted file hashes differ: mres.fileHash=" + mres.fileHash + ", fres.fileHash=" + fres.fileHash);
				if (mres.fileSize != fres.fileSize)
					Log.w(Rhizome.TAG, "extracted file lengths differ: mres.fileSize=" + mres.fileSize + ", fres.fileSize=" + fres.fileSize);
				done = true;
			}
			finally {
				if (!done) {
					try {
						savedPayloadFile.delete();
					}
					catch (SecurityException ee) {
						Log.w(Rhizome.TAG, "could not delete '" + savedPayloadFile + "'", ee);
					}
					try {
						savedManifestFile.delete();
					}
					catch (SecurityException ee) {
						Log.w(Rhizome.TAG, "could not delete '" + savedManifestFile + "'", ee);
					}
				}
			}
			return done;
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
		}
		catch (ServalDInterfaceError e) {
			Log.e(Rhizome.TAG, "servald interface is broken", e);
		}
		catch (IOException e) {
			Log.e(Rhizome.TAG, "cannot save manifestId=" + manifestId + ", name=" + name, e);
		}
		return false;
	}

	/** Delete a pair of files, the payload file first then the manifest file.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void deleteSavedFiles(File payloadFile, File manifestFile) {
		try {
			Log.i(Rhizome.TAG, "Delete " + payloadFile);
			payloadFile.delete();
			try {
				Log.i(Rhizome.TAG, "Delete " + manifestFile);
				manifestFile.delete();
			}
			catch (SecurityException e) {
				Log.w(Rhizome.TAG, "cannot delete " + manifestFile, e);
			}
		}
		catch (SecurityException e) {
			Log.w(Rhizome.TAG, "cannot delete " + payloadFile, e);
		}
	}

	/** Given the 'name' field from a manifest, return a File in the saved directory where its
	 * payload can be saved.
	 *
	 * Leading dots are stripped from the name, to ensure that the file is visible to most
	 * file browsers and to avoid collisions with manifest files.  If the file name consists
	 * of all dots, then the special name "Ndots" is returned, eg, "...." gives "4dots".
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static File savedPayloadFileFromName(String name) {
		String strippedName = name;
		if (strippedName.length() == 0)
			strippedName = "Untitled";
		while (strippedName.startsWith("."))
			strippedName = strippedName.substring(1);
		if (strippedName.length() == 0)
			strippedName = name.length() + "dot" + (name.length() == 1 ? "" : "s");
		return new File(Rhizome.getSaveDirectory(), strippedName);
	}

	/** Given the 'name' field from a manifest, return a File in the saved directory where that
	 * manifest can be saved.
	 *
	 * Leading dots are stripped from the name, to ensure that the file is visible to most
	 * file browsers and to avoid collisions with the namifest files.  Manifest file names are
	 * formed as ".manifest." + payloadName.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static File savedManifestFileFromName(String name) {
		return new File(Rhizome.getSaveDirectory(), ".manifest." + savedPayloadFileFromName(name).getName());
	}

}
