package org.servalproject.servald.mdp;

import java.io.IOException;
import java.net.SocketException;

/**
 * Equivalent of {@link SocketException} for MDP.
 * 
 * @author Romain Vimont (®om) <rom@rom1v.com>
 */
@SuppressWarnings("serial")
public class MeshSocketException extends IOException {

	/**
	 * Constructs a {@code MeshSocketException} without any message.
	 */
	public MeshSocketException() {}

	/**
	 * Constructs a {@code MeshSocketException} with a message.
	 * 
	 * @param msg
	 *            Message.
	 */
	public MeshSocketException(String msg) {
		super(msg);
	}

}
