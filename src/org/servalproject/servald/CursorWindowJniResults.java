package org.servalproject.servald;

import android.database.CursorWindow;

import org.servalproject.servaldna.IJniResults;

public class CursorWindowJniResults implements IJniResults {
	final int offset;
	CursorWindow window;
	boolean full = true;
	int columns;
	String column_names[];

	private int row = -1;
	private int column = -1;
	int totalRowCount = -1;

	public CursorWindowJniResults(int offset) {
		this.offset = offset;
	}

	@Override
	public void startResultSet(int columns) {
		this.window = new CursorWindow(true);
		this.window.setNumColumns(columns);
		this.window.setStartPosition(offset);
		this.row = offset - 1;
		this.columns = columns;
		this.column_names = new String[columns];
		this.full = false;
	}

	@Override
	public void setColumnName(int i, String name) {
		this.column_names[i] = name;
	}

	private boolean checkColumn() {
		if (full)
			return false;
		column++;
		if (column == 0 || column >= columns) {
			if (!window.allocRow()) {
				full = true;
				return false;
			}
			row++;
			column = 0;
		}
		return true;
	}

	@Override
	public void putString(String value) {
		if (!checkColumn())
			return;

		if (value == null) {
			window.putNull(row, column);
		} else {
			window.putString(value, row, column);
		}
	}

	@Override
	public void putBlob(byte[] value) {
		if (!checkColumn())
			return;

		if (value == null) {
			window.putNull(row, column);
		} else {
			window.putBlob(value, row, column);
		}
	}

	@Override
	public void putLong(long value) {
		if (!checkColumn())
			return;
		window.putLong(value, row, column);
	}

	@Override
	public void putDouble(double value) {
		window.putDouble(value, row, column);
	}

	@Override
	public void totalRowCount(int rows) {
		totalRowCount = rows;
	}
}
