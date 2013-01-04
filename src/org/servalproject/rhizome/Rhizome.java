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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;

import org.servalproject.Control;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.meshms.IncomingMeshMS;
import org.servalproject.meshms.SimpleMeshMS;
import org.servalproject.provider.RhizomeProvider;
import org.servalproject.rhizome.RhizomeManifest.MissingField;
import org.servalproject.rhizome.RhizomeMessageLogEntry.TooLongException;
import org.servalproject.servald.BundleId;
import org.servalproject.servald.FileHash;
import org.servalproject.servald.Identity;
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
import android.util.Log;
import android.webkit.MimeTypeMap;

public class Rhizome {

	/** TAG for debugging */
	public static final String TAG = "R3";

	public static final String ACTION_RECEIVE_FILE = "org.servalproject.rhizome.RECEIVE_FILE";
	public static final String RECEIVE_PERMISSION = "org.servalproject.rhizome.RECEIVE_FILE";

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
	public static void sendMessage(SubscriberId sender, SubscriberId recipient, RhizomeMessage rm) throws IOException {
		Log.d(TAG, "Rhizome.sendMessage(" + rm + ")");
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
			extractMeshMSBundle(manifestId, sender, recipient, manifestFile, payloadFile);
			FileOutputStream fos = new FileOutputStream(payloadFile, true); // append
			try {
				fos.write(new RhizomeMessageLogEntry(rm).toBytes());
				fos.getFD().sync();
			}
			finally {
				fos.close();
			}
			ServalD.rhizomeAddFile(payloadFile, manifestFile, sender, null);
			// This INFO message used for automated tests, do not change or remove!
			Log.i(TAG, "MESHMS SENT"
					+ " senderSID=" + sender
					+ " recipientSID=" + recipient
					+ " senderDID=" + rm.senderDID
					+ " recipientDID=" + rm.recipientDID
					+ " millis=" + rm.millis
					+ " content=" + rm.message
				);
		}
		catch (ServalDInterfaceError e) {
			IOException io = new IOException(e.getMessage());
			io.initCause(e);
			throw io;
		}
		catch (ServalDFailureException e) {
			IOException io = new IOException(e.getMessage());
			io.initCause(e);
			throw io;
		} catch (RhizomeManifestSizeException e) {
			IOException io = new IOException(e.getMessage());
			io.initCause(e);
			throw io;
		} catch (TooLongException e) {
			IOException io = new IOException(e.getMessage());
			io.initCause(e);
			throw io;
		}
		finally {
			safeDelete(payloadFile); // delete the payload before the manifest
			safeDelete(manifestFile);
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
		ServalD.rhizomeExtractFile(manifestId, payloadFile);

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
			man.setCrypt(recipient.isBroadcast() ? 0 : 1);

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

	public static void readMessageLogs(SubscriberId destSid)
			throws ServalDFailureException,
			ServalDInterfaceError, RhizomeManifestParseException, IOException,
			RhizomeManifestSizeException, RhizomeManifestServiceException,
			MissingField {

		RhizomeListResult result = ServalD.rhizomeList(
				RhizomeManifest_MeshMS.SERVICE, null,
				destSid, -1, -1);

		for (int i = 0; i != result.list.length; ++i) {
			RhizomeManifest_MeshMS manifest = (RhizomeManifest_MeshMS) result.toManifest(i);
			manifest = (RhizomeManifest_MeshMS) readManifest(manifest.getManifestId());
			receiveMessageLog(manifest);
		}
	}

	public static void readMessageLogs() throws ServalDFailureException,
			ServalDInterfaceError, RhizomeManifestParseException, IOException,
			RhizomeManifestSizeException, RhizomeManifestServiceException,
			MissingField {
		Identity main = Identity.getMainIdentity();
		if (main != null) {
			readMessageLogs(main.sid);
			readMessageLogs(SubscriberId.broadcastSid());
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
	private static boolean receiveMessageLog(RhizomeManifest_MeshMS incomingManifest) throws MissingField {
		Log.d(TAG, "Rhizome.receiveMessage(" + incomingManifest.getManifestId() + ")");
		File incomingPayloadFile = null;
		File outgoingManifestFile = null;
		File outgoingPayloadFile = null;
		RandomAccessFile incomingPayload = null;
		try {
			File dir = getMeshmsStageDirectoryCreated();
			incomingPayloadFile = File.createTempFile("incoming", ".payload", dir);
			extractPayload(incomingManifest, incomingPayloadFile);
			SubscriberId sender = incomingManifest.getSender();
			SubscriberId recipient = incomingManifest.getRecipient();

			Identity self = null;
			{
				for (Identity i : Identity.getIdentities()) {
					if (i.sid.equals(sender)) {
						Log.e(Rhizome.TAG, "Ignoring message log that we sent");
						return false;
					}

					if (i.sid.equals(recipient))
						self = i;
				}
			}

			if (recipient.isBroadcast())
				self = Identity.getMainIdentity();

			if (self == null) {
				Log.e(Rhizome.TAG,
						"incoming MeshMS manifest recipient (" + recipient
								+ ") is not a local identity -- ignoring");
				return false;
			}

			// Find if there is already an outgoing message log to this
			// individual sender.
			// note that we don't send acks for broadcast messages to the
			// broadcast recipient
			// so you can only see who hears your messages, not everyone else's
			BundleId outgoingManifestId = null;
			RhizomeAck latestOutgoingAck = null;

			RhizomeListResult found = ServalD.rhizomeList(
					RhizomeManifest_MeshMS.SERVICE,
					self.sid, sender, -1, -1);
			long lastAckMessageTime = 0;

			// look at all possible outgoing logs, trying to find the last ack
			// that matches
			// In an ideal world we wouldn't have multiple logs, but something
			// is going wrong somewhere.
			// TODO, consider pruning any manifests that we ignored
			for (int i = 0; i < found.list.length; i++) {
				File testManifestFile = null;
				File testPayloadFile = null;
				try {
					BundleId testManifestId = new BundleId(
							found.list[i][found.columns.get("id")]);
					testManifestFile = File.createTempFile("outgoing", ".manifest", dir);
					testPayloadFile = File.createTempFile("outgoing", ".payload", dir);
					// Extract the outgoing manifest and payload files.
					extractExistingMeshMSBundle(testManifestId, self.sid, sender, testManifestFile, testPayloadFile);
					// Look for most recent ACK packet in the outgoing message log.
					RandomAccessFile outgoingPayload = new RandomAccessFile(testPayloadFile, "r");
					try {
						long outgoingOffset = outgoingPayload.length();
						outgoingPayload.seek(outgoingOffset);
						while (outgoingPayload.getFilePointer() != 0) {
							RhizomeMessageLogEntry entry = new RhizomeMessageLogEntry(outgoingPayload, true);
							if (!(entry.filling instanceof RhizomeAck))
								continue;

							RhizomeAck ack = (RhizomeAck) entry.filling;
							// remember the time of the last message we acked from this sender.
							if (ack.messageTime > lastAckMessageTime)
								lastAckMessageTime = ack.messageTime;
							if (!ack.matches(incomingManifest.getManifestId()))
								continue;
							if (latestOutgoingAck == null || ack.offset > latestOutgoingAck.offset) {
								safeDelete(outgoingPayloadFile); // delete payload before manifest
								safeDelete(outgoingManifestFile);
								latestOutgoingAck = ack;
								outgoingManifestFile = testManifestFile;
								outgoingManifestId = testManifestId;
								outgoingPayloadFile = testPayloadFile;
							}
							break;
						}
					} finally {
						outgoingPayload.close();
					}
					if (outgoingManifestFile == null) {
						// Append an ack to the first output file if we don't find an exact match.
						outgoingManifestFile = testManifestFile;
						outgoingManifestId = testManifestId;
						outgoingPayloadFile = testPayloadFile;
					}
				}
				catch (Exception e) {
					throw new ServalDInterfaceError(e.getMessage(), found, e);
				}
				finally {
					// delete payload before manifest
					if (testPayloadFile != outgoingPayloadFile)
						safeDelete(testPayloadFile);
					if (testManifestFile != outgoingManifestFile)
						safeDelete(testManifestFile);
				}
			}
			if (outgoingManifestFile == null) {
				outgoingPayloadFile = File.createTempFile("outgoing", ".payload", dir);
				outgoingManifestFile = File.createTempFile("outgoing", ".manifest", dir);
			}

			// Handle all the incoming messages from the incoming payload since
			// our latest ACK, or since the start of the incoming payload if we
			// have not recorded any previous ACK.

			long incomingPayloadLength = incomingPayloadFile.length();
			// Open the incoming message log for reading.
			incomingPayload = new RandomAccessFile(incomingPayloadFile, "r");
			// Look for most recent ACK packet in the incoming message log.
			RhizomeAck latestIncomingAck = null;
			incomingPayload.seek(incomingPayloadLength);
			LinkedList<SimpleMeshMS> messages = new LinkedList<SimpleMeshMS>();
			RhizomeMessage lastMessage = null;
			long parseCutoff = 0;

			// If the incoming message log got shorter, there might be a message we're missing, so
			// just scan it all until we see an old message.
			if (latestOutgoingAck != null && latestOutgoingAck.offset <= incomingPayloadLength) {
				parseCutoff = latestOutgoingAck.offset;
			}

			while (incomingPayload.getFilePointer() > parseCutoff) {
				RhizomeMessageLogEntry entry = new RhizomeMessageLogEntry(incomingPayload, true);
				if (latestIncomingAck == null && entry.filling instanceof RhizomeAck) {
					// not using this ATM
					latestIncomingAck = (RhizomeAck) entry.filling;
				} else if (entry.filling instanceof RhizomeMessage) {
					RhizomeMessage message = (RhizomeMessage) entry.filling;
					// stop parsing if we see an old message
					if (message.millis <= lastAckMessageTime)
						break;
					if (lastMessage == null)
						lastMessage = message;
					// keep the list ordered based on file order, even though we
					// are parsing backwards
					messages.addFirst(message.toMeshMs(sender, recipient));
				}
			}
			if (latestIncomingAck != null) {
				Log.i(TAG, "MESHMS RECEIVED ACK"
						+ " senderSID=" + sender
						+ " recipientSID=" + recipient
						+ " millis=" + latestIncomingAck.messageTime
						+ " offset=" + latestIncomingAck.offset
					);
			}

			if (lastMessage != null) {
				// Append an ACK to the outgoing message log. But only if we have receieved more
				// messages -- don't just ack the file because we received a new ack...
				RhizomeAck ack = new RhizomeAck(
						incomingManifest.getManifestId(),
						incomingPayloadLength,
						lastMessage.millis);
				FileOutputStream fos = new FileOutputStream(outgoingPayloadFile, true); // append
				try {
					fos.write(new RhizomeMessageLogEntry(ack).toBytes());
					fos.getFD().sync();
				} catch (RhizomeMessageLogEntry.TooLongException e) {
					Log.e(Rhizome.TAG, "message is too long", e);
					return false;
				} finally {
					fos.close();
				}
				// Remove manifest fields that need to be rebuilt.
				RhizomeManifest_MeshMS outgoingManifest = null;
				if (outgoingManifestId != null) {
					outgoingManifest = RhizomeManifest_MeshMS.readFromFile(outgoingManifestFile);
					outgoingManifest.unsetFilesize();
					outgoingManifest.unsetFilehash();
					outgoingManifest.unsetVersion();
					outgoingManifest.unsetDateMillis();
				} else {
					outgoingManifest = new RhizomeManifest_MeshMS();
					outgoingManifest.setSender(self.sid);
					outgoingManifest.setRecipient(sender);
					outgoingManifest.setCrypt(1);
				}
				outgoingManifest.writeTo(outgoingManifestFile);
				Log.d(TAG, "rhizomeAddFile(" + outgoingPayloadFile + " (" + outgoingPayloadFile.length() + " bytes), " + outgoingManifest + ")");
				ServalD.rhizomeAddFile(outgoingPayloadFile,
						outgoingManifestFile, self.sid, null);
				// These INFO messages used for automated testing, do not change or remove!
				for (SimpleMeshMS sms: messages) {
					Log.i(TAG, "MESHMS RECEIVED"
							+ " senderSID=" + sms.sender
							+ " recipientSID=" + sms.recipient
							+ " senderDID=" + sms.senderDid
							+ " recipientDID=" + sms.recipientDid
							+ " millis=" + sms.timestamp
							+ " content=" + sms.content
						);
				}
				Log.i(TAG, "MESHMS SENT ACK"
						+ " senderSID=" + recipient
						+ " recipientSID=" + sender
						+ " millis=" + ack.messageTime
						+ " offset=" + ack.offset
					);
				IncomingMeshMS.addMessages(messages);
			}
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
			safeDelete(outgoingPayloadFile); // delete payload before manifest
			safeDelete(outgoingManifestFile);
		}
		return false;
	}

	/** Add a file (payload) to the rhizome store, creating a basic manifest for it.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean addFile(File path) {
		Log.d(TAG, "Rhizome.addFile(path=" + path + ")");
		try {
			RhizomeAddFileResult res = ServalD.rhizomeAddFile(path, null, Identity.getMainIdentity().sid, null);
			Log.d(TAG, "service=" + res.service);
			Log.d(TAG, "manifestId=" + res.manifestId);
			Log.d(TAG, "fileSize=" + res.fileSize);
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

	/** Unshare a file (payload) that already exists in the rhizome store, by setting
	 * its payload to empty.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean unshareFile(RhizomeManifest_File fileManifest) {
		Log.d(TAG, "Rhizome.unshareFile(" + fileManifest + ")");
		File manifestFile = null;
		try {
			File dir = getStageDirectoryCreated();
			manifestFile = File.createTempFile("unshare", ".manifest", dir);
			ServalD.rhizomeExtractManifest(fileManifest.getManifestId(), manifestFile);
			RhizomeManifest unsharedManifest = RhizomeManifest.readFromFile(manifestFile);
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
			unsharedManifest.writeTo(manifestFile);
			RhizomeAddFileResult res = ServalD.rhizomeAddFile(null, manifestFile, Identity.getMainIdentity().sid, null);
			Log.d(TAG, "service=" + res.service);
			Log.d(TAG, "manifestId=" + res.manifestId);
			Log.d(TAG, "fileSize=" + res.fileSize);
			Log.d(TAG, "fileHash=" + res.fileHash);
			return true;
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
		}
		catch (ServalDInterfaceError e) {
			Log.e(Rhizome.TAG, "servald interface is broken", e);
		}
		catch (RhizomeManifestServiceException e) {
			Log.e(Rhizome.TAG, "cannot build new manifest", e);
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

			// make sure the rhizome storage directory is on external
			// storage.
			if (enable) {
				try {
					File folder = Rhizome.getStorageDirectory();
					Log.v(TAG,
							"Enabling rhizome with database "
									+ folder.getAbsolutePath());
					ServalD.setConfig("rhizome.datastore_path",
							folder.getAbsolutePath());
				} catch (FileNotFoundException e) {
					enable = false;
					Log.v(TAG,
							"Disabling rhizome as external storage is not mounted");
				}
			} else
				Log.v(TAG, "Disabling rhizome");
			ServalD.delConfig("rhizome.enabled");
			ServalD.setConfig("rhizome.enable", enable ? "1" : "0");
			if (enable != alreadyEnabled)
				Control.reloadConfig();
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
		return createDirectory(getMeshmsStageDirectory());
	}

	public static RhizomeManifest readManifest(BundleId bid) throws ServalDFailureException, ServalDInterfaceError
	{
		return ServalD.rhizomeExtractManifest(bid, null).manifest;
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
				done = extractBundle(manifestId, savedManifestFile, savedPayloadFile);
			}
			finally {
				if (!done) {
					safeDelete(savedPayloadFile); // delete payload before manifest
					safeDelete(savedManifestFile);
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

	/** Predicate for deciding whether a given bundle is visible.
	 *
	 * Bundles for services other than "file" are hidden, eg, MeshMS.
	 *
	 * File bundles with names starting with "." are hidden.  File bundles missing a name are
	 * hidden.
	 *
	 * Kludge: file bundles generated by Serval Maps (well known name prefix/suffix) are also
	 * hidden.  TODO: replace this mechanism with one based on mime type.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean isVisible(RhizomeManifest manifest) {
		if (manifest instanceof RhizomeManifest_File) {
			RhizomeManifest_File fm = (RhizomeManifest_File) manifest;
			try {
				if (fm.getName().startsWith("."))
					return false;
				// TODO: replace following tests with mime-type test, once manifest carries
				// mime type information.
				if (fm.getName().endsWith(".smapp") || fm.getName().endsWith(".smapl"))
					return false;
				if (fm.getName().startsWith("smaps-photo-"))
					return false;
				return true;
			}
			catch (RhizomeManifest.MissingField e) {
				// File bundles with no name are hidden.
				return false;
			}
		}
		return false;
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

	/** Helper function for extracting a manifest and its payload file.
	 * Return true if the payload could be extracted, false if not (eg, because it is zero
	 * size).
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static boolean extractBundle(BundleId manifestId, File manifestFile, File payloadFile)
		throws RhizomeManifestSizeException, ServalDFailureException, ServalDInterfaceError
	{
		if (manifestFile.length() > RhizomeManifest.MAX_MANIFEST_BYTES)
			throw new RhizomeManifestSizeException(manifestFile, RhizomeManifest.MAX_MANIFEST_BYTES);
		RhizomeExtractManifestResult mres = ServalD.rhizomeExtractManifest(manifestId, manifestFile);
		if (mres.fileSize == 0)
			return false;
		RhizomeExtractFileResult fres = extractPayload(manifestId, payloadFile);
		if (mres.fileSize != fres.fileSize) {
			Log.w(Rhizome.TAG, "extracted file lengths differ: mres.fileSize=" + mres.fileSize + ", fres.fileSize=" + fres.fileSize);
			return false;
		}
		if (!mres.fileHash.equals(fres.fileHash)) {
			Log.w(Rhizome.TAG, "extracted file hash differ: mres.fileHash="
					+ mres.fileHash + ", fres.fileHash=" + fres.fileHash);
			return false;
		}
		return true;
	}

	/** Helper function for extracting the payload for a given manifest.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static RhizomeExtractFileResult extractPayload(
			BundleId manifestId, File payloadFile)
		throws ServalDFailureException, ServalDInterfaceError
	{
		RhizomeExtractFileResult fres = ServalD.rhizomeExtractFile(manifestId,
				payloadFile);
		return fres;
	}

	/** Helper function for extracting the payload for a given manifest.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static RhizomeExtractFileResult extractPayload(RhizomeManifest man, File payloadFile)
		throws RhizomeManifest.MissingField, ServalDFailureException, ServalDInterfaceError
	{
		RhizomeExtractFileResult fres = extractPayload(man.getManifestId(),
				payloadFile);
		try {
			long fileSize = man.getFilesize();
			if (fileSize != fres.fileSize)
				Log.w(Rhizome.TAG, "extracted file lengths differ: manifest.filesize=" + fileSize + ", fres.fileSize=" + fres.fileSize);
			FileHash fileHash = man.getFilehash();
			if (!fileHash.equals(fres.fileHash))
				Log.w(Rhizome.TAG,
						"extracted file hash inconsist: requested filehash="
								+ fileHash + ", got fres.fileHash="
								+ fres.fileHash);
		}
		catch (RhizomeManifest.MissingField e) {
			Log.w(Rhizome.TAG, "not checking filesize consistency", e);
		}
		return fres;
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
					if (Identity.getMainIdentity().sid.equals(meshms
							.getRecipient()))
						receiveMessageLog(meshms);
					else if (meshms.getRecipient().isBroadcast()) {
						// Message addressed to broadcast - so receive it
						// XXX - Eventually change this to allow subscription to messaging groups
						// and disable broadcast since it is not really what anyone wants
						Log.d(Rhizome.TAG, "receiving broadcast MeshMS");
						receiveMessageLog(meshms);
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

						String filename = file.getName();
						String ext = filename.substring(filename
								.lastIndexOf(".") + 1);
						String contentType = MimeTypeMap.getSingleton()
								.getMimeTypeFromExtension(ext);

						mBroadcastIntent.setDataAndType(Uri.parse("content://"
								+ RhizomeProvider.AUTHORITY + "/"
								+ file.getManifestId().toHex()), contentType);

						mBroadcastIntent.putExtras(file.asBundle());
						Log.v(TAG, "Sending broadcast for " + file.getDisplayName());
						ServalBatPhoneApplication.context.sendBroadcast(
								mBroadcastIntent,
								RECEIVE_PERMISSION);
					}
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}
}
