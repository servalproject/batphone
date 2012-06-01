package org.servalproject;

import java.util.Comparator;

import org.servalproject.servald.Peer;

/**
 * A comparator for a Peer. Sort on peer.sortString. If the peer has a name,
 * sort it above peers that don't so that numbers appear below names.
 *
 * @author brendon
 * @author jeremy
 */
public class PeerComparator implements Comparator<Peer> {

	@Override
	public int compare(Peer p1, Peer p2) {
		boolean hasName1 = p1.hasName();
		boolean hasName2 = p2.hasName();

		if (hasName1 && !hasName2)
			return -1;
		if (!hasName1 && hasName2)
			return 1;

		String s1 = p1.getSortString();
		String s2 = p2.getSortString();

		return s1.compareTo(s2);
	}
}
