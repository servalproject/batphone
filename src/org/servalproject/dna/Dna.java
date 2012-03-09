/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.dna;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.servalproject.batman.PeerRecord;
import org.servalproject.batman.RouteTable;
import org.servalproject.dna.OpSimple.Code;

import android.util.Log;

public class Dna {

	private DatagramSocket s = null;
	private List<Address> staticPeers = null;
	private List<PeerRecord> dynamicPeers = null;

	public int timeout = 300;
	public int retries = 5;
	public int verbose = 3;
	public boolean broadcast = false;
	private final static String TAG = "DNA";

	public void addLocalHost() {
		try {
			// should be able to use InetAddress.getLocalHost() but can fail
			// hack to avoid issues with resolving "localhost"
			byte local[] = new byte[] { 127, 0, 0, 1 };
			addStaticPeer(InetAddress.getByAddress(local));
		} catch (UnknownHostException e) {
			Log.e("BatPhone", e.toString(), e);
		}
	}

	public void addStaticPeer(InetAddress i) {
		addStaticPeer(i, Packet.dnaPort);
	}

	public void addStaticPeer(InetAddress i, int port) {
		if (staticPeers == null)
			staticPeers = new ArrayList<Address>();
		staticPeers.add(new Address(i, port));
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
	Map<Long, PeerBroadcast> broadcasts = new HashMap<Long, PeerBroadcast>();

	private void send(Packet p, Address addr) throws IOException {
		send(p, addr.addr, addr.port);
	}

	private void send(Packet p, InetAddress addr) throws IOException {
		send(p, addr, Packet.dnaPort);
	}

	private void send(Packet p, InetAddress addr, int port) throws IOException {
		DatagramPacket dg = p.getDatagram();
		dg.setAddress(addr);
		dg.setPort(port);
		if (s == null) {
			s = new DatagramSocket();
			s.setBroadcast(true);
		}

		if (this.logDebug())
			this.logDebug("Sending packet to " + addr + ":" + port);
		if (this.logVerbose())
			this.logVerbose(p.toString());

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

	private static List<InetAddress> broadcastAddresses = null;

	private void send(final PeerBroadcast broadcast) throws IOException {
		// TODO Not sure if this is working
		// Do we need to send a broadcast based on the configured networks??
		List<InetAddress> broadcasts = broadcastAddresses;
		while (broadcasts == null) {
			getBroadcastAddresses();
			broadcasts = broadcastAddresses;
		}

		for (InetAddress addr : broadcasts)
			send(broadcast.packet, addr);

		// this.send(broadcast.packet, broadcastAddress);
		broadcast.transmissionTime = System.currentTimeMillis();
		broadcast.retryCount++;
		if (!this.broadcasts.containsKey(broadcast.packet.transactionId))
			this.broadcasts.put(broadcast.packet.transactionId, broadcast);
	}

	// re-send packets. If nothing was sent, return false.
	private boolean send() throws IOException {
		boolean ret = false;

		Iterator<PeerConversation> i = this.resendQueue.iterator();
		while (i.hasNext()) {
			PeerConversation pc = i.next();
			if (pc.responseReceived || pc.retryCount >= this.retries) {
				i.remove();
				continue;
			}
			ret = true;
			this.send(pc);
		}

		for (PeerBroadcast broadcast : broadcasts.values()) {
			if (broadcast.hadResponse || broadcast.retryCount >= this.retries) {
				broadcasts.remove(broadcast.packet.transactionId);
				continue;
			}
			ret = true;
			this.send(broadcast);
		}
		return ret;
	}

	// one packet buffer for all reply's, not thread safe
	DatagramPacket reply = null;
	private static final int RECV_BUFF_LEN = 8000;
	private Packet receivePacket() throws IOException {
		if (this.reply == null) {
			byte[] buffer = new byte[RECV_BUFF_LEN];
			this.reply = new DatagramPacket(buffer, buffer.length);
		}

		if (s == null) {
			s = new DatagramSocket();
			s.setBroadcast(true);
		}
		this.s.setSoTimeout(this.timeout);
		this.reply.setLength(RECV_BUFF_LEN);
		this.s.receive(this.reply);
		Packet p = Packet.parse(this.reply, System.currentTimeMillis());

		if (this.logDebug())
			this.logDebug("Received packet from " + p.addr);
		if (this.logVerbose())
			this.logVerbose(p.toString());

		return p;
	}

	// if we are expecting replies, wait for one and process it.
	// if the wait times out, re-transmit, and wait some more.
	private boolean processResponse() throws IOException {
		while (!(this.resendQueue.isEmpty() && this.awaitingResponse.isEmpty() && this.broadcasts
				.isEmpty())) {
			try {
				while (!(this.awaitingResponse.isEmpty() && this.broadcasts
						.isEmpty())) {
					Packet p = receivePacket();
					PeerConversation.Id id = new PeerConversation.Id(
							p.transactionId, p.addr);

					PeerConversation pc = this.awaitingResponse.get(id);

					if (pc==null){
						PeerBroadcast broadcast = broadcasts
								.get(p.transactionId);
						if (broadcast != null) {
							pc = broadcast.getConversation(p.addr);
							if (logVerbose())
								this.logVerbose("Added conversation " + p.addr
										+ " from broadcast response");
						}
					}

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
						logWarning("Unexpected packet from " + p.addr);
						if (this.logVerbose())
							this.logVerbose(p.toString());
					}
				}

			} catch (SocketTimeoutException e) {
				this.logVerbose("Timeout");
				// remove conversations we are going to give up on
				Iterator<PeerConversation> i = this.awaitingResponse.values()
						.iterator();
				while (i.hasNext()) {
					PeerConversation pc = i.next();
					if (pc.responseReceived || pc.retryCount > this.retries)
						i.remove();
				}
			}

			// if there's nothing to re-send, give up
			if (!this.send())
				return false;
		}

		return false;
	}

	private boolean sendParallel(final Packet p, final boolean waitAll,
			final OpVisitor v) throws IOException {
		if (this.staticPeers == null && this.dynamicPeers == null
				&& !this.broadcast)
			throw new IllegalStateException("No peers have been set");
		boolean handled = false;

		List<PeerConversation> convs = new ArrayList<PeerConversation>();
		if (staticPeers != null)
			for (Address addr : staticPeers) {
				convs.add(new PeerConversation(p, addr, v));
			}

		if (this.dynamicPeers != null)
			for (PeerRecord peer : this.dynamicPeers) {
				convs.add(new PeerConversation(p, peer.getAddress(), v));
			}

		PeerBroadcast broadcast = null;
		if (this.broadcast) {
			broadcast = new PeerBroadcast(p, v);
			this.send(broadcast);
		}

		for (PeerConversation pc : convs) {
			this.send(pc);
		}

		outerLoop: while (processResponse()) {
			if (waitAll)
				handled = true;

			if (broadcast != null && broadcast.replies != null) {
				for (PeerConversation pc : broadcast.replies) {
					if (!convs.contains(pc))
						convs.add(pc);
				}
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

		// if we're bailing before getting all responses,
		// forget about sending any more requests
		for (PeerConversation pc : convs) {
			awaitingResponse.remove(pc.id);
			resendQueue.remove(pc);
		}

		if (broadcast != null) {
			broadcasts.remove(p.transactionId);
		}

		return handled;
	}

	public static void clearBroadcastAddresses() {
		Log.v("BatPhone", "Clearing broadcast addresses");
		broadcastAddresses = null;
	}

	private static void getBroadcastAddresses() throws IOException {
		Log.v("BatPhone", "Rebuilding broadcast addresses");
		List<RouteTable> routes = RouteTable.getRoutes();
		List<InetAddress> addresses = new ArrayList<InetAddress>();
		for (int i = 0; i < routes.size(); i++) {
			RouteTable route = routes.get(i);
			if (route.isHost() || route.isDefault())
				continue;
			addresses.add(route.getAddr());
		}

		broadcastAddresses = addresses;
	}

	public void beaconParallel(final Packet p) throws IOException {
		if (this.staticPeers == null && this.dynamicPeers == null)
			throw new IllegalStateException("No peers have been set");

		if (this.broadcast) {
			if (broadcastAddresses == null)
				getBroadcastAddresses();

			for (InetAddress addr : broadcastAddresses)
				send(p, addr);
		}

		if (staticPeers != null) {
			for (Address addr : staticPeers) {
				send(p, addr);
			}
		}

		if (dynamicPeers != null) {
			for (PeerRecord peer : dynamicPeers) {
				send(p, peer.getAddress());
			}
		}

	}

	private boolean sendSerial(final PeerConversation pc) throws IOException {
		this.send(pc);
		// process responses until this conversation completes or times out.
		while (processResponse() && !pc.conversationComplete)
			;
		return pc.conversationComplete;
	}

	private boolean sendSerial(final Packet p, final OpVisitor v)
			throws IOException {
		if (this.staticPeers == null && this.dynamicPeers == null)
			throw new IllegalStateException("No peers have been set");

		if (staticPeers != null) {
			for (Address addr : staticPeers) {
				if (sendSerial(new PeerConversation(p, addr, v)))
					return true;
			}
		}
		if (dynamicPeers != null) {
			for (PeerRecord peer : dynamicPeers) {
				if (sendSerial(new PeerConversation(p, peer.getAddress(), v)))
					return true;
			}
		}

		if (this.dynamicPeers != null)
			for (PeerRecord peer : this.dynamicPeers) {
				if (this.sendSerial(new PeerConversation(p, peer.getAddress(),
						v)))
					return true;
			}

		return false;
	}

	public SubscriberId requestNewHLR(final String did) throws IOException,
			IllegalAccessException, InstantiationException {
		Packet p = new Packet();
		p.setDid(did);
		p.operations.add(new OpSimple(OpSimple.Code.Create));
		ClientVisitor v = new ClientVisitor();
		if (!this.sendSerial(p, v))
			throw new IllegalStateException(
					"Create request was not handled by any peers");
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
		OutputStream s = beginWriteVariable(sid, var, instance, replace);
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
			if (sid == null)
				throw new IllegalArgumentException(
						"Subscriber ID cannot be null");
			if (var == null)
				throw new IllegalArgumentException(
						"Variable type cannot be null");

			this.sid = sid;
			this.var = var;
			this.instance = instance;
			this.flag = (replace ? OpSet.Flag.Replace : OpSet.Flag.NoReplace);
		}

		@Override
		public void close() throws IOException {
			flush();
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
			}))
				throw new IOException("No peer acknowledged writing fragment.");
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
				if (this.buffer.remaining() == 0)
					flush();

				len -= size;
				off += size;
			}
		}

		@Override
		public void write(final int arg0) throws IOException {
			this.buffer.put((byte) arg0);
			if (this.buffer.remaining() == 0)
				flush();
		}
	}

	public boolean sendSms(final String senderNumber, final String did,
			final String message) throws IOException {
		Packet p = new Packet();
		p.setDid(did);
		p.operations.add(new OpDT(message, senderNumber, OpDT.DTtype.SMS));
		return sendParallel(p, false, new OpVisitor() {

			@Override
			public boolean onSimpleCode(final Packet packet,
					final OpSimple.Code code) {
				if (code == OpSimple.Code.Ok)
					return true;
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

		sendParallel(p, true, new OpVisitor() {
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
			public boolean onTTL(final Packet packet, final int ttl) {
				// XXX PGS make this work
				results.observedTTL(this.peer, packet.getSid(), ttl);
				return false;
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
		Address peer;
		ClientVisitor v = null;

		ReadInputStream(SubscriberId sid, VariableType var, byte instance,
				Address peer, ByteBuffer firstChunk, short expectedLen) {
			this.sid = sid;
			this.var = var;
			this.instance = instance;
			this.peer = peer;
			this.buffer = firstChunk;
			this.expectedLen = expectedLen;
			if (firstChunk != null) {
				offset = (short) firstChunk.remaining();
			}
		}

		private boolean readMore() throws IOException {
			if (offset >= expectedLen) {
				// if the last byte of a location is '@', append the ip address
				// of the peer as a string
				if (var == VariableType.Locations && buffer != null
						&& buffer.position() > 0
						&& buffer.get(buffer.position() - 1) == '@') {
					String addr = peer.addr.toString();
					buffer = ByteBuffer.wrap(addr.getBytes());
					if (buffer.get(0) == '/')
						buffer.position(1);
					offset += buffer.remaining();
					return true;
				}
				this.offset = -1;
			}
			if (this.offset == -1)
				return false;

			Packet p = new Packet();
			p.setSid(this.sid);
			p.operations.add(new OpGet(this.var, this.instance, this.offset));
			if (this.v == null)
				this.v = new ClientVisitor();

			PeerConversation pc = new PeerConversation(p, this.peer, this.v);
			Dna.this.send(pc);
			// wait for responses until we hear from the peer we're interested
			// in or the request times out
			while (processResponse() && !pc.conversationComplete)
				;
			if (!pc.conversationComplete)
				throw new IOException(
						"Timeout waiting for response for more data.");

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
			if (this.offset == -1)
				return -1;
			if (this.buffer == null || this.buffer.remaining() == 0)
				if (!readMore())
					return -1;
			return (this.buffer.get()) & 0xff;
		}

		@Override
		public int available() throws IOException {
			if (this.buffer == null)
				return 0;
			return this.buffer.remaining();
		}

		@Override
		public int read(final byte[] bytes, final int offset, final int len)
				throws IOException {
			if (offset == -1)
				return -1;
			if (len == 0)
				return 0;
			if (this.buffer == null || this.buffer.remaining() == 0)
				if (!readMore())
					return -1;

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
			if (this.offset == -1)
				return -1;
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

	private static void usage(final String error) {
		if (error != null)
			System.out.println(error);

		System.out.println("usage:");

		System.out
				.println("       -i - Specify the instance of variable to set.");
		System.out.println("       -v - Increase logging.");
		System.out
				.println("       -d - Search by Direct Inward Dial (DID) number.");
		System.out.println("       -s - Search by Subscriber ID (SID) number.");
		System.out.println("       -p - Specify additional DNA node to query.");
		System.out.println("       -t - Specify the request timeout period.");
		System.out.println("       -m - Send a message.");
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
				if ("-v".equals(args[i]))
					dna.verbose++;
				else if ("-d".equals(args[i]))
					did = args[++i];
				else if ("-s".equals(args[i]))
					sid = new SubscriberId(args[++i]);
				else if ("-p".equals(args[i])) {
					String host = args[++i];
					int port = 4110;
					if (host.indexOf(':') >= 0) {
						port = Integer
								.valueOf(host.substring(host.indexOf(':')));
						host = host.substring(0, host.indexOf(':'));
					}
					dna.addStaticPeer(InetAddress.getByName(host), port);

				} else if ("-t".equals(args[i])) {
					dna.timeout = Integer.valueOf(args[++i]);

				} else if ("-i".equals(args[i])) {
					instance = Integer.valueOf(args[++i]);
					if (instance < -1 || instance > 255)
						throw new IllegalArgumentException(
								"Instance value must be between -1 and 255");

				} else if ("-C".equals(args[i])) {
					if (did == null)
						throw new IllegalArgumentException(
								"You must specify the DID to register.");
					sid = dna.requestNewHLR(did);
					if (sid != null)
						System.out.println("Sid returned: " + sid);

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
								public void observedTTL(PeerConversation peer,
										SubscriberId sid, int ttl) {
								}

								@Override
								public void result(final PeerConversation peer,
										final SubscriberId sid,
										final VariableType varType,
										final byte instance,
										final InputStream value) {
									try {
										if (varType == VariableType.DIDs)
											System.out.println(sid + " ("
													+ peer.id.addr + ")\n"
													+ varType.name() + "["
													+ instance + "]: "
													+ Packet.unpackDid(value));
										else {
											StringBuilder sb = new StringBuilder();
											byte[] bytes = new byte[256];
											int len;
											while ((len = value.read(bytes)) >= 0)
												sb.append(new String(bytes, 0,
														len));
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
				} else if ("-m".equals(args[i])) {
					String recipient = args[++i];
					String message = args[++i];
					dna.sendSms(did, recipient, message);
				} else
					throw new IllegalArgumentException("Unhandled argument "
							+ args[i] + ".");
			}
		} catch (Exception e) {
			e.printStackTrace();
			usage(null);
		}
	}

	// Note for testing on non-android, change these methods to remove
	// references to android.util.Log
	private boolean logDebug() {
		return this.verbose >= 4 && Log.isLoggable(TAG, Log.DEBUG);
	}

	private boolean logVerbose() {
		return this.verbose >= 5 && Log.isLoggable(TAG, Log.VERBOSE);
	}

	private void logDebug(final String message) {
		Log.d(TAG, message);
	}

	private void logVerbose(final String message) {
		Log.v(TAG, message);
	}

	private void logWarning(final String message) {
		Log.w(TAG, message);
	}
}
