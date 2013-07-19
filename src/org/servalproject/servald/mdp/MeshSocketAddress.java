package org.servalproject.servald.mdp;

import java.net.InetSocketAddress;

import org.servalproject.servald.Identity;
import org.servalproject.servald.SubscriberId;

/**
 * Equivalent of {@link InetSocketAddress} for MDP protocol.
 * 
 * @author Romain Vimont (®om) <rom@rom1v.com>
 */
public class MeshSocketAddress {

	/** SID address. */
	private SubscriberId sid;

	/** Port. */
	private int port;

	/**
	 * Constructs a mesh socket address (a SID address and a port).
	 * 
	 * @param sid
	 *            SID address (can be {@code null}).
	 * @param port
	 *            Port.
	 */
	public MeshSocketAddress(SubscriberId sid, int port) {
		this.sid = sid;
		this.port = port;
	}

	/**
	 * Constructs a mesh socket address with {@code null} SID (wildcard address).
	 * 
	 * @param port
	 *            Port.
	 */
	public MeshSocketAddress(int port) {
		this(Identity.getMainIdentity().subscriberId, port);
	}

	/**
	 * Returns the SID address.
	 * 
	 * @return SID address.
	 */
	public SubscriberId getSid() {
		return sid;
	}

	/**
	 * Returns the port.
	 * 
	 * @return Port.
	 */
	public int getPort() {
		return port;
	}

}
