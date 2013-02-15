package org.servalproject.servald;

import android.database.AbstractWindowedCursor;
import android.util.Log;

public class ServalDCursor extends AbstractWindowedCursor {
	String service;
	String name;
	SubscriberId sender;
	SubscriberId recipient;
	CursorWindowJniResults results;
	private int numRows = -1;
	private static final String TAG = "ServalDCursor";

	void fill(int offset) throws ServalDFailureException {
		int limit = 0;
		if (numRows != -1) {
			offset -= numRows / 3;
			if (offset < 0)
				offset = 0;
		}
		Log.v(TAG, "Filling cursor offset=" + offset + " numRows=" + numRows);
		results = new CursorWindowJniResults(offset);
		int ret = ServalD.rhizomeListRaw(results, service, name, sender, recipient, offset, numRows);
		if (ret != 0)
			throw new ServalDFailureException("non-zero exit status");
		Log.v(TAG, "Returned " + offset + "-" + (offset + results.window.getNumRows()) + " rows of " + results.totalRowCount);
		numRows = results.window.getNumRows();
		if (numRows == results.totalRowCount)
			numRows = -1;
		this.setWindow(results.window);
	}

	public ServalDCursor(String service, String name, SubscriberId sender, SubscriberId recipient )
			throws ServalDFailureException
	{
		this.service = service;
		this.name = name;
		this.sender = sender;
		this.recipient = recipient;
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
