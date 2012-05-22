package org.servalproject;

import java.util.Comparator;

import org.servalproject.servald.Peer;

import android.util.Log;

public class PeerComparator implements Comparator<Peer> {

	@Override
	public int compare(Peer p1, Peer p2) {
		String s1 = p1.getSortString();
		String s2 = p2.getSortString();

		Log.v("PeerComparator", "sorting " + s1 + " and " + s2);

		boolean isNum1 = false;
		boolean isNum2 = false;
		int i1 = 0;
		int i2 = 0;
		try {
			i1 = Integer.parseInt(s1);
			isNum1 = true;
		} catch (NumberFormatException ex) {
			Log.v("PeerComparator", s1 + " is not a number");
		}
		try {
			i2 = Integer.parseInt(s2);
			isNum2 = true;
		} catch (NumberFormatException ex) {
			Log.v("PeerComparator", s2 + " is not a number");
		}
		if (isNum1 && isNum2) {
			if (i1 == i2)
				return 0;
			if (i1 < i2)
				return -1;
			if (i1 > i2)
				return 1;
		} else if (isNum1 && !isNum2) {
			return 1;
		} else if (!isNum1 && isNum2) {
			return -1;
		}
		return s1.compareTo(s2);
	}
}
