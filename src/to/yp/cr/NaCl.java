package to.yp.cr;


public class NaCl {

	public final static int crypto_box_PUBLICKEYBYTES = 32;
	public final static int crypto_box_SECRETKEYBYTES = 32;
	public final static int crypto_box_NONCEBYTES = 24;
	public final static int crypto_box_ZEROBYTES = 32;

	public final static int crypto_sign_PUBLICKEYBYTES = 32;
	public final static int crypto_sign_SECRETKEYBYTES = 64;
	public final static int crypto_sign_BYTES = 64;

	static {
		System.loadLibrary("dnalib");
	}

	// Convenient access to /dev/urandom
	protected static native int nativeRandomBytes(byte[] b);

	// Diffie-Hellman mediated symmetric and authenticated crypto functions
	protected static native int nativeCryptoBoxKeypair(byte[] secretKey,
			byte[] publicKey);
	protected static native int nativeCryptoBox(byte[] publicKey,
			byte[] secretKey, byte[] nonce, byte[] plaintext,
			int plaintextLength, byte[] cipherText);
	protected static native int nativeCryptoBoxOpen(byte[] publicKey,
			byte[] secretKey, byte[] nonce, byte[] plaintext, int cipherLength,
			byte[] cipherText);


	// Public-Private crypto functions
	protected static native int nativeCryptoSignKeypair(byte[] secretKey,
			byte[] publicKey);
	protected static native int nativeCryptoSign(byte[] secretKey,
			byte[] plainText, byte[] signedText);
	protected static native int nativeCryptoSignOpen(byte[] publicKey,
			byte[] signedText, byte[] plainText);


	public static void safeRandomBytes(byte[] a) {
		if (NaCl.nativeRandomBytes(a) != 0)
			throw new RuntimeException();
	}

	public static class CryptoBoxKeypair {

		public byte[] publicKey;
		protected byte[] secretKey;
		public int result;

		public CryptoBoxKeypair() {
			publicKey = new byte[crypto_box_PUBLICKEYBYTES];
			secretKey = new byte[crypto_box_SECRETKEYBYTES];
			result = NaCl.nativeCryptoBoxKeypair(secretKey, publicKey);
		}

	}

	public static class CryptoBox {

		byte[] nonce;
		byte[] cipherText;
		public int cipherLength;
		public int result;

		public CryptoBox(byte[] publicKey, byte[] secretKey,
				byte[] plainText) {
			nonce = new byte[crypto_box_NONCEBYTES];
			NaCl.safeRandomBytes(nonce);

			// Make padded version of plain text to feed into actual NaCl function.
			// XXX Should make a version that takes a pre-padded version to save
			// unnecessary copies.
			byte[] plainTextPadded = new byte[plainText.length+crypto_box_ZEROBYTES];
			for(int i=0;i<plainText.length;i++) plainTextPadded[crypto_box_ZEROBYTES+i]=plainText[i];

			cipherText = new byte[plainTextPadded.length];
			result = NaCl.nativeCryptoBox(publicKey, secretKey, nonce,
					plainTextPadded, plainTextPadded.length, cipherText);
		}
	}

	public static class CryptoBoxOpen {
		int result;
		byte []plainText;

		public CryptoBoxOpen(byte[] publicKey, byte[] secretKey, byte[] nonce,byte[] cipherText) {
			byte[] plainTextPadded = new byte[cipherText.length];

			result = NaCl.nativeCryptoBoxOpen(publicKey, secretKey, nonce, plainTextPadded, cipherText.length, cipherText);

			// Copy plain text back out from padding
			plainText = new byte[cipherText.length - crypto_box_ZEROBYTES];
			for(int i=0;i<plainText.length;i++) plainText[i]=plainTextPadded[crypto_box_ZEROBYTES+i];
		}
	}

	public static class CryptoSignKeypair {

		public byte[] publicKey;
		protected byte[] secretKey;
		public int result;

		public CryptoSignKeypair() {
			publicKey = new byte[crypto_sign_PUBLICKEYBYTES];
			secretKey = new byte[crypto_sign_SECRETKEYBYTES];
			result = NaCl.nativeCryptoSignKeypair(secretKey, publicKey);
		}
	}

	public static class CryptoSign {
		public int result;
		public byte[] signedMessage;

		public CryptoSign(CryptoSignKeypair key, byte[] message) {
			signedMessage = null;
			result = -1;
			signedMessage = new byte[message.length + NaCl.crypto_sign_BYTES];
			result = NaCl.nativeCryptoSign(key.secretKey, message,
					signedMessage);
		}
	}

	public static class CryptoSignOpen {
		public int result;
		public byte [] message;

		public CryptoSignOpen(CryptoSignKeypair key,byte[] signedMessage) {
			this(key.publicKey,signedMessage);
		}

		public CryptoSignOpen(byte[] publicKey, byte[] signedMessage) {
			message = null;
			result = -1;
			message = new byte[signedMessage.length - NaCl.crypto_sign_BYTES];
			result = NaCl.nativeCryptoSignOpen(publicKey, message,
					signedMessage);
		}
	}

}
