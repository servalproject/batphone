package de.ub0r.android.websms.connector.common;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * All SSL certs are trusted!
 */
public final class FakeTrustManager implements X509TrustManager {

	/** ?? */
	private static final X509Certificate[] _AcceptedIssuers = // .
	new X509Certificate[] {};

	/**
	 * {@inheritDoc}
	 */
	public void checkClientTrusted(final X509Certificate[] chain,
			final String authType) throws CertificateException {
	}

	/**
	 * {@inheritDoc}
	 */
	public void checkServerTrusted(final X509Certificate[] chain,
			final String authType) throws CertificateException {
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isClientTrusted(final X509Certificate[] chain) {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isServerTrusted(final X509Certificate[] chain) {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public X509Certificate[] getAcceptedIssuers() {
		return _AcceptedIssuers;
	}
}
