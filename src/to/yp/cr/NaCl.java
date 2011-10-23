package to.yp.cr;


public class NaCl {

	static {
		System.loadLibrary("dnalib");
	}

	protected static native int nativeCryptoBoxKeypair(byte[] a, byte[] b);

	public static class CryptoBoxKeypair {


		public final static int crypto_box_PUBLICKEYBYTES = 32;
		public final static int crypto_box_SECRETKEYBYTES = 32;

		byte[] publicKey;
		byte[] secretKey;
		int result;

		public CryptoBoxKeypair() {
			publicKey = new byte[crypto_box_PUBLICKEYBYTES];
			secretKey = new byte[crypto_box_SECRETKEYBYTES];
			result = NaCl.nativeCryptoBoxKeypair(secretKey, publicKey);
		}

	}
}
