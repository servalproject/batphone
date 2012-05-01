package org.servalproject.servald;

import java.util.Arrays;

public class ServalDResult
{
	public int status;
	public String[] outv;

	public ServalDResult(int status, String[] outv)
	{
		this.status = status;
		this.outv = outv;
	}

	@Override
	public String toString()
	{
		return this.getClass().getName() + "(status=" + this.status + ", outv=" + Arrays.deepToString(this.outv) + ")";
	}

}
