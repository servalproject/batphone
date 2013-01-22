package org.servalproject.servald;

import java.util.List;

public class JniResultsList extends AbstractJniResults implements IJniResults {
	final List<byte[]> list;
	private byte[] empty = new byte[0];
	JniResultsList(List<byte[]> list) {
		this.list = list;
	}

	@Override
	public void putBlob(byte[] value) {
		list.add(value == null ? empty : value);
	}

}
