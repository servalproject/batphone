package org.servalproject.servald;

class ServalDResult
{
	public int status;
	public String[] outv;

	public ServalDResult(int status, String[] outv)
	{
		this.status = status;
		this.outv = outv;
	}

}
