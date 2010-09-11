package de.ub0r.android.websms.connector.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.X509TrustManager;

/**
 * A TrustManager-implementation that checks that a site-certificate has a known
 * (meaning developer-verified) fingerprint.
 * 
 * @author boris
 */
public final class KnownFingerprintTrustManager implements X509TrustManager {

	/** Hex = 16 . */
	private static final int HEX = 16;

	/** Array of known good CAs? */
	private static final X509Certificate[] ACCEPTED_ISSUERS = // .
	new X509Certificate[] {};

	/** List of known good fingerprints. */
	private final List<byte[]> knownSha1Fingerprints;

	/**
	 * @param knownFingerprints
	 *            The known fingerprint that should be used for the
	 *            check-methods
	 * @throws NullPointerException
	 *             If {@code knownSha1Fingerprint == null}
	 */
	public KnownFingerprintTrustManager(final String... knownFingerprints) {
		this.knownSha1Fingerprints = new ArrayList<byte[]>(
				knownFingerprints.length);

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
		this.checkTrusted(chain);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void checkServerTrusted(final X509Certificate[] chain,
			final String authType) throws CertificateException {
		this.checkTrusted(chain);
	}

	/**
	 * Check trusted certificate. It only checks for known good fingerprints!
	 * 
	 * @param chain
	 *            chain of certifications
	 * @throws CertificateException
	 *             CertificateException
	 */
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
			for (byte[] fingerprint : this.knownSha1Fingerprints) {
				if (Arrays.equals(certFingerprint, fingerprint)) {
					matched = true;
					break;
				}
			}

			if (!matched) {
				throw new CertificateException(
						"Unknown certificate fingerprint "
								+ Arrays.toString(certFingerprint));
			}
		} catch (NoSuchAlgorithmException e) {
			throw new CertificateException(
					"Cannot calculate SHA-1 because the algorithm is missing",
					e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return ACCEPTED_ISSUERS;
	}

	/**
	 * Convert fingerprint from {@link String} to {@link byte[]}.
	 * 
	 * @param knownFingerprint
	 *            fingerprint as : separated {@link String}
	 * @return fingerprint
	 */
	private static byte[] fingerprintToBytes(final String knownFingerprint) {
		final String[] byteTokens = knownFingerprint.split(":");
		final byte[] result = new byte[byteTokens.length];
		for (int i = 0; i < result.length; ++i) {
			result[i] = Integer.valueOf(byteTokens[i], HEX).byteValue();
		}
		return result;
	}
}
