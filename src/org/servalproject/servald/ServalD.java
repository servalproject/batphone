package org.servalproject.servald;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.io.File;

import android.util.Log;

/**
 * Low-level class for invoking servald JNI command-line operations.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class ServalD
{

	public static final String TAG = "ServalD";

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
	private static native int rawCommand(List<String> outv, String[] args);

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

	public static synchronized int command(final ResultCallback callback, String... args) {
		return rawCommand(new AbstractList<String>() {
			@Override
			public boolean add(String object) {
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
	{
		Log.i(ServalD.TAG, "args = " + Arrays.deepToString(args));
		LinkedList<String> outv = new LinkedList<String>();
		int status = rawCommand(outv, args);
		Log.i(ServalD.TAG, "status = " + status);
		return new ServalDResult(args, status, outv.toArray(new String[0]));
	}

	/**
	 * Return a list of file manifests currently in the Rhizome store.
	 *
	 * @param offset	Ignored if negative, otherwise passed to the SQL SELECT query in the OFFSET
	 * 					clause.
	 * @param limit 	Ignored if negative, otherwise passed to the SQL SELECT query in the LIMIT
	 * 					clause.
	 * @return			Array of rows, first row contains column labels.  Each row is an array of
	 * 					strings, all rows have identical array length.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeListResult rhizomeList(int offset, int limit) throws ServalDFailureException, ServalDInterfaceError
	{
		List<String> args = new LinkedList<String>();
		args.add("rhizome");
		args.add("list");
		if (limit >= 0) {
			if (offset < 0)
				offset = 0;
			args.add(Integer.toString(offset));
			args.add(Integer.toString(limit));
		} else if (offset >= 0) {
			args.add(Integer.toString(offset));
		}
		ServalDResult result = command(args.toArray(new String[args.size()]));
		if (result.status != 0) {
			throw new ServalDFailureException("non-zero exit status", result);
		}
		try {
			int i = 0;
			final int ncol = Integer.decode(result.outv[i++]);
			if (ncol <= 0)
				throw new ServalDInterfaceError("no columne, ncol=" + ncol, result);
			final int nrows = (result.outv.length - 1) / ncol;
			if (nrows < 1)
				throw new ServalDInterfaceError("missing rows, nrows=" + nrows, result);
			final int properlength = nrows * ncol + 1;
			if (result.outv.length != properlength)
				throw new ServalDInterfaceError("incomplete row, outv.length should be " + properlength, result);
			String[][] ret = new String[nrows][ncol];
			int row, col;
			for (row = 0; row != nrows; ++row)
				for (col = 0; col != ncol; ++col)
					ret[row][col] = result.outv[i++];
			return new RhizomeListResult(result, ret);
		}
		catch (IndexOutOfBoundsException e) {
			throw new ServalDInterfaceError(result, e);
		}
		catch (IllegalArgumentException e) {
			throw new ServalDInterfaceError(result, e);
		}
	}

	public static class RhizomeListResult extends ServalDResult {
		public final String[][] list;
		private RhizomeListResult(ServalDResult result, String[][] list) {
			super(result);
			this.list = list;
		}
	}

	/**
	 * Extract a manifest or payload into a file at the given path.
	 *
	 * @param what			Either "manifest" or "file"
	 * @param id			The manifest ID or file ID (hash) of the object to extract.
	 * @param path 			The path of the file into which the object is to be written.
	 * @return				RhizomeExtractResult
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static RhizomeExtractResult rhizomeExtract(String what, String id, File path) throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = command("rhizome", "extract", what, id, path.getAbsolutePath());
		if (result.status != 0) {
			throw new ServalDFailureException("non-zero exit status", result);
		}
		try {
			if (result.outv.length % 2 != 0)
				throw new ServalDInterfaceError("odd number of fields", result);
			String fileHash = null;
			long fileSize = -1;
			int i;
			for (i = 0; i != result.outv.length; i += 2) {
				if (result.outv[i].equals("filehash"))
					fileHash = result.outv[i + 1];
				else if (result.outv[i].equals("filesize"))
					fileSize = Long.parseLong(result.outv[i + 1]);
			}
			if (fileHash == null)
				throw new ServalDInterfaceError("missing filehash field", result);
			if (fileSize == -1)
				throw new ServalDInterfaceError("missing filesize field", result);
			return new RhizomeExtractResult(result, fileHash, fileSize);
		}
		catch (IndexOutOfBoundsException e) {
			throw new ServalDInterfaceError(result, e);
		}
		catch (IllegalArgumentException e) {
			throw new ServalDInterfaceError(result, e);
		}
	}

	protected static class RhizomeExtractResult extends ServalDResult {
		public final String fileHash;
		public final long fileSize;
		protected RhizomeExtractResult(ServalDResult result, String fileHash, long fileSize) {
			super(result);
			this.fileHash = fileHash;
			this.fileSize = fileSize;
		}
		protected RhizomeExtractResult(RhizomeExtractResult orig) {
			this(orig, orig.fileHash, orig.fileSize);
		}
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
	public static RhizomeExtractManifestResult rhizomeExtractManifest(String manifestId, File path) throws ServalDFailureException, ServalDInterfaceError
	{
		return new RhizomeExtractManifestResult(rhizomeExtract("manifest", manifestId, path));
	}

	public static class RhizomeExtractManifestResult extends RhizomeExtractResult {
		RhizomeExtractManifestResult(RhizomeExtractResult orig) {
			super(orig);
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
		return new RhizomeExtractFileResult(rhizomeExtract("file", fileHash, path));
	}

	public static class RhizomeExtractFileResult extends RhizomeExtractResult {
		RhizomeExtractFileResult(RhizomeExtractResult orig) {
			super(orig);
		}
	}

}
