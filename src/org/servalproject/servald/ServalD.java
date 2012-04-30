package org.servalproject.servald;


class ServalD
{
	private ServalD() {
	}

	static {
		System.loadLibrary("serval");
	}

	public static synchronized native ServalDResult command(String[] args);

	public static void main(String[] args)
	{
		ServalDResult res = ServalD.command(args);
		for (String s: res.outv) {
			System.out.println(s);
		}
		System.exit(res.status);
	}
}
