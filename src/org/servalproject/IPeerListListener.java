package org.servalproject;

import org.servalproject.servald.Peer;

/**
 * Provides an interface for classes that want to listen for new or changed
 * peers from the PeerListService.
 *
 * @author brendon
 *
 */
public interface IPeerListListener {

	public void peerChanged(Peer p);

}
