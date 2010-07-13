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

	private SSLContext sslcontext = null;

	private static SSLContext createEasySSLContext() throws IOException {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { new FakeTrustManager() },
					null);
			return context;
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	private SSLContext getSSLContext() throws IOException {
		if (this.sslcontext == null) {
			this.sslcontext = createEasySSLContext();
		}
		return this.sslcontext;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Socket connectSocket(final Socket sock, final String host,
			final int port, final InetAddress localAddress, int localPort,
			final HttpParams params) throws IOException {
		int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
		int soTimeout = HttpConnectionParams.getSoTimeout(params);

		InetSocketAddress remoteAddress = new InetSocketAddress(host, port);
		SSLSocket sslsock = (SSLSocket) ((sock != null) ? sock : this
				.createSocket());

		if ((localAddress != null) || (localPort > 0)) {
			// we need to bind explicitly
			if (localPort < 0) {
				localPort = 0; // indicates "any"
			}
			InetSocketAddress isa = new InetSocketAddress(localAddress,
					localPort);
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
