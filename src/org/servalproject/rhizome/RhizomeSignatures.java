package org.servalproject.rhizome;

import java.util.Iterator;
import java.util.List;

import to.yp.cr.NaCl;

public class RhizomeSignatures {
	protected static List<RhizomeSignatory> trustedSignatories = null;

	public static void rereadSignatories() {

	}

	public static long getTrustInMilliCents(byte [] publicKey) {
		int j;
		long trust=0;
		if (trustedSignatories == null) rereadSignatories();
		if (trustedSignatories == null) return 0;

		Iterator<RhizomeSignatory> it = trustedSignatories.iterator();
		while (it.hasNext())
		{
			RhizomeSignatory s = it.next();
			for(j=0;j<NaCl.crypto_sign_PUBLICKEYBYTES;j++) if (s.publicKey[j]!=publicKey[j]) break;
			if (j==NaCl.crypto_sign_PUBLICKEYBYTES) {
				if (s.trustInMilliCents>trust||trust==0) trust=s.trustInMilliCents;
			}
		}

		return trust;
	}

	public class RhizomeSignatory {
		protected byte [] publicKey;
		protected String name;
		long trustInMilliCents;

		RhizomeSignatory(byte[] publicKey_in,String name_in,long trustInMilliCents_in) {
			publicKey = publicKey_in;
			name=name_in;
			trustInMilliCents=trustInMilliCents_in;
		}
	}

}
