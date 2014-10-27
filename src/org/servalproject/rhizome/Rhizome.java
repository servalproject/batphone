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

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.provider.RhizomeProvider;
import org.servalproject.servald.Identity;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalDMonitor;
import org.servalproject.servaldna.BundleId;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.ServalDFailureException;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class Rhizome {

	/** TAG for debugging */
	public static final String TAG = "Rhizome";

	public static final String ACTION_RECEIVE_FILE = "org.servalproject.rhizome.RECEIVE_FILE";
	public static final String RECEIVE_PERMISSION = "org.servalproject.rhizome.RECEIVE_FILE";

	/** Unshare a file (payload) that already exists in the rhizome store, by setting
	 * its payload to empty.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean unshareFile(RhizomeManifest_File fileManifest) {
		Log.d(TAG, "Rhizome.unshareFile(" + fileManifest + ")");
		File manifestFile = null;
		try {
			RhizomeManifest unsharedManifest = Rhizome.readManifest(fileManifest.getManifestId());

			Log.d(TAG, "unsharedManifest=" + unsharedManifest);
			unsharedManifest.setFilesize(0L);
			long millis = System.currentTimeMillis();
			try {
				long version = unsharedManifest.getVersion();
				if (millis > version)
					unsharedManifest.setVersion(millis);
				else
					unsharedManifest.setVersion(version + 1);
			}
			catch (RhizomeManifest.MissingField e) {
				unsharedManifest.setVersion(millis);
			}
			unsharedManifest.setDateMillis(millis);
			unsharedManifest.unsetFilehash();
			File dir = getStageDirectoryCreated();

			manifestFile = File.createTempFile("unshare", ".manifest", dir);
			unsharedManifest.writeTo(manifestFile);
			ServalDCommand.ManifestResult res = ServalDCommand.rhizomeAddFile(null, manifestFile, Identity.getMainIdentity().subscriberId, null);
			Log.d(TAG, "service=" + res.service);
			Log.d(TAG, "manifestId=" + res.manifestId);
			Log.d(TAG, "fileSize=" + res.fileSize);
			Log.d(TAG, "fileHash=" + res.fileHash);
			return true;
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
		}
		catch (RhizomeManifest.MissingField e) {
			Log.e(Rhizome.TAG, "cannot build new manifest", e);
		}
		catch (RhizomeManifestParseException e) {
			Log.e(Rhizome.TAG, "cannot build new manifest", e);
		}
		catch (RhizomeManifestSizeException e) {
			Log.e(Rhizome.TAG, "manifest too big", e);
		}
		catch (IOException e) {
			Log.e(Rhizome.TAG, "cannot write manifest to " + manifestFile, e);
		}
		finally {
			safeDelete(manifestFile);
		}
		return false;
	}

	/**
	 * Detect if rhizome can currently be used, updating config if required.
	 */
	public static void setRhizomeEnabled() {
		setRhizomeEnabled(true);
	}

	public static void setRhizomeEnabled(boolean enable) {
		try {
			boolean alreadyEnabled = ServalD.isRhizomeEnabled();
			if (enable == alreadyEnabled)
				return;

			// make sure the rhizome storage directory is on external
			// storage.
			File folder = null;

			if (enable) {
				try{
					folder = Rhizome.getStorageDirectory();
				} catch (FileNotFoundException e) {
					enable = false;
					Log.v(TAG, "Disabling rhizome as external storage is not mounted");
				}
			}

			// TODO, an earlier version of this code attempted to set rhizome.enabled, at some point we can deprecate this
			if (enable) {
				ServalDCommand.configActions(
						ServalDCommand.ConfigAction.set, "rhizome.datastore_path", folder.getAbsolutePath(),
						ServalDCommand.ConfigAction.set, "rhizome.enable", "1",
						ServalDCommand.ConfigAction.del, "rhizome.enabled",
						ServalDCommand.ConfigAction.sync
				);
				if (ServalBatPhoneApplication.context.meshMS!=null)
					ServalBatPhoneApplication.context.meshMS.initialiseNotification();
			} else {
				ServalDCommand.configActions(
						ServalDCommand.ConfigAction.set, "rhizome.enable", "0",
						ServalDCommand.ConfigAction.del, "rhizome.enabled",
						ServalDCommand.ConfigAction.sync
				);
			}
		} catch (ServalDFailureException e) {
			Log.e(TAG, e.toString(), e);
		}
	}

	private static File getStoragePath(String subpath) throws FileNotFoundException {
		File folder = ServalBatPhoneApplication.getStorageFolder();
		if (folder == null)
			throw new FileNotFoundException("External storage is not available.");
		return new File(folder, subpath);
	}

	private static File createDirectory(File dir) throws IOException {
		try {
			if (!dir.isDirectory() && !dir.mkdirs())
				throw new IOException("cannot mkdirs: " + dir);
			return dir;
		}
		catch (SecurityException e) {
			throw new IOException("no permission to create " + dir);
		}
	}

	/**
	 * Returns the location of the rhizome data store Must match the contents of
	 * serval.conf
	 *
	 * @throws FileNotFoundException
	 */
	public static File getStorageDirectory() throws FileNotFoundException {
		return getStoragePath("rhizome");
	}

	/**
	 * Return the path of the directory where rhizome temporary files can be created.
	 * All files in this directory may be deleted on app start.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 * @throws FileNotFoundException
	 */
	public static File getTempDirectory() throws FileNotFoundException {
		return getStoragePath("rhizome/tmp");
	}

	/**
	 * Return the path of the directory where rhizome temporary files can be created, after ensuring
	 * the directory exists.  All files in this directory may be deleted on app start.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 * @throws FileNotFoundException
	 */
	public static File getTempDirectoryCreated() throws IOException {
		return createDirectory(getTempDirectory());
	}

	/**
	 * Remove all files from the temporary directory.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void cleanTemp() {
		try {
			File dir = getTempDirectory();
			if (dir.isDirectory())
				for (File file: dir.listFiles())
					safeDelete(file);
		}
		catch (Exception e) {
			Log.w(Rhizome.TAG, "error cleaning Rhizome temporary directory", e);
		}
	}

	/**
	 * Return the path of the directory where saved rhizome files are stored.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 * @throws FileNotFoundException
	 */
	public static File getSaveDirectory() throws FileNotFoundException {
		return getStoragePath("rhizome/saved");
	}

	/** Return the path of the directory where saved rhizome files are stored, after ensuring that
	 * the directory exists.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static File getSaveDirectoryCreated() throws IOException {
		return createDirectory(getSaveDirectory());
	}

	/**
	 * Return the path of the directory where rhizome files are staged.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 * @throws FileNotFoundException
	 */
	public static File getStageDirectory() throws FileNotFoundException {
		return getStoragePath("rhizome/stage");
	}

	/** Return the path of the directory where rhizome files are staged, after ensuring that
	 * the directory exists.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static File getStageDirectoryCreated() throws IOException {
		return createDirectory(getStageDirectory());
	}

	public static RhizomeManifest readManifest(BundleId bid) throws ServalDFailureException, RhizomeManifestParseException {
		ServalDCommand.ManifestResult result = ServalDCommand.rhizomeExportManifest(bid, null);
		return RhizomeManifest.fromByteArray(result.manifest);
	}

	/**
	 * Given the 'name' field from a manifest, return a File in the saved
	 * directory where its payload can be saved.
	 *
	 * Leading dots are stripped from the name, to ensure that the file is
	 * visible to most file browsers and to avoid collisions with manifest
	 * files. If the file name consists of all dots, then the special name
	 * "Ndots" is returned, eg, "...." gives "4dots".
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 * @throws FileNotFoundException
	 */
	public static File savedPayloadFileFromName(String name)
			throws FileNotFoundException {
		String strippedName = name;
		if (strippedName.length() == 0)
			strippedName = "Untitled";
		while (strippedName.startsWith("."))
			strippedName = strippedName.substring(1);
		if (strippedName.length() == 0)
			strippedName = name.length() + "dot" + (name.length() == 1 ? "" : "s");
		return new File(Rhizome.getSaveDirectory(), strippedName);
	}

	/**
	 * Given the 'name' field from a manifest, return a File in the saved
	 * directory where that manifest can be saved.
	 *
	 * Leading dots are stripped from the name, to ensure that the file is
	 * visible to most file browsers and to avoid collisions with the namifest
	 * files. Manifest file names are formed as ".manifest." + payloadName.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 * @throws FileNotFoundException
	 */
	public static File savedManifestFileFromName(String name)
			throws FileNotFoundException {
		return new File(Rhizome.getSaveDirectory(), ".manifest." + savedPayloadFileFromName(name).getName());
	}

	/** Helper function for cleaning up temporary files, for use in 'finally' clauses or where
	 * another exception is already being dealt with.  If removing a pair of files representing a
	 * bundle (payload and manifest), remove the payload file first, so there is no chance of
	 * leaving a manifest-less payload file lying around.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean safeDelete(File f) {
		if (f != null) {
			try {
				return f.delete();
			} catch (SecurityException e) {
				Log.w(Rhizome.TAG, "could not delete '" + f + "'", e);
			}
		}
		return false;
	}

	/** Invoked in a thread whenever a new bundle appears in the rhizome store.
	 */
	private static class ExamineBundle implements Runnable {
		public final RhizomeManifest manifest;

		public ExamineBundle(RhizomeManifest manifest) {
			this.manifest = manifest;
		}

		@Override
		public void run() {
			try {
				if (manifest instanceof RhizomeManifest_MeshMS) {
					RhizomeManifest_MeshMS meshms = (RhizomeManifest_MeshMS) manifest;
					if (Identity.getMainIdentity().subscriberId.equals(meshms
                            .getRecipient()))
                        if (ServalBatPhoneApplication.context.meshMS!=null)
                            ServalBatPhoneApplication.context.meshMS.bundleArrived(meshms);
					else if (meshms.getRecipient().isBroadcast()) {
                        // TODO?
					} else
						Log.d(Rhizome.TAG, "not for me (is for " + meshms.getRecipient() + ")");
				} else if (manifest instanceof RhizomeManifest_File) {
					RhizomeManifest_File file = (RhizomeManifest_File) manifest;
					// If file size is zero, then this is an "unshared" file, and has no payload.
					// We cannot form a URI because there is no file hash.  It is not clear whether
					// we ought to announce this as a received file, anyway, because technically it
					// is not: it is an instruction to remove a file that we received previously.
					if (file.getFilesize() != 0) {
						Intent mBroadcastIntent = new Intent(
								ACTION_RECEIVE_FILE);

						mBroadcastIntent.setDataAndType(Uri.parse("content://"
										+ RhizomeProvider.AUTHORITY + "/"
								+ file.getManifestId().toHex()), file.getMimeType());

						mBroadcastIntent.putExtras(file.asBundle());
						Log.v(TAG, "Sending broadcast for " + file.getDisplayName()+", type "+file.getMimeType());
						ServalBatPhoneApplication.context.sendBroadcast(
								mBroadcastIntent,
								RECEIVE_PERMISSION);

						testUpgrade(file);
					}
				}else
					Log.v(TAG, "Unknown manifest type?");
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		private void testUpgrade(RhizomeManifest_File file) {
			try {
				ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
				String sBundleId = app.getString(R.string.manifest_id);
				if (sBundleId == null || "".equals(sBundleId))
					return;

				BundleId installedBundleId = new BundleId(sBundleId);
				if (!file.mManifestId.equals(installedBundleId))
					return;

				long installedVersion = app.settings
						.getLong("installed_manifest_version", -1);
				if (file.mVersion <= installedVersion)
					return;

				app.notifySoftwareUpdate(file.mManifestId);

			} catch (Exception e) {
				Log.v(TAG, e.getMessage(), e);
			}
		}
	}

	public static void registerMessageHandlers(ServalDMonitor monitor){
		ServalDMonitor.Messages handler = new ServalDMonitor.Messages() {
			@Override
			public void onConnect(ServalDMonitor monitor) {
				monitor.sendMessageAndLog("monitor rhizome");
			}

			@Override
			public void onDisconnect(ServalDMonitor monitor) {
			}

			@Override
			public int message(String cmd, Iterator<String> args, InputStream in, int dataBytes) throws IOException {
				int ret=0;
				if (cmd.equalsIgnoreCase("BUNDLE")) {
					try {
						String manifestId = args.next();
						BundleId bid = new BundleId(manifestId);
						RhizomeManifest manifest;
						if (dataBytes > 0) {
							byte manifestBytes[] = new byte[dataBytes];
							int offset = 0;
							while (offset < dataBytes) {
								int read = in.read(manifestBytes, offset, dataBytes
										- offset);
								if (read < 0)
									throw new EOFException();
								offset += read;
								ret += read;
							}
							manifest = RhizomeManifest.fromByteArray(manifestBytes);
						} else {
							manifest = readManifest(bid);
						}

						ServalBatPhoneApplication.context.runOnBackgroundThread(new ExamineBundle(manifest));
					} catch (Exception e) {
						Log.v(TAG, e.getMessage(), e);
					}
				}
				return ret;
			}
		};
		monitor.addHandler("BUNDLE", handler);
	}

}
