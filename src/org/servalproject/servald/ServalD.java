package org.servalproject.servald;

import org.servalproject.servald.ServalDResult;

class ServalD
{
	public ServalD()
	{
		System.loadLibrary("servald");
	}

	public native ServalDResult command(String[] args);

	public static void main(String[] args)
	{
		ServalD sdi = new ServalD();
		ServalDResult res = sdi.command(args);
		for (String s: res.outv) {
			System.out.println(s);
		}
		System.exit(res.status);
	}
}
