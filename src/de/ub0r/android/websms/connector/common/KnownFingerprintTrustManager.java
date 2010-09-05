package de.ub0r.android.websms.connector.common;

import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A TrustManager-implementation that checks that a site-certificate has a known
 * (meaning developer-verified) fingerprint.
 * 
 * @author boris
 */
public final class KnownFingerprintTrustManager implements X509TrustManager {

	/** ?? */
	private static final X509Certificate[] _AcceptedIssuers = // .
	new X509Certificate[] {};

	private final List<byte[]> knownSha1Fingerprints;

	/**
	 * @param knownFingerprints
	 *          The known fingerprint that should be used for the check-methods
	 * @throws NullPointerException
	 *           If {@code knownSha1Fingerprint == null}
	 */
	public KnownFingerprintTrustManager(String... knownFingerprints) {
		this.knownSha1Fingerprints = new ArrayList<byte[]>(knownFingerprints.length);
		
		for (String fingerprint : knownFingerprints) {
			this.knownSha1Fingerprints.add(fingerprintToBytes(fingerprint));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void checkClientTrusted(final X509Certificate[] chain,
			final String authType) throws CertificateException {
		checkTrusted(chain);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void checkServerTrusted(final X509Certificate[] chain,
			final String authType) throws CertificateException {
		checkTrusted(chain);
	}

	private void checkTrusted(final X509Certificate[] chain)
			throws CertificateException {
		if (chain.length == 0) {
			throw new CertificateException("No entries in certificate-chain");
		}

		try {
			X509Certificate siteCert = chain[0];
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] certFingerprint = md.digest(siteCert.getEncoded());

			boolean matched = false;
			for (byte[] fingerprint : knownSha1Fingerprints) {
				if (Arrays.equals(certFingerprint, fingerprint)) {
					matched = true;
					break;
				}
			}

			if (!matched) {
				throw new CertificateException("Unknown certificate fingerprint "
						+ Arrays.toString(certFingerprint));
			}
		} catch (NoSuchAlgorithmException e) {
			throw new CertificateException(
					"Cannot calculate SHA-1 because the algorithm is missing", e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return _AcceptedIssuers;
	}

	private static byte[] fingerprintToBytes(String knownFingerprint) {
		String[] byteTokens = knownFingerprint.split(":");
		byte[] result = new byte[byteTokens.length];
		for (int i = 0; i < result.length; ++i) {
			result[i] = Integer.valueOf(byteTokens[i], 16).byteValue();
		}
		return result;
	}

	public static void main(final String[] args) throws Exception {
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, new TrustManager[] { new KnownFingerprintTrustManager("2c:b4:86:a8:da:87:77:3f:e4:b2:9d:26:6e:11:9e:00:3d:db:85:55") }, null);
		SSLSocketFactory sslSocketFactory = context.getSocketFactory();

		URL url = new URL("https://login.o2online.de");
		URLConnection con = url.openConnection();
		((HttpsURLConnection) con).setSSLSocketFactory(sslSocketFactory);
		con.connect();
		System.out.println(con.getInputStream().read());
	}
}
