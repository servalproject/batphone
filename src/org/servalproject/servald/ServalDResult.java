package org.servalproject.servald;

import java.util.Arrays;
import java.util.HashMap;
import java.lang.Integer;
import java.lang.Long;

public class ServalDResult
{
	public static final int STATUS_ERROR = 255;

	public final String[] args;
	public final int status;
	public final String[] outv;
	private HashMap<String,Object> keyValue;

	public ServalDResult(String[] args, int status, String[] outv) {
		this.args = args;
		this.status = status;
		this.outv = outv;
		this.keyValue = null;
	}

	public ServalDResult(ServalDResult orig) {
		this.args = orig.args;
		this.status = orig.status;
		this.outv = orig.outv;
		this.keyValue = orig.keyValue;
	}

	@Override
	public String toString() {
		return this.getClass().getName() + "(args=" + Arrays.deepToString(this.args) + ", status=" + this.status + ", outv=" + Arrays.deepToString(this.outv) + ")";
	}

	public void failIfStatusError() throws ServalDFailureException {
		if (this.status == STATUS_ERROR)
			throw new ServalDFailureException("error exit status", this);
	}

	public void failIfStatusNonzero() throws ServalDFailureException {
		if (this.status != 0)
			throw new ServalDFailureException("non-zero exit status", this);
	}

	public Object getField(String fieldName) throws ServalDInterfaceError {
		if (this.keyValue == null) {
			if (this.outv.length % 2 != 0)
				throw new ServalDInterfaceError("odd number of fields", this);
			this.keyValue = new HashMap<String,Object>();
			int i;
			for (i = 0; i != this.outv.length; i += 2) {
				String key = this.outv[i];
				String value = this.outv[i + 1];
				try {
					this.keyValue.put(key, new Integer(value));
				}
				catch (NumberFormatException e1) {
					try {
						this.keyValue.put(key, new Long(value));
					}
					catch (NumberFormatException e2) {
						this.keyValue.put(key, value);
					}
				}
			}
		}
		if (!this.keyValue.containsKey(fieldName))
			throw new ServalDInterfaceError("missing '" + fieldName + "' field", this);
		return this.keyValue.get(fieldName);
	}

	public String getFieldString(String fieldName) throws ServalDInterfaceError {
		return "" + getField(fieldName);
	}

	public long getFieldLong(String fieldName) throws ServalDInterfaceError {
		Object value = getField(fieldName);
		if (value instanceof Long)
			return ((Long) value).longValue();
		if (value instanceof Integer)
			return ((Integer) value).longValue();
		throw new ServalDInterfaceError("field " + fieldName + "='" + value + "' is not of type long", this);
	}

	public int getFieldInt(String fieldName) throws ServalDInterfaceError {
		Object value = getField(fieldName);
		if (value instanceof Integer)
			return ((Integer) value).intValue();
		throw new ServalDInterfaceError("field " + fieldName + "='" + value + "' is not of type int", this);
	}

}
