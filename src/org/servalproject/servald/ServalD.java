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
import java.util.AbstractList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.RhizomeManifest;
import org.servalproject.rhizome.RhizomeManifestParseException;

import android.net.Uri;
import android.os.Bundle;
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
	 * @param outv	A list to which the output fields will be appended using add()
	 * @param args	The words to pass on the command line (ie, argv[1]...argv[n])
	 * @return		The servald exit status code (normally 0 indicates success)
	 */
	private static native int rawCommand(List<String> outv, String[] args)
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

	public static synchronized int command(final ResultCallback callback,
			String... args) throws ServalDInterfaceError {
		if (log)
			Log.i(ServalD.TAG, "args = " + Arrays.deepToString(args));
		return rawCommand(new AbstractList<String>() {
			@Override
			public boolean add(String object) {
				if (log)
					Log.i(TAG, "Result = " + object);
				return callback.result(object);
			}
			@Override
			public String get(int location) {
				// TODO Auto-generated method stub
				return null;
			}
			@Override
			public int size() {
				// TODO Auto-generated method stub
				return 0;
			}
		}, args);
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

	public static synchronized ServalDResult command(String... args)
			throws ServalDInterfaceError {
		if (log)
			Log.i(ServalD.TAG, "args = " + Arrays.deepToString(args));
		LinkedList<String> outv = new LinkedList<String>();
		int status = rawCommand(outv, args);
		if (log) {
			Log.i(ServalD.TAG,
					"result = " + Arrays.deepToString(outv.toArray()));
			Log.i(ServalD.TAG, "status = " + status);
		}
		return new ServalDResult(args, status, outv.toArray(new String[0]));
	}

	/** Start the servald server process if it is not already running.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void serverStart(String execPath)
			throws ServalDFailureException, ServalDInterfaceError {
		ServalDResult result = command("start", "exec", execPath);
		result.failIfStatusError();
		started = System.currentTimeMillis();
		Log.i(ServalD.TAG, "server " + (result.status == 0 ? "started" : "already running") + ", pid=" + result.getFieldInt("pid"));
	}

	public static void serverStart() throws ServalDFailureException,
			ServalDInterfaceError {
		serverStart(ServalBatPhoneApplication.context.coretask.DATA_FILE_PATH
				+ "/bin/servald");
	}
	/** Stop the servald server process if it is running.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void serverStop() throws ServalDFailureException,
			ServalDInterfaceError {
		ServalDResult result = command("stop");
		started = -1;
		result.failIfStatusError();
		Log.i(ServalD.TAG, "server " + (result.status == 0 ? "stopped, pid=" + result.getFieldInt("pid") : "not running"));
	}

	/** Query the servald server process status.
	 *
	 * @return	True if the process is running
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean serverIsRunning() throws ServalDFailureException, ServalDInterfaceError {
		ServalDResult result = command("status");
		result.failIfStatusError();
		return result.status == 0;
	}

	public static long uptime() {
		if (started == -1)
			return -1;
		return System.currentTimeMillis() - started;
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
		int ret = rawCommand(new AbstractList<String>() {
			DnaResult nextResult;
			int resultNumber = 0;
			@Override
			public boolean add(String value) {
				if (log)
					Log.i(ServalD.TAG, "result = " + value);
				switch ((resultNumber++) % 3) {
				case 0:
					try {
						nextResult = new DnaResult(Uri.parse(value));
					} catch (Exception e) {
						Log.e(ServalD.TAG, "Unhandled dna response " + value, e);
						nextResult = null;
					}
					break;
				case 1:
					if (nextResult != null && nextResult.did == null)
						nextResult.did = value;
					break;
				case 2:
					if (nextResult != null) {
						nextResult.name = value;
						results.result(nextResult);
					}
					nextResult = null;
				}
				return true;
			}

			@Override
			public String get(int location) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public int size() {
				// TODO Auto-generated method stub
				return 0;
			}
		}, new String[] {
				"dna", "lookup", did, Integer.toString(timeout)
		});

		if (ret == ServalDResult.STATUS_ERROR)
			throw new ServalDFailureException("error exit status");
	}

	/** The result of a "rhizome list" operation.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static class RhizomeListResult extends ServalDResult {
		public final Map<String,Integer> columns;
		public final String[][] list;

		public RhizomeManifest toManifest(int i)
				throws RhizomeManifestParseException
		{
			Bundle b = new Bundle();
			for (Entry<String, Integer> entry : columns.entrySet()) {
				b.putString(entry.getKey(), list[i][entry.getValue()]);
			}
			return RhizomeManifest.fromBundle(b, null);
		}

		private RhizomeListResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
			try {
				int i = 0;
				final int ncol = Integer.decode(this.outv[i++]);
				if (ncol <= 0)
					throw new ServalDInterfaceError("no columns, ncol=" + ncol, this);
				final int nrows = (this.outv.length - 1) / ncol;
				if (nrows < 1)
					throw new ServalDInterfaceError("missing rows, nrows=" + nrows, this);
				final int properlength = nrows * ncol + 1;
				if (this.outv.length != properlength)
					throw new ServalDInterfaceError("incomplete row, outv.length should be " + properlength, this);
				int row, col;
				this.columns = new HashMap<String,Integer>(ncol);
				for (col = 0; col != ncol; ++col)
					this.columns.put(this.outv[i++], col);
				this.list = new String[nrows - 1][ncol];
				for (row = 0; row != this.list.length; ++row)
					for (col = 0; col != ncol; ++col)
						this.list[row][col] = this.outv[i++];
				if (i != this.outv.length)
					throw new ServalDInterfaceError("logic error, i=" + i + ", outv.length=" + this.outv.length, this);
			}
			catch (IndexOutOfBoundsException e) {
				throw new ServalDInterfaceError(result, e);
			}
			catch (IllegalArgumentException e) {
				throw new ServalDInterfaceError(result, e);
			}
		}
	}

	/** The result of any rhizome operation that involves a payload.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static class PayloadResult extends ServalDResult {

		public final String fileHash;
		public final long fileSize;

		/** Copy constructor. */
		protected PayloadResult(PayloadResult orig) {
			super(orig);
			this.fileHash = orig.fileHash;
			this.fileSize = orig.fileSize;
		}

		/** Unpack a result from a rhizome operation that describes a payload file.
		*
		* @param result		The result object returned by the operation.
		*
		* @author Andrew Bettison <andrew@servalproject.com>
		*/
		protected PayloadResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
			try {
				this.fileSize = getFieldLong("filesize");
				this.fileHash = this.fileSize != 0 ? getFieldString("filehash") : null;
			}
			catch (IllegalArgumentException e) {
				throw new ServalDInterfaceError(result, e);
			}
		}

	}

	/**
	 * Add a payload file to the rhizome store, with author identity (SID).
	 *
	 * @param path 			The path of the file containing the payload.  The name is taken from the
	 * 						path's basename.  If path is null, then it means an empty payload, and
	 * 						the name is empty also.
	 * @param author 		The SID of the author or null.  If a SID is supplied, then bundle's
	 * 						secret key will be encoded into the manifest (in the BK field) using the
	 * 						author's rhizome secret, so that the author can update the file in
	 * 						future.  If no SID is provided, then the bundle carries no BK field, so
	 * 						the author will be unable to update the manifest with a new payload (ie,
	 * 						make a new version of the same bundle) unless she retains the bundle's
	 * 						secret key herself.
	 * @param pin 			The pin to unlock the author's rhizome secret.
	 * @return				PayloadResult
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeAddFileResult rhizomeAddFile(File payloadPath, File manifestPath, SubscriberId author, String pin)
		throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = command("rhizome", "add", "file",
				author == null ? "" : author.toHex().toUpperCase(),
										pin != null ? pin : "",
										payloadPath != null ? payloadPath.getAbsolutePath() : "",
										manifestPath != null ? manifestPath.getAbsolutePath() : ""
									);
		if (result.status != 0 && result.status != 2)
			throw new ServalDFailureException("exit status indicates failure", result);
		return new RhizomeAddFileResult(result);
	}

	public static class RhizomeAddFileResult extends PayloadResult {

		public final String service;
		public final BundleId manifestId;

		RhizomeAddFileResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
			this.service = getFieldString("service");
			this.manifestId = getFieldBundleId("manifestid");
		}

	}

	public interface ManifestResult {
		public void manifest(Bundle b);
	}

	public static synchronized void rhizomeListAsync(String service, SubscriberId sender,
			SubscriberId recipient, int offset, int limit,
			final ManifestResult results) {
		List<String> args = new LinkedList<String>();
		args.add("rhizome");
		args.add("list");
		args.add(""); // list of comma-separated PINs
		args.add(service == null ? "" : service);
		args.add(sender == null ? "" : sender.toHex().toUpperCase());
		args.add(recipient == null ? "" : recipient.toHex().toUpperCase());
		if (limit >= 0) {
			if (offset < 0)
				offset = 0;
			args.add(Integer.toString(offset));
			args.add(Integer.toString(limit));
		} else if (offset >= 0) {
			args.add(Integer.toString(offset));
		}
		rawCommand(new AbstractList<String>() {
			int state = 0;
			int columns = 0;
			int column;
			String names[];

			Bundle b = new Bundle();

			@Override
			public boolean add(String value) {
				try {
					if (log)
						Log.i(ServalD.TAG, "result = " + value);
					switch (state) {
					case 0:
						columns = Integer.parseInt(value);
						names = new String[columns];
						column = 0;
						state = 1;
						break;
					case 1:
						names[column++] = value;
						if (column >= columns) {
							column = 0;
							state = 2;
						}
						break;
					case 2:
						b.putString(names[column++], value);
						if (column >= columns) {
							column = 0;
							results.manifest(b);
							b.clear();
						}
						break;
					}
					return true;
				}
				catch (Exception e) {
					Log.e(ServalD.TAG, e.getMessage(), e);
					return false;
				}
			}

			@Override
			public String get(int location) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public int size() {
				// TODO Auto-generated method stub
				return 0;
			}
		}, args.toArray(new String[args.size()]));
	}

	/**
	 * Return a list of manifests currently in the Rhizome store.
	 *
	 * @param service	If non-null, then all found manifests will have the given service type, eg,
	 * 					"file", "MeshMS"
	 * @param sender	If non-null, then all found manifests will have the given sender SID
	 * @param recipient	If non-null, then all found manifests will have the given recipient SID
	 * @param offset	Ignored if negative, otherwise passed to the SQL SELECT query in the OFFSET
	 * 					clause.
	 * @param limit 	Ignored if negative, otherwise passed to the SQL SELECT query in the LIMIT
	 * 					clause.
	 * @return			Array of rows, first row contains column labels.  Each row is an array of
	 * 					strings, all rows have identical array length.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeListResult rhizomeList(String service, SubscriberId sender, SubscriberId recipient, int offset, int limit)
		throws ServalDFailureException, ServalDInterfaceError
	{
		List<String> args = new LinkedList<String>();
		args.add("rhizome");
		args.add("list");
		args.add(""); // list of comma-separated PINs
		args.add(service == null ? "" : service);
		args.add(sender == null ? "" : sender.toHex().toUpperCase());
		args.add(recipient == null ? "" : recipient.toHex().toUpperCase());
		if (limit >= 0) {
			if (offset < 0)
				offset = 0;
			args.add(Integer.toString(offset));
			args.add(Integer.toString(limit));
		} else if (offset >= 0) {
			args.add(Integer.toString(offset));
		}
		ServalDResult result = command(args.toArray(new String[args.size()]));
		result.failIfStatusNonzero();
		return new RhizomeListResult(result);
	}

	/**
	 * Extract a manifest into a file at the given path.
	 *
	 * @param manifestId	The manifest ID of the manifest to extract.
	 * @param path 			The path of the file into which the manifest is to be written.
	 * @return				RhizomeExtractManifestResult
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeExtractManifestResult rhizomeExtractManifest(BundleId manifestId, File path) throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = command("rhizome", "extract", "manifest", manifestId.toString(), path.getAbsolutePath());
		result.failIfStatusNonzero();
		return new RhizomeExtractManifestResult(result);
	}

	public static class RhizomeExtractManifestResult extends PayloadResult {
		public final String service;
		RhizomeExtractManifestResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
			this.service = getFieldString("service");
		}
	}

	/**
	 * Extract a payload file into a file at the given path.
	 *
	 * @param fileHash	The hash (file ID) of the file to extract.
	 * @param path 		The path of the file into which the payload is to be written.
	 * @return			RhizomeExtractFileResult
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeExtractFileResult rhizomeExtractFile(String fileHash, File path) throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = command("rhizome", "extract", "file", fileHash, path.getAbsolutePath());
		result.failIfStatusNonzero();
		return new RhizomeExtractFileResult(result);
	}

	public static boolean isRhizomeEnabled() {
		ServalDResult result = command("rhizome", "enabled");
		return result.status == 0 && "true".equals(result.outv[0]);
	}

	public static class RhizomeExtractFileResult extends PayloadResult {
		RhizomeExtractFileResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
		}
	}

}
