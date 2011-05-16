package de.ub0r.android.websms.connector.common;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Fake Socket Factory.
 */
public final class FakeSocketFactory implements SocketFactory,
		LayeredSocketFactory {

	/** {@link SSLContext}. */
	private SSLContext sslcontext = null;
	/** Known good fingerprints. */
	private final String[] knownFingerprints;

	/** Default constructor. */
	public FakeSocketFactory() {
		this((String[]) null);
	}

	/**
	 * Constructor checking for known good fingerprints.
	 * 
	 * @param fingerprints
	 *            known good fingerprints
	 */
	public FakeSocketFactory(final String... fingerprints) {
		this.knownFingerprints = fingerprints;
	}

	/**
	 * Create a {@link SSLContext}.
	 * 
	 * @return {@link SSLContext}
	 * @throws IOException
	 *             IOException
	 */
	private SSLContext createEasySSLContext() throws IOException {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			final TrustManager trustManager;
			if (this.knownFingerprints == null) {
				trustManager = new FakeTrustManager();
			} else {
				trustManager = new KnownFingerprintTrustManager(
						this.knownFingerprints);
			}
			context.init(null, new TrustManager[] { trustManager }, null);
			return context;
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	/**
	 * @return {@link SSLContext}
	 * @throws IOException
	 *             IOException
	 */
	private SSLContext getSSLContext() throws IOException {
		if (this.sslcontext == null) {
			this.sslcontext = this.createEasySSLContext();
		}
		return this.sslcontext;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Socket connectSocket(final Socket sock, final String host,
			final int port, final InetAddress localAddress,
			final int localPort, final HttpParams params) throws IOException {
		int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
		int soTimeout = HttpConnectionParams.getSoTimeout(params);

		InetSocketAddress remoteAddress = new InetSocketAddress(host, port);
		SSLSocket sslsock = (SSLSocket) sock;
		if (sslsock == null) {
			this.createSocket();
		}

		if ((localAddress != null) || (localPort > 0)) {
			int lp = localPort;
			// we need to bind explicitly
			if (lp < 0) {
				lp = 0; // indicates "any"
			}
			InetSocketAddress isa = new InetSocketAddress(localAddress, lp);
			sslsock.bind(isa);
		}

		sslsock.connect(remoteAddress, connTimeout);
		sslsock.setSoTimeout(soTimeout);
		return sslsock;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Socket createSocket() throws IOException {
		return this.getSSLContext().getSocketFactory().createSocket();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isSecure(final Socket arg0) {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Socket createSocket(final Socket socket, final String host,
			final int port, final boolean autoClose) throws IOException {
		return this.getSSLContext().getSocketFactory().createSocket(socket,
				host, port, autoClose);
	}
}
