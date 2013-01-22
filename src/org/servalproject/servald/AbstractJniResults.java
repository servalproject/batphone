package org.servalproject.servald;

public abstract class AbstractJniResults implements IJniResults {

	@Override
	public void startResultSet(int columns) {
		putBlob(Integer.toString(columns).getBytes());
	}

	@Override
	public void setColumnName(int i, String name) {
		putBlob(name.getBytes());
	}

	@Override
	public void putString(String value) {
		putBlob((value != null) ? value.getBytes() : null);
	}

	@Override
	public abstract void putBlob(byte[] value);

	@Override
	public void putLong(long value) {
		putBlob(Long.toString(value).getBytes());
	}

	@Override
	public void putDouble(double value) {
		putBlob(Double.toString(value).getBytes());
	}

	@Override
	public void totalRowCount(int rows) {

	}
}
