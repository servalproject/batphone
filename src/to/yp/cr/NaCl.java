package to.yp.cr;


public class NaCl {

	static {
		System.loadLibrary("dnalib");
	}

	public static class CryptoBoxKeypair {
		private native int method(byte[] a, byte[] b);

		public final static int crypto_box_PUBLICKEYBYTES = 32;
		public final static int crypto_box_SECRETKEYBYTES = 32;

		byte[] publicKey;
		byte[] secretKey;

		static {
			System.loadLibrary("dnalib");
		}

		public CryptoBoxKeypair() {
			publicKey = new byte[crypto_box_PUBLICKEYBYTES];
			secretKey = new byte[crypto_box_SECRETKEYBYTES];
			method(secretKey, publicKey);
		}

	}
}
