package org.servalproject.batman;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class RouteTable {
	private String fields[];

	private RouteTable(String line) {
		fields = line.split("\\s+");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i=0;i<fields.length;i++)
			sb.append(fields[i]).append(' ');
		try {
			sb.append("Address: ").append(this.getAddr().getHostAddress())
					.append(", Default: ").append(this.isDefault())
					.append(", Host: ").append(this.isHost());
		} catch (UnknownHostException e) {
			Log.e("BatPhone", e.toString(), e);
		}
		return sb.toString();
	}

	public InetAddress getAddr() throws UnknownHostException {
		int ip = Integer.reverseBytes((int) Long.parseLong(fields[1], 16));
		int mask = Integer.reverseBytes((int) Long.parseLong(fields[7], 16));

		int maskedIp = ip | ~mask;

		byte addr[] = new byte[4];
		ByteBuffer b = ByteBuffer.wrap(addr);
		b.putInt(maskedIp);
		return InetAddress.getByAddress(addr);
	}

	public int getMask() {
		long l = Long.parseLong(fields[7], 16);
		return Integer.reverseBytes((int) l);
	}

	public boolean isDefault() {
		return fields[7].equals("00000000");
	}

	public boolean isHost() {
		return fields[7].equals("FFFFFFFF");
	}

	public static List<RouteTable> getRoutes() throws IOException {
		DataInputStream data = null;
		Log.v("BatPhone", "Reading routes");
		try {
			data = new DataInputStream(new FileInputStream("/proc/net/route"));
			List<RouteTable> routes = new ArrayList<RouteTable>();
			// drop the header
			data.readLine();
			while (true) {
				String line = data.readLine();
				if (line == null)
					break;
				RouteTable route = new RouteTable(line);

				routes.add(route);
			}
			return routes;
		} finally {
			data.close();
		}
	}
}
