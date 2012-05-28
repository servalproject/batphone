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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.meshms.IncomingMeshMS;
import org.servalproject.meshms.SimpleMeshMS;
import org.servalproject.provider.RhizomeProvider;
import org.servalproject.rhizome.RhizomeManifest.MissingField;
import org.servalproject.rhizome.RhizomeMessageLogEntry.TooLongException;
import org.servalproject.servald.BundleId;
import org.servalproject.servald.Identities;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalD.RhizomeAddFileResult;
import org.servalproject.servald.ServalD.RhizomeExtractFileResult;
import org.servalproject.servald.ServalD.RhizomeExtractManifestResult;
import org.servalproject.servald.ServalD.RhizomeListResult;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDInterfaceError;
import org.servalproject.servald.SubscriberId;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

public class Rhizome {

	/** TAG for debugging */
	public static final String TAG = "R3";
	public static final String ACTION_RECIEVE_FILE = "org.servalproject.rhizome.RECIEVE_FILE";

	/** Display a toast message in a toast.
	 */
	public static void goToast(String text) {
		ServalBatPhoneApplication.context.displayToastMessage(text);
	}

	/**
	 * Send a message over Rhizome.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 * @throws IOException
	 */
	public static void sendMessage(SubscriberId sender, SubscriberId recipient,
			RhizomeMessage rm) throws IOException {
		Log.i(TAG, "Rhizome.sendMessage(" + rm + ")");
		File manifestFile = null;
		File payloadFile = null;
		try {
			RhizomeListResult found = ServalD.rhizomeList(RhizomeManifest_MeshMS.SERVICE, sender, recipient, -1, -1);
			BundleId manifestId = null;
			if (found.list.length != 0) {
				try {
					manifestId = new BundleId(found.list[0][found.columns.get("id")]);
				}
				catch (NullPointerException e) {
					throw new ServalDInterfaceError("missing 'id' column", found);
				}
				catch (BundleId.InvalidHexException e) {
					throw new ServalDInterfaceError("invalid 'id' column", found, e);
				}
			}
			File dir = getMeshmsStageDirectoryCreated();
			manifestFile = File.createTempFile("send", ".manifest", dir);
			payloadFile = File.createTempFile("send", ".payload", dir);

			extractMeshMSBundle(manifestId, sender, recipient, manifestFile,
					payloadFile);

			FileOutputStream fos = new FileOutputStream(payloadFile, true); // append
			try {
				fos.write(new RhizomeMessageLogEntry(rm).toBytes());
				fos.getFD().sync();
			}
			finally {
				fos.close();
			}
			ServalD.rhizomeAddFile(payloadFile, manifestFile, sender, null);
		}
		catch (ServalDInterfaceError e) {
			IOException io = new IOException();
			io.initCause(e);
			throw io;
		}
		catch (ServalDFailureException e) {
			IOException io = new IOException();
			io.initCause(e);
			throw io;
		} catch (RhizomeManifestSizeException e) {
			IOException io = new IOException();
			io.initCause(e);
			throw io;
		} catch (TooLongException e) {
			IOException io = new IOException();
			io.initCause(e);
			throw io;
		}
		finally {
			safeDelete(manifestFile);
			safeDelete(payloadFile);
		}
	}

	private static RhizomeManifest_MeshMS extractExistingMeshMSBundle(
			BundleId manifestId,
			SubscriberId sender, SubscriberId recipient, File manifestFile,
			File payloadFile) throws ServalDFailureException,
			ServalDInterfaceError, MissingField, IOException,
			RhizomeManifestSizeException, RhizomeManifestParseException,
			RhizomeManifestServiceException {

		ServalD.rhizomeExtractManifest(manifestId, manifestFile);
		RhizomeManifest_MeshMS man = RhizomeManifest_MeshMS
				.readFromFile(manifestFile);
		ServalD.rhizomeExtractFile(man.getFilehash(), payloadFile);

		if (!sender.equals(man.getSender())
				|| !recipient.equals(man.getRecipient()))
			throw new RhizomeManifestParseException(
					"Manifest doesn't have the expected sender and recipient");

		return man;
	}

	/**
	 * Helper function, extract manifest and payload files if they exist,
	 * otherwise create empty files. The manifest file is left in a state
	 * suitable for updating its payload, ie, the date, version, filehash and
	 * filesize fields are removed, and the sender and recipient fields are
	 * guaranteed to be as given in the arguments.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 * @throws IOException
	 * @throws RhizomeManifestSizeException
	 */
	private static void extractMeshMSBundle(BundleId manifestId,
			SubscriberId sender, SubscriberId recipient, File manifestFile,
			File payloadFile) throws IOException, RhizomeManifestSizeException {
		RhizomeManifest_MeshMS man = null;

		if (manifestId != null) {
			try {
				man = extractExistingMeshMSBundle(manifestId, sender,
						recipient, manifestFile, payloadFile);

				man.unsetFilesize();
				man.unsetFilehash();
				man.unsetVersion(); // servald will auto-generate a new version
									// from current time
				man.unsetDateMillis();

			} catch (Exception e) {
				// if there were *any* failures reading the existing message
				// log, just log it and create a new manifest
				Log.e(TAG, e.getMessage(), e);
				manifestFile.delete();
				payloadFile.delete();
				man = null;
			}
		}

		if (man == null) {
			man = new RhizomeManifest_MeshMS();
			man.setSender(sender);
			man.setRecipient(recipient);
			payloadFile.delete();
			payloadFile.createNewFile();
		}
		FileOutputStream fos = new FileOutputStream(manifestFile);
		try {
			fos.write(man.toByteArrayUnsigned());
		} finally {
			fos.close();
		}
	}

	public static void readMessageLogs() throws ServalDFailureException,
			ServalDInterfaceError, RhizomeManifestParseException, IOException,
			RhizomeManifestSizeException, RhizomeManifestServiceException,
			MissingField {
		RhizomeListResult result = ServalD.rhizomeList(
				RhizomeManifest_MeshMS.SERVICE, null,
				Identities.getCurrentIdentity(), -1, -1);
		for (int i = 0; i != result.list.length; ++i) {
			RhizomeManifest_MeshMS manifest = (RhizomeManifest_MeshMS) result
					.toManifest(i);
			manifest = (RhizomeManifest_MeshMS) readManifest(manifest
					.getManifestId());
			receiveMessageLog(manifest);
		}
	}

	/**
	 * Called when a new MeshMS message has been received. The manifest ID
	 * identifies the bundle that contains the message log to which the message
	 * has been appended.
	 *
	 * @param manifestId
	 *            The manifest ID of the bundle to extract
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 * @throws MissingField
	 */
	private static boolean receiveMessageLog(
			RhizomeManifest_MeshMS incomingManifest) throws MissingField {
		Log.i(TAG, "Rhizome.receiveMessage(" + incomingManifest.getManifestId()
				+ ")");
		File incomingPayloadFile = null;
		File outgoingManifestFile = null;
		File outgoingPayloadFile = null;
		RandomAccessFile incomingPayload = null;
		try {
			File dir = getMeshmsStageDirectoryCreated();
			incomingPayloadFile = File.createTempFile("incoming", ".payload", dir);
			outgoingPayloadFile = File.createTempFile("outgoing", ".payload", dir);
			extractPayload(incomingManifest.getFilehash(), incomingPayloadFile);
			SubscriberId other = incomingManifest.getSender();
			SubscriberId self = incomingManifest.getRecipient();
			// Ensure that the recipient is us.
			if (!Identities.getCurrentIdentity().equals(self)) {
				Log.e(Rhizome.TAG, "incoming MeshMS manifest recipient (" + self + ") is not self (" + Identities.getCurrentIdentity() + ") -- discarding");
				return false;
			}
			// Open the incoming message log for reading.
			incomingPayload = new RandomAccessFile(incomingPayloadFile, "r");
			// Look for most recent ACK packet in the incoming message log.
			RhizomeAck latestIncomingAck = null;
			long incomingPayloadLength = incomingPayloadFile.length();
			incomingPayload.seek(incomingPayloadLength);
			while (incomingPayload.getFilePointer() != 0) {
				RhizomeMessageLogEntry entry = new RhizomeMessageLogEntry(incomingPayload, true);
				if (entry.filling instanceof RhizomeAck) {
					latestIncomingAck = (RhizomeAck) entry.filling;
					break;
				}
			}
			// Find if there is already an outgoing message log.
			BundleId outgoingManifestId = null;
			RhizomeListResult found = ServalD.rhizomeList(RhizomeManifest_MeshMS.SERVICE, self, other, -1, -1);
			if (found.list.length != 0) {
				try {
					outgoingManifestId = new BundleId(found.list[0][found.columns.get("id")]);
				}
				catch (NullPointerException e) {
					throw new ServalDInterfaceError("missing 'id' column", found);
				}
				catch (BundleId.InvalidHexException e) {
					throw new ServalDInterfaceError("invalid 'id' column", found, e);
				}
				// Check that outgoing manifest ID matches the latest incoming ACK, if known.
				if (latestIncomingAck != null && !latestIncomingAck.matches(outgoingManifestId)) {
					Log.e(Rhizome.TAG, "outgoing manifest ID (" + outgoingManifestId + ")" +
										" does not match latest incoming ACK (" + latestIncomingAck.bundleIdPrefixHex() + ")"
						);
					return false;
				}
			}

			RhizomeAck latestOutgoingAck = null;

			if (outgoingManifestId != null) {
				outgoingManifestFile = File.createTempFile("outgoing",
						".manifest", dir);
				// Extract the outgoing manifest and payload files.
				extractExistingMeshMSBundle(outgoingManifestId, self, other,
						outgoingManifestFile, outgoingPayloadFile);
				// Look for most recent ACK packet in the outgoing message log.
				RandomAccessFile outgoingPayload = new RandomAccessFile(
						outgoingPayloadFile, "r");
				try {
					long outgoingOffset = outgoingPayload.length();
					outgoingPayload.seek(outgoingOffset);
					while (outgoingPayload.getFilePointer() != 0) {
						RhizomeMessageLogEntry entry = new RhizomeMessageLogEntry(
								outgoingPayload, true);
						if (entry.filling instanceof RhizomeAck) {
							latestOutgoingAck = (RhizomeAck) entry.filling;
							break;
						}
					}
				} finally {
					outgoingPayload.close();
				}
			}

			// Ensure that the incoming manifest ID matches the latest outgoing ACK.  Handle all the
			// incoming messages from the incoming payload since our latest ACK, or since the start
			// of the incoming payload if we have not recorded any previous ACK.
			incomingPayload.seek(0);
			if (latestOutgoingAck != null) {
				if (!latestOutgoingAck
						.matches(incomingManifest.getManifestId())) {
					Log.e(Rhizome.TAG, "incoming manifest ID ("
							+ incomingManifest.getManifestId() + ")" +
										" does not match latest outgoing ACK (" + latestOutgoingAck.bundleIdPrefixHex() + ")"
						);
					return false;
				}
				if (latestOutgoingAck.offset >= incomingPayloadLength) {
					Log.e(Rhizome.TAG, "latest outgoing ACK offset (" + latestOutgoingAck.offset + ")" +
									   " exceeds incoming payload size (" + incomingPayloadLength + ")"
						);
					return false;
				}
				incomingPayload.seek(latestOutgoingAck.offset);
			}

			List<SimpleMeshMS> messages = new ArrayList<SimpleMeshMS>();

			while (incomingPayload.getFilePointer() < incomingPayloadLength) {
				RhizomeMessageLogEntry entry = new RhizomeMessageLogEntry(incomingPayload, false);

				if (entry.filling instanceof RhizomeMessage) {
					RhizomeMessage message = (RhizomeMessage) entry.filling;
					messages.add(message.toMeshMs(other, self));
				}
			}

			// Append an ACK to the outgoing message log.
			FileOutputStream fos = new FileOutputStream(outgoingPayloadFile, true); // append
			try {
				RhizomeAck ack = new RhizomeAck(
						incomingManifest.getManifestId(), incomingPayloadLength);
				fos.write(new RhizomeMessageLogEntry(ack).toBytes());
				fos.getFD().sync();
			}
			catch (RhizomeMessageLogEntry.TooLongException e) {
				Log.e(Rhizome.TAG, "message is too long", e);
				return false;
			}
			finally {
				fos.close();
			}

			// remove manifest flags that need to be rebuilt
			if (outgoingManifestId != null) {
				RhizomeManifest_MeshMS newOutGoing = RhizomeManifest_MeshMS
						.readFromFile(outgoingManifestFile);
				newOutGoing.unsetFilesize();
				newOutGoing.unsetFilehash();
				newOutGoing.unsetVersion();
				newOutGoing.unsetDateMillis();
				newOutGoing.writeTo(outgoingManifestFile);
			}

			ServalD.rhizomeAddFile(outgoingPayloadFile, outgoingManifestFile, self, null);

			// only add to local database after successfully adding outgoing ack
			IncomingMeshMS.addMessages(messages);
			return true;
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
		}
		catch (ServalDInterfaceError e) {
			Log.e(Rhizome.TAG, "servald interface is broken", e);
		}
		catch (RhizomeManifestServiceException e) {
			Log.e(Rhizome.TAG, "incompatible manifest", e);
		}
		catch (RhizomeManifestSizeException e) {
			Log.e(Rhizome.TAG, "manifest too big", e);
		}
		catch (RhizomeManifestParseException e) {
			Log.e(Rhizome.TAG, "malformed manifest", e);
		}
		catch (RhizomeManifest.MissingField e) {
			Log.e(Rhizome.TAG, "incomplete manifest", e);
		}
		catch (RhizomeMessageLogEntry.FormatException e) {
			Log.e(Rhizome.TAG, "malformed payload file", e);
		}
		catch (IOException e) {
			Log.e(Rhizome.TAG, "error reading file", e);
		}
		finally {
			if (incomingPayload != null) {
				try {
					incomingPayload.close();
				}
				catch (IOException e) {
					Log.w(Rhizome.TAG, "error closing " + incomingPayloadFile, e);
				}
			}
			safeDelete(incomingPayloadFile);
			safeDelete(outgoingManifestFile);
			safeDelete(outgoingPayloadFile);
		}
		return false;
	}

	/** Add a file (payload) to the rhizome store, creating a basic manifest for it.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean addFile(File path) {
		Log.i(TAG, "Rhizome.addFile(path=" + path + ")");
		try {
			RhizomeAddFileResult res = ServalD.rhizomeAddFile(path, null, Identities.getCurrentIdentity(), null);
			Log.d(TAG, "service=" + res.service);
			Log.d(TAG, "manifestId=" + res.manifestId);
			Log.d(TAG, "fileHash=" + res.fileHash);
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

	public static RhizomeManifest readManifest(BundleId bid)
			throws ServalDFailureException, ServalDInterfaceError, IOException,
			RhizomeManifestSizeException, RhizomeManifestParseException,
			RhizomeManifestServiceException {

		// XXX - Should read manifest direct from database using
		// the supplied ID.

		File tempFile = File.createTempFile("manifest", ".tmp");
		try {
			ServalD.rhizomeExtractManifest(bid, tempFile);
			return RhizomeManifest
					.readFromFile(tempFile);
		} finally {
			tempFile.delete();
		}
	}

	public static boolean extractFile(RhizomeManifest_File manifest)
			throws MissingField {
		return extractFile(manifest.getManifestId(), manifest.getName());
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
	public static boolean extractFile(BundleId manifestId, String name) {
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
				extractBundle(manifestId, savedManifestFile, savedPayloadFile);
				done = true;
			}
			finally {
				if (!done) {
					safeDelete(savedManifestFile);
					safeDelete(savedPayloadFile);
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
		catch (RhizomeManifestSizeException e) {
			Log.e(Rhizome.TAG, "manifest too big", e);
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

	/** Helper function for extracting a manifest and its payload file.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static void extractBundle(BundleId manifestId, File manifestFile, File payloadFile)
		throws RhizomeManifestSizeException, ServalDFailureException, ServalDInterfaceError
	{
		if (manifestFile.length() > RhizomeManifest.MAX_MANIFEST_BYTES)
			throw new RhizomeManifestSizeException(manifestFile, RhizomeManifest.MAX_MANIFEST_BYTES);
		RhizomeExtractManifestResult mres = ServalD.rhizomeExtractManifest(manifestId, manifestFile);
		RhizomeExtractFileResult fres = extractPayload(mres.fileHash, payloadFile);
		if (mres.fileSize != fres.fileSize)
			Log.w(Rhizome.TAG, "extracted file lengths differ: mres.fileSize=" + mres.fileSize + ", fres.fileSize=" + fres.fileSize);
	}

	/** Helper function for extracting the payload for a given manifest.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static RhizomeExtractFileResult extractPayload(String fileHash,
			File payloadFile)
		throws ServalDFailureException, ServalDInterfaceError
	{
		RhizomeExtractFileResult fres = ServalD.rhizomeExtractFile(fileHash, payloadFile);
		if (!fileHash.equals(fres.fileHash))
			Log.w(Rhizome.TAG, "extracted file hash inconsist: requested filehash=" + fileHash + ", got fres.fileHash=" + fres.fileHash);
		return fres;
	}

	/** Helper function for extracting the payload for a given manifest.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static RhizomeExtractFileResult extractPayload(RhizomeManifest man, File payloadFile)
		throws RhizomeManifest.MissingField, ServalDFailureException, ServalDInterfaceError
	{
		RhizomeExtractFileResult fres = extractPayload(man.getFilehash(), payloadFile);
		try {
			long fileSize = man.getFilesize();
			if (fileSize != fres.fileSize)
				Log.w(Rhizome.TAG, "extracted file lengths differ: manifest.filesize=" + fileSize + ", fres.fileSize=" + fres.fileSize);
		}
		catch (RhizomeManifest.MissingField e) {
			Log.w(Rhizome.TAG, "not checking filesize consistency", e);
		}
		return fres;
	}

	/** Helper function for cleaning up a manifest and its payload file.  Remove the payload file
	 * first.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	private static void safeDelete(File f) {
		if (f == null)
			return;
		try {
			f.delete();
		} catch (SecurityException ee) {
			Log.w(Rhizome.TAG, "could not delete '" + f + "'", ee);
		}
	}

	/** Invoked by the servald monitor thread whenever a new bundle has been added to the Rhizome
	 * store.  That monitor thread must remain highly responsive for the sake of voice call
	 * performance, so the significant work that rhizome needs to do is done in a separate thread
	 * that is started here.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 * @throws RhizomeManifestParseException
	 */
	public static void notifyIncomingBundle(RhizomeManifest manifest) {
		new Thread(new ExamineBundle(manifest)).start();
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
					if (Identities.getCurrentIdentity().equals(
							meshms.getRecipient()))
						receiveMessageLog(meshms);
					else if (meshms.getRecipient().equals('f' * 64)) {
						// Message addressed to broadcast - so receive it
						// XXX - Eventually change this to allow subscription to
						// messaging groups
						// and disable broadcast since it is not really what
						// anyone wants
						Log.d(Rhizome.TAG, "receiving broadcast MeshMS");
						receiveMessageLog(meshms);
					} else
						Log.d(Rhizome.TAG, "not for me");

				} else if (manifest instanceof RhizomeManifest_File) {
					RhizomeManifest_File file = (RhizomeManifest_File) manifest;

					Intent mBroadcastIntent = new Intent(ACTION_RECIEVE_FILE,
							Uri.parse("content://"
									+ RhizomeProvider.AUTHORITY + "/"
									+ file.getFilehash()));

					mBroadcastIntent.putExtras(file.asBundle());

					Log.v(TAG, "Sending broadcast for " + file.getDisplayName());
					ServalBatPhoneApplication.context.sendBroadcast(
							mBroadcastIntent,
							"org.servalproject.rhizome.RECIEVE_FILE");
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}
}
