package org.servalproject.servald.mdp;

import java.io.IOException;
import java.net.DatagramSocket;

import org.servalproject.servald.SubscriberId;

/**
 * Equivalent of {@link DatagramSocket} for MDP protocol.
 * 
 * Server-side example:
 * 
 * <pre>
 * byte[] data = new byte[BUF_SIZE];
 * MeshSocket serverSocket = new MeshSocket(SERVER_PORT);
 * MeshPacket packet = new MeshPacket(data, data.length);
 * serverSocket.receive(packet); // blocking call
 * </pre>
 * 
 * Client-side example:
 * 
 * <pre>
 * byte[] data = ...;
 * MeshSocket clientSocket = new MeshSocket(CLIENT_PORT);
 * MeshPacket packet = new MeshPacket(data, data.length, SERVER_SID, SERVER_PORT);
 * clientSocket.send(packet);
 * </pre>
 * 
 * @author Romain Vimont (®om) <rom@rom1v.com>
 */
public class MeshSocket {

	static {
		// Some native initializations
		init();
	}

	/** File descriptor. */
	private int fd;

	/** Flag indicating whether the socket is bound. */
	private boolean bound;

	/** Flag indicating whether the socket is closed. */
	private boolean closed;

	/** SubscriberID as binary. */
	private byte[] rawSid;

	/** Port */
	private int port;

	/** Lock for closing socket. */
	private final Object closeLock = new Object();

	/**
	 * Constructs a mesh socket and binds it to any available port on the local host machine. The
	 * socket will be bound to the wildcard address.
	 * 
	 * @throws MeshSocketException
	 *             if the socket could not be opened, or the socket could not bind to the specified
	 *             local port.
	 */
	public MeshSocket() throws MeshSocketException {
		_create();
		bind(new MeshSocketAddress(0));
	}

	/**
	 * Creates a mesh socket, bound to the specified local mesh address.
	 * 
	 * If, if the address is {@code null}, creates an unbound socket.
	 * 
	 * @param bindaddr
	 *            local mesh address to bind, or {@code null} for an unbound socket.
	 * @throws MeshSocketException
	 *             if the socket could not be opened, or the socket could not bind to the specified
	 *             local port.
	 */
	public MeshSocket(MeshSocketAddress bindaddr) throws MeshSocketException {
		_create();
		if (bindaddr != null) {
			bind(bindaddr);
		}
	}

	/**
	 * Constructs a mesh socket and binds it to the specified port on the local host machine. The
	 * socket will be bound to the wildcard address.
	 * 
	 * @param port
	 *            port to use.
	 * @throws MeshSocketException
	 *             if the socket could not be opened, or the socket could not bind to the specified
	 *             local port.
	 */
	public MeshSocket(int port) throws MeshSocketException {
		this(port, null);
	}

	/**
	 * Creates a mesh socket, bound to the specified local SID address. If the SID address is 0, the
	 * socket will be bound to the wildcard address.
	 * 
	 * @param port
	 *            local port to use.
	 * @param sid
	 *            local address to bind.
	 * @throws MeshSocketException
	 *             if the socket could not be opened, or the socket could not bind to the specified
	 *             local port.
	 */
	public MeshSocket(int port, SubscriberId sid) throws MeshSocketException {
		this(new MeshSocketAddress(sid, port));
	}

	/**
	 * Returns the binding state of the socket.
	 * 
	 * @return {@code true} if the socket successfully bound to an address, {@code false} otherwise.
	 */
	public synchronized boolean isBound() {
		return bound;
	}

	/**
	 * Returns whether the socket is closed or not.
	 * 
	 * @return {@code true} if the socket has been closed, {@code false} otherwise.
	 */
	public boolean isClosed() {
		synchronized (closeLock) {
			return closed;
		}
	}

	/**
	 * Binds this mesh socket to a specific address and port.
	 * 
	 * If the address is {@code null}, then the system will pick up an ephemeral port and a valid
	 * local address to bind the socket.
	 * 
	 * @param addr
	 *            mesh socket address.
	 * @throws MeshSocketException
	 */
	public synchronized void bind(MeshSocketAddress addr) throws MeshSocketException {
		if (isClosed()) {
			throw new MeshSocketException("Mesh socket is closed");
		}
		if (isBound()) {
			throw new MeshSocketException("Mesh socket already bound");
		}
		if (addr == null) {
			addr = new MeshSocketAddress(0);
		}
		try {
			SubscriberId sid = addr.getSid();
			_bind(addr.getPort(), addr.getSid());
			port = addr.getPort();
			rawSid = sid == null ? null : sid.binary;
		} catch (MeshSocketException e) {
			close();
			throw e;
		}
		bound = true;
	}

	/**
	 * Sends a mesh packet from this socket. The mesh packet includes information indicating the
	 * data to be sent, its length, the SID address of the remote host, and the port number on the
	 * remote host.
	 * 
	 * @param packet
	 *            mesh packet to send.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public synchronized void send(MeshPacket packet) throws IOException {
		if (isClosed()) {
			throw new MeshSocketException("Mesh socket is closed");
		}
		if (!isBound()) {
			bind(new MeshSocketAddress(0));
		}
		_send(packet);
	}

	/**
	 * Receives a mesh packet from this socket. When this method returns, the mesh packet's buffer
	 * is filled with the data received. The mesh packet also contains the sender's SID address, and
	 * the port number on the sender's machine.
	 * 
	 * This method blocks until data is received. The length field of the mesh packet object
	 * contains the length of the received message. If the message is longer than the packet's
	 * length, the message is truncated.
	 * 
	 * @param packet
	 *            the mesh packet into which to place the incoming data.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public synchronized void receive(MeshPacket packet) throws IOException {
		if (isClosed()) {
			throw new MeshSocketException("Mesh socket is closed");
		}
		if (!isBound()) {
			bind(new MeshSocketAddress(0));
		}
		_receive(packet);
	}

	/**
	 * Closes this mesh socket.
	 * 
	 * Any thread currently blocked in {@link #receive(MeshPacket)} upon this socket will throw a
	 * {@link MeshSocketException}. <strong>TODO not yet implemented</strong>
	 */
	public void close() {
		synchronized (closeLock) {
			closed = true;
			_close();
		}
	}

	private native static void init();

	private native void _create() throws MeshSocketException;

	private native void _bind(int port, SubscriberId sid) throws MeshSocketException;

	private native void _send(MeshPacket packet) throws IOException;

	private native void _receive(MeshPacket packet) throws IOException;

	private native void _close();

}
