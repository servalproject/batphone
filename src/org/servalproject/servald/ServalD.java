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

package org.servalproject.servald;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.RhizomeManifest;
import org.servalproject.rhizome.RhizomeManifestParseException;

import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * Low-level class for invoking servald JNI command-line operations.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class ServalD
{

	public static final String TAG = "ServalD";
	private static long started = -1;
	static boolean log = false;

	private ServalD() {
	}

	static {
		System.loadLibrary("serval");
	}

	/**
	 * Low-level JNI entry point into servald command line.
	 *
	 * @param outv
	 *            A list to which the output fields will be appended using add()
	 * @param args
	 *            The words to pass on the command line (ie, argv[1]...argv[n])
	 * @return The servald exit status code (normally 0 indicates success)
	 */
	private static native int rawCommand(IJniResults outv, String[] args)
			throws ServalDInterfaceError;

	/**
	 * Common entry point into servald command line.
	 *
	 * @param callback
	 *            Each result will be passed to callback.result(String)
	 *            immediately.
	 * @param args
	 *            The parameters as passed on the command line, eg: res =
	 *            servald.command("config", "set", "debug", "peers");
	 * @return The servald exit status code (normally0 indicates success)
	 */
	private static synchronized int command(final IJniResults callback,
			String... args)
			throws ServalDInterfaceError
	{
		if (log)
			Log.i(ServalD.TAG, "args = " + Arrays.deepToString(args));
		return rawCommand(callback, args);
	}

	/**
	 * Common entry point into servald command line.
	 *
	 * @param args
	 *            The parameters as passed on the command line, eg: res =
	 *            servald.command("config", "set", "debug", "peers");
	 * @return An object containing the servald exit status code (normally0
	 *         indicates success) and zero or more output fields that it would
	 *         have sent to standard output if invoked via a shell command line.
	 */

	private static synchronized ServalDResult command(String... args)
			throws ServalDInterfaceError
	{
		if (log)
			Log.i(ServalD.TAG, "args = " + Arrays.deepToString(args));
		LinkedList<byte[]> outv = new LinkedList<byte[]>();
		int status = rawCommand(new JniResultsList(outv), args);
		if (log) {
			LinkedList<String> outvstr = new LinkedList<String>();
			for (byte[] a : outv)
				outvstr.add(a == null ? null : new String(a));
			Log.i(ServalD.TAG,
					"result = " + Arrays.deepToString(outvstr.toArray()));
			Log.i(ServalD.TAG, "status = " + status);
		}
		return new ServalDResult(args, status, outv.toArray(new byte[outv
				.size()][]));
	}

	/**
	 * Start the servald server process if it is not already running.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void serverStart(String execPath)
			throws ServalDFailureException, ServalDInterfaceError {
		ServalDResult result = command("start", "exec", execPath);
		result.failIfStatusError();
		started = System.currentTimeMillis();
		Log.i(ServalD.TAG, "server "
				+ (result.status == 0 ? "started" : "already running")
				+ ", pid=" + result.getFieldInt("pid"));
	}

	public static void serverStart() throws ServalDFailureException,
			ServalDInterfaceError {
		serverStart(ServalBatPhoneApplication.context.coretask.DATA_FILE_PATH
				+ "/bin/servald");
	}

	/**
	 * Stop the servald server process if it is running.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void serverStop() throws ServalDFailureException,
			ServalDInterfaceError {
		ServalDResult result = command("stop");
		started = -1;
		result.failIfStatusError();
		Log.i(ServalD.TAG,
				"server "
						+ (result.status == 0 ? "stopped, pid="
								+ result.getFieldInt("pid") : "not running"));
	}

	/**
	 * Query the servald server process status.
	 *
	 * @return True if the process is running
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean serverIsRunning() throws ServalDFailureException,
			ServalDInterfaceError {
		ServalDResult result = command("status");
		result.failIfStatusError();
		return result.status == 0;
	}

	public static long uptime() {
		if (started == -1)
			return -1;
		return System.currentTimeMillis() - started;
	}

	/**
	 * The result of a lookup operation.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static class LookupResult extends ServalDResult {
		public final SubscriberId subscriberId;
		public final String did;
		public final String name;
		public final String profileData;

		/** Copy constructor. */
		protected LookupResult(LookupResult orig) {
			super(orig);
			this.subscriberId = orig.subscriberId;
			this.did = orig.did;
			this.name = orig.name;
			this.profileData = orig.profileData;
		}

		/**
		 * Unpack a result from a keyring add operation.
		 *
		 * @param result
		 *            The result object returned by the operation.
		 *
		 * @author Andrew Bettison <andrew@servalproject.com>
		 */
		protected LookupResult(ServalDResult result)
				throws ServalDInterfaceError {
			super(result);
			if (result.status == 0) {
				this.subscriberId = getFieldSubscriberId("sid");
				this.did = getFieldStringNonEmptyOrNull("did");
				this.name = getFieldStringNonEmptyOrNull("name");
				this.profileData = getFieldStringNonEmptyOrNull("profileData");
			} else {
				this.subscriberId = null;
				this.did = null;
				this.name = null;
				this.profileData = null;
			}
		}
	}

	/**
	 * The result of a keyring add operation.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static class KeyringAddResult extends LookupResult {
		/** Copy constructor. */
		protected KeyringAddResult(KeyringAddResult orig) {
			super(orig);
		}

		/**
		 * Unpack a result from a keyring add operation.
		 *
		 * @param result
		 *            The result object returned by the operation.
		 *
		 * @author Andrew Bettison <andrew@servalproject.com>
		 */
		protected KeyringAddResult(ServalDResult result)
				throws ServalDInterfaceError {
			super(result);
		}
	}

	public static KeyringAddResult keyringAdd()
			throws ServalDFailureException,
			ServalDInterfaceError
	{
		ServalDResult result = command("keyring", "add");
		result.failIfStatusError();
		return new KeyringAddResult(result);
	}

	public static KeyringAddResult keyringSetDidName(SubscriberId sid,
			String did, String name, String profileData)
			throws ServalDFailureException, ServalDInterfaceError
	{
		List<String> args = new LinkedList<String>();
		args.add("keyring");
		args.add("set");
		args.add("did");
		args.add(sid.toHex().toUpperCase());
		if (did != null)
			args.add(did);
		else if (name != null)
			args.add("");
		if (name != null)
			args.add(name);
		if (profileData != null)
			args.add(profileData);
		ServalDResult result = command(args.toArray(new String[args.size()]));
		result.failIfStatusError();
		return new KeyringAddResult(result);
	}

	/**
	 * The result of a keyring list.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static class KeyringListResult extends ServalDResult {
		static class Entry {
			public final SubscriberId subscriberId;
			public final String did;
			public final String name;
			public final String profileData;

			protected Entry(SubscriberId sid, String did, String name,
					String profileData) {
				this.subscriberId = sid;
				this.did = did;
				this.name = name;
				this.profileData = profileData;
			}
		}

		public final Entry[] entries;

		/** Copy constructor. */
		protected KeyringListResult(KeyringListResult orig) {
			super(orig);
			this.entries = orig.entries;
		}

		/**
		 * Unpack a result from a keyring list output.
		 *
		 * @param result
		 *            The result object returned by the operation.
		 *
		 * @author Andrew Bettison <andrew@servalproject.com>
		 */
		protected KeyringListResult(ServalDResult result)
				throws ServalDInterfaceError {
			super(result);
			Log.e("ServalD.outv.length", "" + this.outv.length);
			/*
			 * Log.e("ServalD.outv.0", "" + this.outv[0]);
			 * Log.e("ServalD.outv.1", "" + this.outv[1]);
			 * Log.e("ServalD.outv.2", "" + this.outv[2]);
			 */
			if (this.outv.length % 4 != 0)
				throw new ServalDInterfaceError("invalid number of fields "
						+ this.outv.length + " (not multiple of 4)", this);
			Entry[] entries = new Entry[this.outv.length / 4];
			for (int i = 0; i != this.outv.length; i += 4)
				try {
					entries[i / 4] = new Entry(
							new SubscriberId(new String(this.outv[i])),
							this.outv[i + 1].length != 0 ? new String(
									this.outv[i + 1]) : null,
							this.outv[i + 2].length != 0 ? new String(
									this.outv[i + 2]) : null,
							this.outv[i + 3].length != 0 ? new String(
									this.outv[i + 3]) : null
							);
				} catch (SubscriberId.InvalidHexException e) {
					throw new ServalDInterfaceError(
							"invalid output field outv[" + i + "]", this, e);
				}
			this.entries = entries;
		}
	}

	public static KeyringListResult keyringList()
			throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = command("keyring", "list");
		result.failIfStatusError();
		return new KeyringListResult(result);
	}

	public static void dnaLookup(final LookupResults results, String did)
			throws ServalDFailureException, ServalDInterfaceError {
		dnaLookup(results, did, 3000);
	}

	public static synchronized void dnaLookup(final LookupResults results,
			String did, int timeout) throws ServalDFailureException,
			ServalDInterfaceError {
		if (log)
			Log.i(ServalD.TAG, "args = [dna, lookup, " + did + "]");
		int ret = rawCommand(new AbstractJniResults() {
			DnaResult nextResult;
			int resultNumber = 0;

			@Override
			public void putBlob(byte[] value) {
				String str = value == null ? "" : new String(value);
				if (log)
					Log.i(ServalD.TAG, "result = " + str);
				switch ((resultNumber++) % 3) {
				case 0:
					try {
						nextResult = new DnaResult(Uri.parse(str));
					} catch (Exception e) {
						Log.e(ServalD.TAG, "Unhandled dna response " + str, e);
						nextResult = null;
					}
					break;
				case 1:
					if (nextResult != null && nextResult.did == null)
						nextResult.did = str;
					break;
				case 2:
					if (nextResult != null) {
						nextResult.name = str;
						results.result(nextResult);
					}
					nextResult = null;
				}
			}
		}, new String[] {
				"dna", "lookup", did, Integer.toString(timeout)
		});

		if (ret == ServalDResult.STATUS_ERROR)
			throw new ServalDFailureException("error exit status");
	}

	/**
	 * The result of any rhizome operation that involves a payload.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static class PayloadResult extends ServalDResult {

		public final FileHash fileHash;
		public final long fileSize;

		/** Copy constructor. */
		protected PayloadResult(PayloadResult orig) {
			super(orig);
			this.fileHash = orig.fileHash;
			this.fileSize = orig.fileSize;
		}

		/**
		 * Unpack a result from a rhizome operation that describes a payload
		 * file.
		 *
		 * @param result
		 *            The result object returned by the operation.
		 *
		 * @author Andrew Bettison <andrew@servalproject.com>
		 */
		protected PayloadResult(ServalDResult result)
				throws ServalDInterfaceError {
			super(result);
			this.fileSize = getFieldLong("filesize");
			this.fileHash = this.fileSize != 0 ? getFieldFileHash("filehash")
					: null;
		}

	}

	/**
	 * Add a payload file to the rhizome store, with author identity (SID).
	 *
	 * @param path
	 *            The path of the file containing the payload. The name is taken
	 *            from the path's basename. If path is null, then it means an
	 *            empty payload, and the name is empty also.
	 * @param author
	 *            The SID of the author or null. If a SID is supplied, then
	 *            bundle's secret key will be encoded into the manifest (in the
	 *            BK field) using the author's rhizome secret, so that the
	 *            author can update the file in future. If no SID is provided,
	 *            then the bundle carries no BK field, so the author will be
	 *            unable to update the manifest with a new payload (ie, make a
	 *            new version of the same bundle) unless she retains the
	 *            bundle's secret key herself.
	 * @param pin
	 *            The pin to unlock the author's rhizome secret.
	 * @return PayloadResult
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeAddFileResult rhizomeAddFile(File payloadPath,
			File manifestPath, SubscriberId author, String pin)
			throws ServalDFailureException, ServalDInterfaceError
	{
		List<String> args = new LinkedList<String>();
		args.add("rhizome");
		args.add("add");
		args.add("file");
		if (pin != null) {
			args.add("--entry-pin");
			args.add(pin);
		}
		args.add(author == null ? "" : author.toHex().toUpperCase());
		if (payloadPath != null)
			args.add(payloadPath.getAbsolutePath());
		else if (manifestPath != null)
			args.add("");
		if (manifestPath != null)
			args.add(manifestPath.getAbsolutePath());
		ServalDResult result = command(args.toArray(new String[args.size()]));
		if (result.status != 0 && result.status != 2)
			throw new ServalDFailureException("exit status indicates failure",
					result);
		return new RhizomeAddFileResult(result);
	}

	public static class RhizomeAddFileResult extends RhizomeManifestResult {
		RhizomeAddFileResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
		}
	}

	public static RhizomeManifestResult rhizomeImportBundle(File payloadFile,
			File manifestFile) throws ServalDFailureException,
			ServalDInterfaceError {
		ServalDResult result = command("rhizome", "import", "bundle",
				payloadFile.getAbsolutePath(), manifestFile.getAbsolutePath());
		result.failIfStatusError();
		RhizomeManifestResult ret = new RhizomeManifestResult(result);
		return ret;
	}

	public static int rhizomeListRaw(final IJniResults callback,
			String service, String name, SubscriberId sender,
			SubscriberId recipient, int offset, int limit)
			throws ServalDFailureException
	{
		List<String> args = new LinkedList<String>();
		args.add("rhizome");
		args.add("list");
		args.add(service == null ? "" : service);
		args.add(name == null ? "" : name);
		args.add(sender == null ? "" : sender.toHex().toUpperCase());
		args.add(recipient == null ? "" : recipient.toHex().toUpperCase());
		if (offset > 0)
			args.add("" + offset);
		else if (limit > 0)
			args.add("0");
		if (limit > 0)
			args.add("" + limit);
		int ret = command(callback, args.toArray(new String[args.size()]));
		if (ret == ServalDResult.STATUS_ERROR)
			throw new ServalDFailureException("error exit status");
		return ret;
	}

	public static Cursor rhizomeList(String service, String name,
			SubscriberId sender, SubscriberId recipient)
			throws ServalDFailureException, ServalDInterfaceError
	{
		return new ServalDCursor(service, name, sender, recipient);
	}

	public static RhizomeExtractManifestResult rhizomeExtractBundle(
			BundleId manifestId, File manifestFile, File payloadFile)
			throws ServalDFailureException, ServalDInterfaceError {
		ServalDResult r = ServalD.command("rhizome", "extract", "bundle",
				manifestId.toHex(),
				manifestFile == null ? "-" : manifestFile.getAbsolutePath(),
				payloadFile.getAbsolutePath());
		r.failIfStatusNonzero();
		RhizomeExtractManifestResult ret = new RhizomeExtractManifestResult(r);
		if (manifestFile == null && ret.manifest == null)
			throw new ServalDInterfaceError("missing manifest", ret);
		return ret;
	}

	/**
	 * Export a manifest into a file at the given path.
	 *
	 * @param manifestId
	 *            The manifest ID of the manifest to extract.
	 * @param path
	 *            The path of the file into which the manifest is to be written.
	 * @return RhizomeExtractManifestResult
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeExtractManifestResult rhizomeExportManifest(
			BundleId manifestId, File path) throws ServalDFailureException,
			ServalDInterfaceError
	{
		List<String> args = new LinkedList<String>();
		args.add("rhizome");
		args.add("export");
		args.add("manifest");
		args.add(manifestId.toString());
		if (path == null)
			args.add("-");
		else
			args.add(path.getAbsolutePath());
		ServalDResult result = command(args.toArray(new String[args.size()]));
		result.failIfStatusNonzero();
		RhizomeExtractManifestResult mresult = new RhizomeExtractManifestResult(
				result);
		if (path == null && mresult.manifest == null)
			throw new ServalDInterfaceError("missing manifest", mresult);
		return mresult;
	}

	public static class RhizomeManifestResult extends PayloadResult {
		public final String service;
		public final RhizomeManifest manifest;
		public final BundleId manifestId;
		public final long version;

		RhizomeManifestResult(ServalDResult result)
				throws ServalDInterfaceError {
			super(result);
			this.version = getFieldLong("version");
			this.service = getFieldString("service");
			this.manifestId = getFieldBundleId("manifestid");
			byte[] manifestBytes = getFieldByteArray("manifest", null);
			if (manifestBytes != null) {
				try {
					this.manifest = RhizomeManifest
							.fromByteArray(manifestBytes);
				} catch (RhizomeManifestParseException e) {
					throw new ServalDInterfaceError("invalid manifest", result,
							e);
				}
			}
			else
				this.manifest = null;
		}
	}

	public static class RhizomeExtractManifestResult extends
			RhizomeManifestResult {
		public final boolean _readOnly;
		public final SubscriberId _author;

		RhizomeExtractManifestResult(ServalDResult result)
				throws ServalDInterfaceError {
			super(result);
			this._readOnly = getFieldBoolean(".readonly");
			this._author = getFieldSubscriberId(".author", null);
		}
	}

	/**
	 * Extract a payload file into a file at the given path.
	 *
	 * @param fileHash
	 *            The hash (file ID) of the file to extract.
	 * @param path
	 *            The path of the file into which the payload is to be written.
	 * @return RhizomeExtractFileResult
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeExtractFileResult rhizomeExtractFile(BundleId bid,
			File path) throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = command("rhizome", "extract", "file",
				bid.toHex(), path.getAbsolutePath());
		result.failIfStatusNonzero();
		return new RhizomeExtractFileResult(result);
	}

	/**
	 * Push Rhizome bundles to all configured direct hosts.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void rhizomeDirectPush() throws ServalDFailureException,
			ServalDInterfaceError
	{
		ServalDResult result = command("rhizome", "direct", "push");
		result.failIfStatusNonzero();
	}

	/**
	 * Pull Rhizome bundles from all configured direct hosts.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void rhizomeDirectPull() throws ServalDFailureException,
			ServalDInterfaceError
	{
		ServalDResult result = command("rhizome", "direct", "pull");
		result.failIfStatusNonzero();
	}

	/**
	 * Sync (push and pull) Rhizome bundles from all configured direct hosts.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void rhizomeDirectSync() throws ServalDFailureException,
			ServalDInterfaceError
	{
		ServalDResult result = command("rhizome", "direct", "sync");
		result.failIfStatusNonzero();
	}

	// copies the semantics of serval-dna's confParseBoolean
	private static boolean parseBoolean(String value, boolean defaultValue) {
		if (value == null || "".equals(value))
			return defaultValue;
		return "off".compareToIgnoreCase(value) != 0
				&& "no".compareToIgnoreCase(value) != 0
				&& "false".compareToIgnoreCase(value) != 0
				&& "0".compareToIgnoreCase(value) != 0;
	}

	public static class ConfigOption {
		final String var;
		final String value;

		private ConfigOption(String var, String value) {
			this.var = var;
			this.value = value;
		}
	}

	public static ConfigOption[] getConfigOptions(String pattern) {
		List<String> args = new LinkedList<String>();
		args.add("config");
		args.add("get");
		if (pattern != null)
			args.add(pattern);
		ServalDResult result = command(args.toArray(new String[args.size()]));
		Map<String, byte[]> vars = result.getKeyValueMap();
		List<ConfigOption> colist = new LinkedList<ConfigOption>();
		for (Map.Entry<String, byte[]> ent : vars.entrySet())
			colist.add(new ConfigOption(ent.getKey(),
					new String(ent.getValue())));
		return colist.toArray(new ConfigOption[0]);
	}

	public static String getConfig(String name) {
		String ret = null;
		ServalDResult result = command("config", "get", name);
		if (result.status == 0 && result.outv.length >= 2
				&& name.equals(new String(result.outv[0])))
			ret = new String(result.outv[1]);
		return ret;
	}

	public static void delConfig(String name) throws ServalDFailureException {
		ServalDResult result = command("config", "del", name);
		if (result.status != 2)
			result.failIfStatusNonzero();
	}

	public static void setConfig(String name, String value)
			throws ServalDFailureException {
		ServalDResult result = command("config", "set", name, value);
		if (result.status != 2)
			result.failIfStatusNonzero();
	}

	public static boolean getConfigBoolean(String name, boolean defaultValue) {
		String value = getConfig(name);
		return parseBoolean(value, defaultValue);
	}

	public static int getConfigInt(String name, int defaultValue) {
		String value = getConfig(name);
		return value == null ? defaultValue : Integer.parseInt(value);
	}

	public static boolean isRhizomeEnabled() {
		return getConfigBoolean("rhizome.enable", true);
	}

	public static class RhizomeExtractFileResult extends PayloadResult {
		RhizomeExtractFileResult(ServalDResult result)
				throws ServalDInterfaceError {
			super(result);
		}
	}

	public static int getPeerCount() throws ServalDFailureException {
		ServalDResult result = ServalD.command("peer", "count");
		result.failIfStatusError();
		return Integer.parseInt(new String(result.outv[0]));
	}

	public static int peers(final IJniResults callback)
			throws ServalDInterfaceError
	{
		return command(callback, "id", "peers");
	}

	public static LookupResult reverseLookup(SubscriberId sid)
			throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = ServalD.command("reverse", "lookup", sid.toHex()
				.toUpperCase());
		result.failIfStatusError();
		return new LookupResult(result);
	}

}
