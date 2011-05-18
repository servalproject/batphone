package org.servalproject.dna;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.batman.PeerRecord;
import org.servalproject.dna.OpSimple.Code;

import android.content.Context;
import android.util.Log;

public class Dna {

	private DatagramSocket s = null;
	private List<SocketAddress> staticPeers = null;
	private List<PeerRecord> dynamicPeers = null;

	private int timeout = 300;
	private int retries = 5;

	public void addStaticPeer(final InetAddress i) {
		this.addStaticPeer(new InetSocketAddress(i, Packet.dnaPort));
	}

	public void addStaticPeer(final SocketAddress i) {
		if (this.staticPeers == null) {
			this.staticPeers = new ArrayList<SocketAddress>();
		}
		this.staticPeers.add(i);
	}

	public void clearPeers() {
		this.staticPeers = null;
	}

	public void setDynamicPeers(final List<PeerRecord> batmanPeers) {
		this.dynamicPeers = batmanPeers;
	}

	// packets that we may need to (re-)send
	List<PeerConversation> resendQueue = new ArrayList<PeerConversation>();
	// responses that we are still waiting for
	Map<PeerConversation.Id, PeerConversation> awaitingResponse = new HashMap<PeerConversation.Id, PeerConversation>();

	private void send(final Packet p, final InetAddress addr)
			throws IOException {
		this.send(p, new InetSocketAddress(addr, Packet.dnaPort));
	}

	private void send(final Packet p, final SocketAddress addr)
			throws IOException {
		DatagramPacket dg = p.getDatagram();
		dg.setSocketAddress(addr);
		if (this.s == null) {
			this.s = new DatagramSocket();
			this.s.setBroadcast(true);
		}
		this.s.send(dg);
	}

	private void send(final PeerConversation pc) throws IOException {
		this.send(pc.packet, pc.id.addr);
		pc.transmissionTime = System.currentTimeMillis();
		pc.retryCount++;

		if (!this.resendQueue.contains(pc)) {
			this.resendQueue.add(pc);
			this.awaitingResponse.put(pc.id, pc);
		}
	}

	private void send() throws IOException {
		Iterator<PeerConversation> i = this.resendQueue.iterator();
		while (i.hasNext()) {
			PeerConversation pc = i.next();
			if (pc.responseReceived || pc.retryCount >= this.retries) {
				i.remove();
				continue;
			}
			this.send(pc);
		}
	}

	// one packet buffer for all reply's
	DatagramPacket reply = null;

	private Packet receivePacket() throws IOException {
		if (this.awaitingResponse.isEmpty()) {
			throw new IllegalStateException(
					"No conversations are expecting a response");
		}
		if (this.reply == null) {
			byte[] buffer = new byte[8000];
			this.reply = new DatagramPacket(buffer, buffer.length);
		}
		this.s.setSoTimeout(this.timeout);
		this.s.receive(this.reply);
		return Packet.parse(this.reply, System.currentTimeMillis());
	}

	// if we are expecting replies, wait for one and process it.
	// if the wait times out, re-transmit, and wait some more.
	private boolean processResponse() throws IOException {
		while (!(this.resendQueue.isEmpty() && this.awaitingResponse.isEmpty())) {
			try {
				while (!this.awaitingResponse.isEmpty()) {
					Packet p = this.receivePacket();
					PeerConversation.Id id = new PeerConversation.Id(
							p.transactionId, p.addr);

					PeerConversation pc = this.awaitingResponse.get(id);

					if (pc != null) {
						pc.replyTime = p.timestamp;
						pc.processResponse(p);
						if (pc.conversationComplete) {
							this.awaitingResponse.remove(id);
							// break out of this function to give the caller a
							// chance to stop processing
							return true;
						}
					} else {
						Log.d("BatPhone", "Unexpected packet from " + p.addr);
						Log.v("BatPhone", p.toString());
					}
				}

			} catch (SocketTimeoutException e) {
				// remove conversations we are going to give up on
				Iterator<PeerConversation> i = this.awaitingResponse.values()
						.iterator();
				while (i.hasNext()) {
					PeerConversation pc = i.next();
					if (pc.responseReceived || pc.retryCount > this.retries) {
						i.remove();
					}
				}

				// if we're not going to re-send a packet, we can just give up
				// now.
				if (this.resendQueue.isEmpty()) {
					return false;
				}
			}

			if (!this.resendQueue.isEmpty()) {
				this.send();
			}
		}

		return false;
	}

	private boolean sendParallel(final Packet p, final boolean waitAll,
			final OpVisitor v) throws IOException {
		if (this.staticPeers == null && this.dynamicPeers == null) {
			throw new IllegalStateException("No peers have been set");
		}
		boolean handled = false;

		List<PeerConversation> convs = new ArrayList<PeerConversation>();
		if (this.staticPeers != null) {
			for (SocketAddress addr : this.staticPeers) {
				convs.add(new PeerConversation(p, addr, v));
			}
		}

		if (this.dynamicPeers != null) {
			for (PeerRecord peer : this.dynamicPeers) {
				convs.add(new PeerConversation(p, peer.getAddress(), v));
			}
		}

		for (PeerConversation pc : convs) {
			this.send(pc);
		}

		outerLoop: while (this.processResponse()) {
			if (waitAll) {
				handled = true;
			}
			for (PeerConversation pc : convs) {
				if (waitAll && !pc.conversationComplete) {
					handled = false;
					break;
				}
				if (pc.conversationComplete && !waitAll) {
					handled = true;
					break outerLoop;
				}
			}
		}

		// forget about sending any more requests
		for (PeerConversation pc : convs) {
			this.awaitingResponse.remove(pc.id);
		}

		return handled;
	}

	public void beaconParallel(final Packet p) throws IOException {
		if (this.staticPeers == null && this.dynamicPeers == null) {
			throw new IllegalStateException("No peers have been set");
		}

		if (this.staticPeers != null) {
			for (SocketAddress addr : this.staticPeers) {
				this.send(p, addr);
			}
		}

		if (this.dynamicPeers != null) {
			for (PeerRecord peer : this.dynamicPeers) {
				this.send(p, peer.getAddress());
			}
		}

	}

	private boolean sendSerial(final PeerConversation pc) throws IOException {
		this.send(pc);
		// process responses until this conversation completes or times out.
		while (this.processResponse() && !pc.conversationComplete) {
			;
		}
		return pc.conversationComplete;
	}

	private boolean sendSerial(final Packet p, final OpVisitor v)
			throws IOException {
		if (this.staticPeers == null && this.dynamicPeers == null) {
			throw new IllegalStateException("No peers have been set");
		}

		if (this.staticPeers != null) {
			for (SocketAddress addr : this.staticPeers) {
				if (this.sendSerial(new PeerConversation(p, addr, v))) {
					return true;
				}
			}
		}
		if (this.dynamicPeers != null) {
			for (PeerRecord peer : this.dynamicPeers) {
				if (this.sendSerial(new PeerConversation(p, peer.getAddress(),
						v))) {
					return true;
				}
			}
		}
		return false;
	}

	public SubscriberId requestNewHLR(final String did) throws IOException,
			IllegalAccessException, InstantiationException {
		Packet p = new Packet();
		p.setDid(did);
		p.operations.add(new OpSimple(OpSimple.Code.Create));
		ClientVisitor v = new ClientVisitor();
		if (!this.sendSerial(p, v)) {
			throw new IllegalStateException(
					"Create request was not handled by any peers");
		}
		return v.sid;
	}

	private class ClientVisitor extends OpVisitor {
		SubscriberId sid = null;
		VariableRef reference;
		short varLen;
		ByteBuffer buffer;

		@Override
		public boolean onSimpleCode(final Packet packet, final Code code) {
			switch (code) {
			case Ok:
				this.sid = packet.getSid();
				return true;
			default:
				Log.v("BatPhone", "Response: " + code.name());
			}
			return false;
		}

		@Override
		public boolean onData(final Packet packet, final VariableRef reference,
				final short varLen, final ByteBuffer buffer) {
			this.sid = packet.getSid();
			this.reference = reference;
			this.varLen = varLen;
			this.buffer = buffer;
			return true;
		}

	};

	public void writeDid(final SubscriberId sid, final byte instance,
			final boolean replace, final String did) throws IOException {
		this.writeVariable(sid, VariableType.DIDs, instance, replace,
				ByteBuffer.wrap(Packet.packDid(did)));
	}

	public void writeLocation(final SubscriberId sid, final byte instance,
			final boolean replace, final String value) throws IOException {
		this.writeVariable(sid, VariableType.Locations, instance, replace,
				ByteBuffer.wrap(value.getBytes()));
	}

	public void writeVariable(final SubscriberId sid, final VariableType var,
			final byte instance, final boolean replace, final String value)
			throws IOException {
		this.writeVariable(sid, var, instance, replace, ByteBuffer
				.wrap(var == VariableType.DIDs ? Packet.packDid(value) : value
						.getBytes()));
	}

	public void writeVariable(final SubscriberId sid, final VariableType var,
			final byte instance, final boolean replace, final ByteBuffer value)
			throws IOException {
		OutputStream s = this.beginWriteVariable(sid, var, instance, replace);
		s.write(value.array(), value.arrayOffset(), value.remaining());
		s.close();
	}

	// write variables with an output stream so we can store large values as
	// they are created
	// TODO, send packets asynchronously on calls to write, blocking only when
	// flush() or close() is called.
	public OutputStream beginWriteVariable(final SubscriberId sid,
			final VariableType var, final byte instance, final boolean replace) {
		return new WriteOutputStream(sid, var, instance, replace);
	}

	class WriteOutputStream extends OutputStream {
		SubscriberId sid;
		VariableType var;
		byte instance;
		short offset = 0;
		OpSet.Flag flag;
		ByteBuffer buffer = ByteBuffer.allocate(256);

		public WriteOutputStream(final SubscriberId sid,
				final VariableType var, final byte instance,
				final boolean replace) {
			if (sid == null) {
				throw new IllegalArgumentException(
						"Subscriber ID cannot be null");
			}
			if (var == null) {
				throw new IllegalArgumentException(
						"Variable type cannot be null");
			}

			this.sid = sid;
			this.var = var;
			this.instance = instance;
			this.flag = (replace ? OpSet.Flag.Replace : OpSet.Flag.NoReplace);
		}

		@Override
		public void close() throws IOException {
			this.flush();
		}

		@Override
		public void flush() throws IOException {
			this.buffer.flip();

			Packet p = new Packet();
			p.setSid(this.sid);
			p.operations.add(new OpSet(this.var, this.instance, this.offset,
					this.flag, this.buffer));
			this.flag = OpSet.Flag.Fragment;
			this.offset += this.buffer.remaining();
			if (!Dna.this.sendSerial(p, new OpVisitor() {
				@Override
				public boolean onWrote(final Packet packet,
						final VariableRef reference) {
					Log.v("BatPhone", "Wrote " + reference);
					return true;
				}
			})) {
				throw new IOException("No peer acknowledged writing fragment.");
			}
			this.buffer.clear();
		}

		@Override
		public void write(final byte[] b) throws IOException {
			this.write(b, 0, b.length);
		}

		@Override
		public void write(final byte[] b, int off, int len) throws IOException {
			while (len > 0) {
				int size = len > 256 ? 256 : len;

				this.buffer.put(b, off, size);
				if (this.buffer.remaining() == 0) {
					this.flush();
				}

				len -= size;
				off += size;
			}
		}

		@Override
		public void write(final int arg0) throws IOException {
			this.buffer.put((byte) arg0);
			if (this.buffer.remaining() == 0) {
				this.flush();
			}
		}
	}

	public boolean sendSms(final Context context, final String did,
			final String message) throws IOException {
		String senderNumber = ((ServalBatPhoneApplication) context
				.getApplicationContext()).getPrimaryNumber();
		Packet p = new Packet();
		p.setDid(did);
		p.operations.add(new OpDT(message, senderNumber, OpDT.DTtype.SMS));
		Log.i("DNA", "sendSms : Operation added to the packet");
		return this.sendParallel(p, false, new OpVisitor() {

			@Override
			public void onPacketArrived(final Packet packet,
					final PeerConversation peer) {
				Log.v("Batphone", packet.toString());
			}

			@Override
			public boolean onSimpleCode(final Packet packet,
					final OpSimple.Code code) {
				if (code == OpSimple.Code.Ok) {
					return true;
				}
				return false;
			}
		});
	}

	// Send a request to all peers for this variable
	public void readVariable(final SubscriberId sid, final String did,
			final VariableType var, final byte instance,
			final VariableResults results) throws IOException {
		Packet p = new Packet();
		p.setSidDid(sid, did);
		p.operations.add(new OpGet(var, instance, (short) 0));

		this.sendParallel(p, true, new OpVisitor() {
			PeerConversation peer;

			@Override
			public void onPacketArrived(final Packet packet,
					final PeerConversation peer) {
				this.peer = peer;
			}

			@Override
			public boolean onDone(final Packet packet, final byte count) {
				return true;
			}

			@Override
			public boolean onData(final Packet packet,
					final VariableRef reference, final short varLen,
					final ByteBuffer buffer) {
				// inform the caller of this variable, create an input stream
				// for the caller to read the value.
				results.result(
						this.peer,
						packet.getSid(),
						reference.varType,
						reference.instance,
						new ReadInputStream(packet.getSid(), reference.varType,
								reference.instance, packet.addr, buffer, varLen));
				return false;
			}
		});
	}

	// TODO pre-fetch remaining fragments asynchronously
	public class ReadInputStream extends InputStream {
		short offset = 0;
		ByteBuffer buffer = null;
		short expectedLen;
		SubscriberId sid;
		VariableType var;
		byte instance;
		SocketAddress peer;
		ClientVisitor v = null;

		ReadInputStream(final SubscriberId sid, final VariableType var,
				final byte instance, final SocketAddress peer,
				final ByteBuffer firstChunk, final short expectedLen) {
			this.sid = sid;
			this.var = var;
			this.instance = instance;
			this.peer = peer;
			this.buffer = firstChunk;
			this.expectedLen = expectedLen;
			if (firstChunk != null) {
				this.offset = (short) firstChunk.remaining();
			}
		}

		private boolean readMore() throws IOException {
			if (this.offset >= this.expectedLen) {
				// if the last byte of a location is '@', append the ip address
				// of the peer as a string
				if (this.var == VariableType.Locations && this.buffer != null
						&& this.buffer.position() > 0
						&& this.buffer.get(this.buffer.position() - 1) == '@') {
					InetSocketAddress inetAddr = (InetSocketAddress) this.peer;
					String addr = inetAddr.getAddress().toString();
					this.buffer = ByteBuffer.wrap(addr.getBytes());
					if (this.buffer.get(0) == '/') {
						this.buffer.position(1);
					}
					this.offset += this.buffer.remaining();
					return true;
				}
				this.offset = -1;
			}
			if (this.offset == -1) {
				return false;
			}

			Packet p = new Packet();
			p.setSid(this.sid);
			p.operations.add(new OpGet(this.var, this.instance, this.offset));
			if (this.v == null) {
				this.v = new ClientVisitor();
			}

			PeerConversation pc = new PeerConversation(p, this.peer, this.v);
			Dna.this.send(pc);
			// wait for responses until we hear from the peer we're interested
			// in or the request times out
			while (Dna.this.processResponse() && !pc.conversationComplete) {
				;
			}
			if (!pc.conversationComplete) {
				throw new IOException(
						"Timeout waiting for response for more data.");
			}

			this.expectedLen = this.v.varLen;
			this.buffer = this.v.buffer;
			this.offset += this.v.reference.len;
			return true;
		}

		@Override
		public void close() throws IOException {
			this.offset = -1;
			this.buffer = null;
		}

		@Override
		public int read() throws IOException {
			if (this.offset == -1) {
				return -1;
			}
			if (this.buffer == null || this.buffer.remaining() == 0) {
				if (!this.readMore()) {
					return -1;
				}
			}
			return (this.buffer.get()) & 0xff;
		}

		@Override
		public int available() throws IOException {
			if (this.buffer == null) {
				return 0;
			}
			return this.buffer.remaining();
		}

		@Override
		public int read(final byte[] bytes, final int offset, final int len)
				throws IOException {
			if (offset == -1) {
				return -1;
			}
			if (len == 0) {
				return 0;
			}
			if (this.buffer == null || this.buffer.remaining() == 0) {
				if (!this.readMore()) {
					return -1;
				}
			}

			int size = len > this.buffer.remaining() ? this.buffer.remaining()
					: len;
			this.buffer.get(bytes, offset, size);
			return size;
		}

		@Override
		public int read(final byte[] bytes) throws IOException {
			return this.read(bytes, 0, bytes.length);
		}

		@Override
		public long skip(final long len) throws IOException {
			int size = 0;
			if (this.offset == -1) {
				return -1;
			}
			if (this.buffer != null && this.buffer.remaining() > 0) {
				size = (int) (len > this.buffer.remaining() ? this.buffer
						.remaining() : len);
				this.buffer.position(this.buffer.position() + size);
			}
			this.offset += len - size;
			if (this.offset > this.expectedLen) {
				this.offset = -1;
				return -1;
			}

			return len;
		}
	}

	/*
	 * @SuppressWarnings("unused") private void locateSubscriber(SubscriberId
	 * sid, String did) throws IOException, IllegalAccessException,
	 * InstantiationException{ Packet p=new Packet(); p.setSidDid(sid, did);
	 * p.operations.add(new OpGet(VariableType.Locations, (byte)0, (short)0));
	 * LocateVisitor v=new LocateVisitor(); if (!sendParallel(p, v)){ throw new
	 * IllegalStateException("Unable to locate subscriber"); }
	 * 
	 * // TODO }
	 * 
	 * private class LocateVisitor extends OpVisitor{
	 * 
	 * @Override public boolean onSimpleCode(Packet packet, Code code) {
	 * System.out.println("Response: "+code.name()); return false; }
	 * 
	 * };
	 */

	private static void usage(final String error) {
		if (error != null) {
			System.out.println(error);
		}

		System.out.println("usage:");

		System.out
				.println("       -i - Specify the instance of variable to set.");
		System.out
				.println("       -d - Search by Direct Inward Dial (DID) number.");
		System.out.println("       -s - Search by Subscriber ID (SID) number.");
		System.out.println("       -p - Specify additional DNA node to query.");
		System.out.println("       -t - Specify the request timeout period.");
		System.out.println("       -R - Read a variable value.\n");
		System.out
				.println("       -U - Write a variable value, updating previous values.\n");
		System.out
				.println("       -W - Write a variable value, keeping previous values.\n");
		System.out
				.println("       -C - Request the creation of a new subscriber with the specified DID.");
	}

	public static void main(final String[] args) {
		try {
			Dna dna = new Dna();
			String did = null;
			SubscriberId sid = null;
			int instance = -1;

			for (int i = 0; i < args.length; i++) {
				if ("-d".equals(args[i])) {
					did = args[++i];

				} else if ("-s".equals(args[i])) {
					sid = new SubscriberId(args[++i]);

				} else if ("-p".equals(args[i])) {
					String host = args[++i];
					int port = 4110;
					if (host.indexOf(':') >= 0) {
						port = Integer
								.valueOf(host.substring(host.indexOf(':')));
						host = host.substring(0, host.indexOf(':'));
					}
					dna.addStaticPeer(new InetSocketAddress(host, port));

				} else if ("-t".equals(args[i])) {
					dna.timeout = Integer.valueOf(args[++i]);

				} else if ("-i".equals(args[i])) {
					instance = Integer.valueOf(args[++i]);
					if (instance < -1 || instance > 255) {
						throw new IllegalArgumentException(
								"Instance value must be between -1 and 255");
					}

				} else if ("-C".equals(args[i])) {
					if (did == null) {
						throw new IllegalArgumentException(
								"You must specify the DID to register.");
					}
					sid = dna.requestNewHLR(did);
					if (sid != null) {
						System.out.println("Sid returned: " + sid);
					}

				} else if ("-U".equals(args[i])) {
					VariableType var = VariableType.getVariableType(args[++i]);
					dna.writeVariable(sid, var, (byte) instance, true,
							args[++i]);

				} else if ("-W".equals(args[i])) {
					VariableType var = VariableType.getVariableType(args[++i]);
					dna.writeVariable(sid, var, (byte) instance, false,
							args[++i]);

				} else if ("-R".equals(args[i])) {
					VariableType var = VariableType.getVariableType(args[++i]);
					dna.readVariable(sid, did, var, (byte) instance,
							new VariableResults() {

								@Override
								public void result(final PeerConversation peer,
										final SubscriberId sid,
										final VariableType varType,
										final byte instance,
										final InputStream value) {
									try {
										if (varType == VariableType.DIDs) {
											System.out.println(sid + " ("
													+ peer.id.addr + ")\n"
													+ varType.name() + "["
													+ instance + "]: "
													+ Packet.unpackDid(value));
										} else {
											StringBuilder sb = new StringBuilder();
											byte[] bytes = new byte[256];
											int len;
											while ((len = value.read(bytes)) >= 0) {
												sb.append(new String(bytes, 0,
														len));
											}
											System.out.println(sid + " ("
													+ peer.id.addr + ")\n"
													+ varType.name() + "["
													+ instance + "]: "
													+ sb.toString());
										}
									} catch (IOException e) {
										e.printStackTrace();
									}
								}
							});

				} else {
					throw new IllegalArgumentException("Unhandled argument "
							+ args[i] + ".");
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			usage(null);
		}
	}
}
