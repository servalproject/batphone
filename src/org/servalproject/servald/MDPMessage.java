package org.servalproject.servald;


public class MDPMessage {
	public static final int MDP_MTU = 2000;
	public static final int MDP_AWAITREPLY = 9999;

	public static final int MDP_FLAG_MASK = 0xff00;
	public static final int MDP_FORCE = 0x0100;
	public static final int MDP_NOCRYPT = 0x0200;
	public static final int MDP_NOSIGN = 0x0400;

	public static final int MDP_TYPE_MASK = 0xff;
	public static final int MDP_TX = 1;
	public static final int MDP_BIND = 3;
	public static final int MDP_ERROR = 4;
	public static final int MDP_GETADDRS = 5;
	public static final int MDP_ADDRLIST = 6;
	public static final int MDP_VOMPEVENT = 7;
	public static final int MDP_NODEINFO = 8;
	public static final int MDP_GOODBYE = 9;

	public int packetType;
	public int flags;
	public byte[] raw;

	public byte[] toByteArray() {
		byte[] out = new byte[4 + MDP_MTU];
		int packetTypeAndFlags = this.packetType | this.flags;
		putInt(out, 0, packetTypeAndFlags);
		copyBytes(this.raw, 0, this.raw.length, out, 4);
		return out;
	}

	protected void putLong(byte[] out, int offset, long v) {
		out[offset + 0] = makeByte(v >> 56);
		out[offset + 1] = makeByte(v >> 48);
		out[offset + 2] = makeByte(v >> 40);
		out[offset + 3] = makeByte(v >> 32);
		out[offset + 4] = makeByte(v >> 24);
		out[offset + 5] = makeByte(v >> 16);
		out[offset + 6] = makeByte(v >> 8);
		out[offset + 7] = makeByte(v >> 0);
	}

	protected void putInt(byte[] out, int offset, int v) {
		out[offset + 0] = makeByte(v >> 24);
		out[offset + 1] = makeByte(v >> 16);
		out[offset + 2] = makeByte(v >> 8);
		out[offset + 3] = makeByte(v >> 0);
	}

	protected void putShort(byte[] out, int offset, short v) {
		out[offset + 0] = makeByte(v >> 24);
		out[offset + 1] = makeByte(v >> 16);
		out[offset + 2] = makeByte(v >> 8);
		out[offset + 3] = makeByte(v >> 0);
	}

	protected void putByte(byte[] out, int offset, int v) {
		out[offset + 0] = makeByte(v >> 24);
	}

	protected void copyBytes(byte[] from, int fromOffset, int length,
			byte[] to,
			int toOffset) {
		// TODO Auto-generated method stub
		int n;
		for (n = 0; n < length; n++)
			to[toOffset + n] = from[fromOffset + n];
	}

	protected byte makeByte(long l) {
		return makeByte((int) l);
	}

	protected byte makeByte(int i) {
		// TODO Auto-generated method stub
		int byteValue = i & 0xff;
		if (byteValue > 127)
			byteValue -= 256;
		return (byte) byteValue;
	}

	public MDPMessage(byte[] packet)
	{
		if (packet == null)
			throw new IllegalArgumentException(
					"Packet too short to be an MDP message");
		int packetTypeAndFlags = parseInt(packet, 0);
		this.packetType = packetTypeAndFlags & MDP_TYPE_MASK;
		this.flags = packetTypeAndFlags & MDP_FLAG_MASK;
		this.raw = new byte[packet.length - 4];
		int i;
		for (i = 0; i < packet.length - 4; i++)
			this.raw[i] = packet[i + 4];

	}

	public MDPMessage() {
		this.raw = new byte[MDP_MTU];
	}

	protected int parseInt(byte[] packet, int i) {
		// TODO Auto-generated method stub
		int b = 0;
		b |= unsignedByte(packet[0]) << 24;
		b |= unsignedByte(packet[1]) << 16;
		b |= unsignedByte(packet[2]) << 8;
		b |= unsignedByte(packet[3]) << 0;
		return b;
	}

	protected int unsignedByte(byte b) {
		if (b >= 0)
			return b;
		else
			return 256 + b;
	}

}
