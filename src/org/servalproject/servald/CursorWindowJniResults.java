package org.servalproject.servald;

import android.database.CursorWindow;

import org.servalproject.servaldna.IJniResults;

public class CursorWindowJniResults implements IJniResults {
	final int offset;
	CursorWindow window;
	boolean full = true;
	int column_count;
	String column_names[];

	private int row = -1;
	private int column = -1;
	int totalRowCount = -1;

	public CursorWindowJniResults(int offset) {
		this.offset = offset;
	}

	private boolean checkColumn() {
		if (full)
			return false;
		column++;
		if (column == 0 || column >= column_count) {
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
	public void putHexValue(byte[] blob) {
		if (!checkColumn())
			return;
		if (blob == null) {
			window.putNull(row, column);
		} else {
			window.putBlob(blob, row, column);
		}
	}

	@Override
	public void putBlob(byte[] blob) {
		if (!checkColumn())
			return;
		if (blob == null) {
			window.putNull(row, column);
		} else {
			window.putBlob(blob, row, column);
		}
	}

	@Override
	public void startTable(int column_count) {
		this.window = new CursorWindow(true);
		this.window.setNumColumns(column_count);
		this.window.setStartPosition(offset);
		this.row = offset - 1;
		this.column_count = column_count;
		this.column_names = new String[column_count];
		this.full = false;
	}

	@Override
	public void setColumnName(int i, String name) {
		this.column_names[i] = name;
	}

	@Override
	public void endTable(int row_count) {
		totalRowCount = row_count;
	}
}
