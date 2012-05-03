package org.servalproject.servald;

import java.util.Arrays;

public class ServalDResult
{
	public final String[] args;
	public final int status;
	public final String[] outv;

	public ServalDResult(String[] args, int status, String[] outv) {
		this.args = args;
		this.status = status;
		this.outv = outv;
	}

	public ServalDResult(ServalDResult orig) {
		this.args = orig.args;
		this.status = orig.status;
		this.outv = orig.outv;
	}

	@Override
	public String toString() {
		return this.getClass().getName() + "(args=" + Arrays.deepToString(this.args) + ", status=" + this.status + ", outv=" + Arrays.deepToString(this.outv) + ")";
	}

}
