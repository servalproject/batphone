package org.servalproject.servald;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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

	public static synchronized int command(final ResultCallback callback,
			String... args) {
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
		return new ServalDResult(status, outv.toArray(new String[0]));
	}

	public static synchronized void dnaLookup(final LookupResults results,
			String did) {
		rawCommand(new AbstractList<String>() {
			DidResult nextResult;
			int resultNumber = 0;
			@Override
			public boolean add(String value) {
				switch ((resultNumber++) % 3) {
				case 0:
					nextResult = new DidResult();
					nextResult.sid = new SubscriberId(value);
					break;
				case 1:
					nextResult.did = value;
					break;
				case 2:
					nextResult.name = value;
					results.result(nextResult);
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
				"dna", "lookup", did
		});
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
	public static String[][] rhizomeList(int offset, int limit) throws ServalDFailureException, ServalDInterfaceError
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
			throw new ServalDFailureException("return status = " + result.status);
		}
		try {
			int i = 0;
			final int ncol = Integer.decode(result.outv[i++]);
			if (ncol <= 0)
				throw new ServalDInterfaceError("illegal column count = " + ncol);
			final int nrows = (result.outv.length - 1) / ncol;
			if (nrows < 1)
				throw new ServalDInterfaceError("missing rows, outv.length = " + result.outv.length + ", nrows = " + nrows);
			final int properlength = nrows * ncol + 1;
			if (result.outv.length != properlength)
				throw new ServalDInterfaceError("incomplete row, outv.length = " + result.outv.length + ", should be " + properlength);
			String[][] ret = new String[nrows][ncol];
			int row, col;
			for (row = 0; row != nrows; ++row)
				for (col = 0; col != ncol; ++col)
					ret[row][col] = result.outv[i++];
			return ret;
		}
		catch (IndexOutOfBoundsException e) {
			throw new ServalDInterfaceError(e);
		}
		catch (IllegalArgumentException e) {
			throw new ServalDInterfaceError(e);
		}
	}

}
