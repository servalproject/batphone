package org.servalproject.servald;

import android.database.AbstractWindowedCursor;
import android.util.Log;

import org.servalproject.servaldna.ServalDFailureException;

public abstract class ServalDCursor extends AbstractWindowedCursor {
	CursorWindowJniResults results;
	private int numRows = -1;
	private static final String TAG = "ServalDCursor";

    ServalDCursor() throws ServalDFailureException {
        fill(0);
    }

    abstract void fillWindow(CursorWindowJniResults window, int offset, int numRows) throws ServalDFailureException;

    final void fill(int offset) throws ServalDFailureException {
		int limit = 0;
		if (numRows != -1) {
			offset -= numRows / 3;
			if (offset < 0)
				offset = 0;
		}
		Log.v(TAG, "Filling cursor offset=" + offset + " numRows=" + numRows);
		results = new CursorWindowJniResults(offset);
        fillWindow(results, offset, numRows);
        if (results.window==null)
            throw new ServalDFailureException("Command failed to start a result set");
		Log.v(TAG, "Returned " + offset + "-" + (offset + results.window.getNumRows()) + " rows of " + results.totalRowCount);
		numRows = results.window.getNumRows();
		if (numRows == results.totalRowCount)
			numRows = -1;
		this.setWindow(results.window);
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
