package org.servalproject.servald;

import android.database.AbstractWindowedCursor;
import android.util.Log;

public class ServalDCursor extends AbstractWindowedCursor {
	private final String command[];
	CursorWindowJniResults results;
	private int numRows = -1;
	private static final String TAG = "ServalDCursor";

	void fill(int offset) throws ServalDFailureException {
		// limit;
		if (numRows != -1) {
			command[command.length - 1] = Integer.toString(numRows);
			offset -= numRows / 3;
			if (offset < 0)
				offset = 0;
		} else
			command[command.length - 1] = null;

		// offset;
		command[command.length - 2] = Integer.toString(offset);

		Log.v(TAG, "Filling cursor @" + offset);
		results = new CursorWindowJniResults(offset);
		int ret = ServalD.command(results, command);
		if (ret != 0)
			throw new ServalDFailureException("");
		Log.v(TAG,
				"Returned " + offset + "-" + (offset
						+ results.window.getNumRows()) + " rows of "
				+ results.totalRowCount);
		numRows = results.window.getNumRows();
		if (numRows == results.totalRowCount)
			numRows = -1;
		this.setWindow(results.window);
	}

	public ServalDCursor(String... command) throws ServalDFailureException {
		this.command = command;
		fill(0);
	}

	@Override
	public String[] getColumnNames() {
		return results.column_names;
	}

	@Override
	public int getCount() {
		return results.totalRowCount;
	}

	@Override
	public boolean onMove(int oldPosition, int newPosition) {
		if (newPosition < mWindow.getStartPosition()
				|| newPosition >= (mWindow.getStartPosition() + mWindow
						.getNumRows())) {
			try {
				fill(newPosition);
			} catch (ServalDFailureException e) {
				Log.e(TAG, e.getMessage(), e);
				return false;
			}
		}

		return true;
	}

	@Override
	public void close() {
		super.close();
		results = null;
		numRows = -1;
	}

	@Override
	public void deactivate() {
		super.deactivate();
		results = null;
		numRows = -1;
	}

	@Override
	public boolean requery() {
		results = null;
		numRows = -1;
		return super.requery();
	}
}
