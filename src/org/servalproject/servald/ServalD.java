package org.servalproject.servald;

import org.servalproject.servald.ServalDResult;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDInterfaceError;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;
import android.util.Log;

/**
 * Low-level class for invoking servald JNI command-line operations.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class ServalD
{
	public static final String TAG = "ServalD";

	public ServalD()
	{
		System.loadLibrary("serval");
	}

	/**
	 * Low-level JNI entry point into servald command line.
	 *
	 * @param args	The parameters as passed on the command line, eg:
	 * 					res = sdi.command("config", "set", "debug", "peers");
	 * @return		An object containing the servald exit status code (normally0 indicates success)
	 * 				and zero or more output fields that it would have sent to standard output if
	 * 				invoked via a shell command line.
	 */
	public native ServalDResult command(String... args);

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
	public String[][] rhizomeList(int offset, int limit) throws ServalDFailureException, ServalDInterfaceError
	{
		List<String> args = new LinkedList<String>();
		args.add("rhizome");
		args.add("list");
		if (limit >= 0) {
			if (offset < 0)
				offset = 0;
			args.add("" + offset);
			args.add("" + limit);
		} else if (offset >= 0) {
			args.add("" + offset);
		}
		Log.i(ServalD.TAG, "args = " + Arrays.deepToString(args.toArray()));
		final ServalDResult result = this.command(args.toArray(new String[0]));
		Log.i(ServalD.TAG, "result = " + result);
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
