package to.yp.cr;


public class NaCl {

	public final static int crypto_box_PUBLICKEYBYTES = 32;
	public final static int crypto_box_SECRETKEYBYTES = 32;
	public final static int crypto_box_NONCEBYTES = 24;

	static {
		System.loadLibrary("dnalib");
	}

	protected static native int nativeRandomBytes(byte[] b);
	protected static native int nativeCryptoBoxKeypair(byte[] a, byte[] b);
	protected static native int nativeCryptoBox(byte[] publicKey,
			byte[] secretKey, byte[] nonce, byte[] plaintext,
			int plaintextLength, byte[] cipherText);

	public static void safeRandomBytes(byte[] a) {
		if (NaCl.nativeRandomBytes(a) != 0)
			throw new RuntimeException();
	}

	public static class CryptoBoxKeypair {

		byte[] publicKey;
		byte[] secretKey;
		int result;

		public CryptoBoxKeypair() {
			publicKey = new byte[crypto_box_PUBLICKEYBYTES];
			secretKey = new byte[crypto_box_SECRETKEYBYTES];
			result = NaCl.nativeCryptoBoxKeypair(secretKey, publicKey);
		}

	}

	public static class CryptoBox {

		byte[] nonce;
		byte[] cipherText;
		int cipherLength;
		int result;

		public CryptoBox(byte[] publicKey, byte[] secretKey,
				byte[] plainText) {
			nonce = new byte[crypto_box_NONCEBYTES];
			NaCl.safeRandomBytes(nonce);
			cipherText = new byte[plainText.length];
			result = NaCl.nativeCryptoBox(publicKey, secretKey, nonce,
					plainText, plainText.length, cipherText);
		}

	}

}
