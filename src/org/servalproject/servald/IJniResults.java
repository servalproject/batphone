package org.servalproject.servald;

public interface IJniResults {
	public void startResultSet(int columns);
	public void setColumnName(int column, String name);
	public void putString(String value);
	public void putBlob(byte[] value);
	public void putLong(long value);
	public void putDouble(double value);
	public void totalRowCount(int rows);
}