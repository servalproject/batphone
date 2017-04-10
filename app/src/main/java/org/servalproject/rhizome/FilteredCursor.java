package org.servalproject.rhizome;

import android.database.AbstractCursor;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

public class FilteredCursor extends AbstractCursor {
	final String columns[];
	final Cursor existing;
	final List<Integer> rows;

	public FilteredCursor(Cursor existing) {
		this.existing = existing;
		this.columns = existing.getColumnNames();
		int name_col = existing.getColumnIndex("name");
		int size_col = existing.getColumnIndex("filesize");

		List<Integer> rows = new ArrayList<Integer>();

		while (existing.moveToNext()) {
			String name = existing.getString(name_col);
			if (name == null || "".equals(name))
				continue;

			long fileSize = existing.getLong(size_col);

			if (fileSize == 0 || name.startsWith(".")
					|| name.endsWith(".smapp")
					|| name.endsWith(".smapl")
					|| name.startsWith("smaps-photo-")) {
				continue;
			}

			rows.add(existing.getPosition());
		}
		this.rows = rows;
	}

	@Override
	public String[] getColumnNames() {
		return columns;
	}

	@Override
	public int getCount() {
		return rows.size();
	}

	@Override
	public double getDouble(int column) {
		return existing.getDouble(column);
	}

	@Override
	public float getFloat(int column) {
		return existing.getFloat(column);
	}

	@Override
	public int getInt(int column) {
		return existing.getInt(column);
	}

	@Override
	public long getLong(int column) {
		return existing.getLong(column);
	}

	@Override
	public short getShort(int column) {
		return existing.getShort(column);
	}

	@Override
	public String getString(int column) {
		return existing.getString(column);
	}

	@Override
	public byte[] getBlob(int column) {
		return existing.getBlob(column);
	}

	@Override
	public boolean isNull(int column) {
		return existing.isNull(column);
	}

	@Override
	public boolean onMove(int oldPosition, int newPosition) {
		return existing.moveToPosition(this.rows.get(newPosition));
	}
}
