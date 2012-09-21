package org.servalproject;

import org.servalproject.servald.IPeerListListener;

/**
 * Provides an interface for adding and removing listeners to the
 * PeerListService. The onBind() method in PeerListService returns an object
 * that implements this interface which allows interested classes to add and
 * remove their listener.
 *
 * @author brendon
 *
 */
public interface IPeerListMonitor {

	public void registerListener(IPeerListListener listener);

	public void removeListener(IPeerListListener listener);

}
