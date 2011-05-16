/*
 * Copyright (C) 2010 Felix Bechstein
 * 
 * This file is part of WebSMS.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */

package de.ub0r.android.websms.connector.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.impl.cookie.CookieSpecBase;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * General Utils calls.
 * 
 * @author flx
 */
public final class Utils {
	/** Tag for output. */
	private static final String TAG = "utl";

	/** Standard buffer size. */
	public static final int BUFSIZE = 32768;

	/** HTTP Response 200. */
	@Deprecated
	public static final int HTTP_SERVICE_OK = HttpStatus.SC_OK;
	/** HTTP Response 401. */
	@Deprecated
	public static final int HTTP_SERVICE_UNAUTHORIZED = // .
	HttpStatus.SC_UNAUTHORIZED;
	/** HTTP Response 500. */
	@Deprecated
	public static final int HTTP_SERVICE_500 = // .
	HttpStatus.SC_INTERNAL_SERVER_ERROR;
	/** HTTP Response 503. */
	@Deprecated
	public static final int HTTP_SERVICE_UNAVAILABLE = // .
	HttpStatus.SC_SERVICE_UNAVAILABLE;

	/** Gzip. */
	private static final String GZIP = "gzip";
	/** Accept-Encoding. */
	private static final String ACCEPT_ENCODING = "Accept-Encoding";

	/** Default port for HTTP. */
	private static final int PORT_HTTP = 80;
	/** Default port for HTTPS. */
	private static final int PORT_HTTPS = 443;

	/** Preference's name: use default sender. */
	public static final String PREFS_USE_DEFAULT_SENDER = "use_default_sender";
	/** Preference's name: custom sender. */
	public static final String PREFS_CUSTOM_SENDER = "custom_sender";

	/** Resturn only matching line in stream2str(). */
	public static final int ONLY_MATCHING_LINE = -2;

	/** Common {@link HttpClient}. */
	private static DefaultHttpClient httpClient = null;

	/**
	 * {@link HttpEntityWrapper} to wrap giziped content.
	 * 
	 * @author flx
	 */
	public static final class GzipDecompressingEntity // .
			extends HttpEntityWrapper {
		/**
		 * Default Constructor.
		 * 
		 * @param entity
		 *            {@link HttpEntity}
		 */
		public GzipDecompressingEntity(final HttpEntity entity) {
			super(entity);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public InputStream getContent() throws IOException {
			Log.d(TAG, "unzip content");
			InputStream wrappedin = this.wrappedEntity.getContent();
			return new GZIPInputStream(wrappedin);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public long getContentLength() {
			return -1;
		}
	}

	/**
	 * No Constructor needed here.
	 */
	private Utils() {
		return;
	}

	/**
	 * Get custom sender from preferences by users choice. Else: default sender
	 * is selected.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param defSender
	 *            default Sender
	 * @return selected Sender
	 */
	public static String getSender(final Context context, // .
			final String defSender) {
		if (context == null) {
			return defSender;
		}
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (p.getBoolean(PREFS_USE_DEFAULT_SENDER, true)) {
			return defSender;
		}
		final String s = p.getString(PREFS_CUSTOM_SENDER, "");
		if (s == null || s.length() == 0) {
			return defSender;
		}
		return s;
	}

	/**
	 * Get custom sender number from preferences by users choice. Else: default
	 * sender is selected.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param defSender
	 *            default Sender
	 * @return selected Sender
	 */
	public static String getSenderNumber(final Context context, // .
			final String defSender) {
		if (context == null) {
			return defSender;
		}
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (p.getBoolean(PREFS_USE_DEFAULT_SENDER, true)) {
			return defSender;
		}
		final String s = p.getString(PREFS_CUSTOM_SENDER, "");
		if (s == null || s.length() == 0) {
			return defSender;
		}
		final String sn = s.replaceAll("(\\+|[0-9])", "");
		if (sn.length() > 0) {
			Log.d(TAG, "fall back to default numer: " + sn);
			return defSender;
		}
		return s;
	}

	/**
	 * Parse a String of "name <number>, name <number>, number, ..." to an array
	 * of "name <number>".
	 * 
	 * @param recipients
	 *            recipients
	 * @return array of recipients
	 */
	public static String[] parseRecipients(final String recipients) {
		String s = recipients.trim();
		if (s.endsWith(",")) {
			s = s.substring(0, s.length() - 1);
		}
		ArrayList<String> ret = new ArrayList<String>();
		String[] ss = s.split(",");
		final int l = ss.length;
		String r = null;
		String rr;
		for (int i = 0; i < l; i++) {
			rr = ss[i];
			if (r == null) {
				r = rr;
			} else {
				r += "," + rr;
			}
			if (rr.contains("0") || rr.contains("1") || rr.contains("2")
					|| rr.contains("3") || rr.contains("4") || rr.contains("5")
					|| rr.contains("6") || rr.contains("7") || rr.contains("8")
					|| rr.contains("9")) {
				r = r.trim();
				final String na = getRecipientsName(r);
				final String nu = cleanRecipient(getRecipientsNumber(r));
				if (na != null && na.trim().length() > 0) {
					r = na + " <" + nu + ">";
				} else {
					r = nu;
				}
				ret.add(r);
				r = null;
			}
		}
		return ret.toArray(new String[0]);
	}

	/**
	 * Join an array of recipients separated with separator.
	 * 
	 * @param recipients
	 *            recipients
	 * @param separator
	 *            separator
	 * @return joined recipients
	 */
	public static String joinRecipients(final String[] recipients,
			final String separator) {
		if (recipients == null) {
			return null;
		}
		final int e = recipients.length;
		if (e == 0) {
			return null;
		}
		final StringBuilder buf = new StringBuilder(recipients[0]);
		for (int i = 1; i < e; i++) {
			buf.append(separator);
			buf.append(recipients[i]);
		}
		return buf.toString();
	}

	/**
	 * Join an array of recipients separated with separator, stripped to only
	 * contain numbers.
	 * 
	 * @param recipients
	 *            recipients
	 * @param separator
	 *            separator
	 * @param oldFormat
	 *            Use old international format. E.g. 0049, not +49.
	 * @return joined recipients
	 */
	public static String joinRecipientsNumbers(final String[] recipients,
			final String separator, final boolean oldFormat) {
		if (recipients == null) {
			return null;
		}
		final int e = recipients.length;
		if (e == 0) {
			return null;
		}
		final StringBuilder buf = new StringBuilder();
		if (oldFormat) {
			buf.append(international2oldformat(// .
					getRecipientsNumber(recipients[0])));
		} else {
			buf.append(getRecipientsNumber(recipients[0]));
		}
		for (int i = 1; i < e; i++) {
			buf.append(separator);
			if (oldFormat) {
				buf.append(international2oldformat(// .
						getRecipientsNumber(recipients[i])));
			} else {
				buf.append(getRecipientsNumber(recipients[i]));
			}
		}
		return buf.toString();
	}

	/**
	 * Get a recipient's number.
	 * 
	 * @param recipient
	 *            recipient
	 * @return recipient's number
	 */
	public static String getRecipientsNumber(final String recipient) {
		final int i = recipient.lastIndexOf('<');
		if (i >= 0) {
			final int j = recipient.indexOf('>', i);
			if (j > 0) {
				return recipient.substring(i + 1, j);
			}
		}
		return recipient;
	}

	/**
	 * Get a recipient's name.
	 * 
	 * @param recipient
	 *            recipient
	 * @return recipient's name
	 */
	public static String getRecipientsName(final String recipient) {
		final int i = recipient.lastIndexOf('<');
		if (i > 0) {
			return recipient.substring(0, i - 1).trim();
		}
		return recipient;
	}

	/**
	 * Clean recipient's phone number from [ -.()<>].
	 * 
	 * @param recipient
	 *            recipient's mobile number
	 * @return clean number
	 */
	public static String cleanRecipient(final String recipient) {
		if (recipient == null) {
			return "";
		}
		return recipient.replaceAll("[^*#+0-9]", "") // .
				.replaceAll("^[*#][0-9]*#", "");
	}

	/**
	 * Convert international number to national.
	 * 
	 * @param defPrefix
	 *            default prefix
	 * @param number
	 *            international number
	 * @return national number
	 */
	public static String international2national(final String defPrefix,
			final String number) {
		if (number.startsWith(defPrefix)) {
			return '0' + number.substring(defPrefix.length());
		} else if (number.startsWith("00" + defPrefix.substring(1))) {
			return '0' + number.substring(defPrefix.length() + 1);
		}
		return number;
	}

	/**
	 * Convert national number to international. Old format internationals were
	 * converted to new format.
	 * 
	 * @param defPrefix
	 *            default prefix
	 * @param number
	 *            national number
	 * @return international number
	 */
	public static String national2international(final String defPrefix,
			final String number) {
		if (number.startsWith("+")) {
			return number;
		} else if (number.startsWith("00")) {
			return "+" + number.substring(2);
		} else if (number.startsWith("0")) {
			return defPrefix + number.substring(1);
		}
		return defPrefix + number;
	}

	/**
	 * Convert national number to international.
	 * 
	 * @param defPrefix
	 *            default prefix
	 * @param number
	 *            national numbers
	 * @return international numbers
	 */
	public static String[] national2international(final String defPrefix,
			final String[] number) {
		final int l = number.length;
		String[] n = new String[l];
		for (int i = 0; i < l; i++) {
			n[i] = national2international(defPrefix,
					getRecipientsNumber(number[i]));
		}
		return n;
	}

	/**
	 * Convert international number to old format. Eg. +49123 to 0049123
	 * 
	 * @param number
	 *            international number starting with +
	 * @return international number in old format starting with 00
	 */
	public static String international2oldformat(final String number) {
		if (number.startsWith("+")) {
			return "00" + number.substring(1);
		}
		return number;
	}

	/**
	 * Get a fresh HTTP-Connection. Please use getHttpClient(url, cookies,
	 * postData, userAgent, referer, false).
	 * 
	 * @param url
	 *            URL to open
	 * @param cookies
	 *            cookies to transmit
	 * @param postData
	 *            post data
	 * @param userAgent
	 *            user agent
	 * @param referer
	 *            referer
	 * @return the connection
	 * @throws IOException
	 *             IOException
	 */
	@Deprecated
	public static HttpResponse getHttpClient(final String url,
			final ArrayList<Cookie> cookies,
			final ArrayList<BasicNameValuePair> postData,
			final String userAgent, final String referer) throws IOException {
		return getHttpClient(url, cookies, postData, userAgent, referer, false);
	}

	/**
	 * Print all cookies from {@link CookieStore} to {@link String}.
	 * 
	 * @param client
	 *            {@link DefaultHttpClient}
	 * @return {@link Cookie}s formated for debug out
	 */
	private static String getCookies(final DefaultHttpClient client) {
		String ret = "cookies:";
		for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
			ret += "\n" + cookie.getName() + ": " + cookie.getValue();
		}
		ret += "\nend of cookies";
		return ret;
	}

	/**
	 * Print all {@link Header}s from {@link HttpRequest} to {@link String}.
	 * 
	 * @param request
	 *            {@link HttpRequest}
	 * @return {@link Header}s formated for debug out
	 */
	private static String getHeaders(final HttpRequest request) {
		String ret = "headers:";
		for (Header h : request.getAllHeaders()) {
			ret += "\n" + h.getName() + ": " + h.getValue();
		}
		ret += "\nend of headers";
		return ret;
	}

	/**
	 * Get {@link Cookie}s stored in static {@link CookieStore}.
	 * 
	 * @return {@link ArrayList} of {@link Cookie}s
	 */
	public static ArrayList<Cookie> getCookies() {
		if (httpClient == null) {
			return null;
		}
		List<Cookie> cookies = httpClient.getCookieStore().getCookies();
		if (cookies == null || cookies.size() == 0) {
			return null;
		}
		ArrayList<Cookie> ret = new ArrayList<Cookie>(cookies.size());
		ret.addAll(cookies);
		return ret;
	}

	/**
	 * Get the number of {@link Cookie}s stored in static {@link CookieStore}.
	 * 
	 * @return number of {@link Cookie}s
	 */
	public static int getCookieCount() {
		if (httpClient == null) {
			return 0;
		}
		List<Cookie> cookies = httpClient.getCookieStore().getCookies();
		if (cookies == null) {
			return 0;
		}
		return cookies.size();
	}

	/**
	 * Get cookies as {@link String}.
	 * 
	 * @return cookies
	 */
	public static String getCookiesAsString() {
		if (httpClient == null) {
			return null;
		}
		return getCookies(httpClient);
	}

	/**
	 * Clear internal cookie cache.
	 */
	public static void clearCookies() {
		if (httpClient != null) {
			final CookieStore cs = httpClient.getCookieStore();
			if (cs != null) {
				cs.clear();
			}
		}
	}

	/**
	 * Get a fresh HTTP-Connection.
	 * 
	 * @param url
	 *            URL to open
	 * @param cookies
	 *            cookies to transmit
	 * @param postData
	 *            post data
	 * @param userAgent
	 *            user agent
	 * @param referer
	 *            referer
	 * @param trustAll
	 *            trust all SSL certificates; only used on first call!
	 * @return the connection
	 * @throws IOException
	 *             IOException
	 */
	@Deprecated
	public static HttpResponse getHttpClient(final String url,
			final ArrayList<Cookie> cookies,
			final ArrayList<BasicNameValuePair> postData,
			final String userAgent, final String referer, // .
			final boolean trustAll) throws IOException {
		return getHttpClient(url, cookies, postData, userAgent, referer, null,
				trustAll, (String[]) null);
	}

	/**
	 * Get a fresh HTTP-Connection.
	 * 
	 * @param url
	 *            URL to open
	 * @param cookies
	 *            cookies to transmit
	 * @param postData
	 *            post data
	 * @param userAgent
	 *            user agent
	 * @param referer
	 *            referer
	 * @param encoding
	 *            encoding; default encoding: ISO-8859-15
	 * @param trustAll
	 *            trust all SSL certificates; only used on first call!
	 * @return the connection
	 * @throws IOException
	 *             IOException
	 */
	public static HttpResponse getHttpClient(final String url,
			final ArrayList<Cookie> cookies,
			final ArrayList<BasicNameValuePair> postData,
			final String userAgent, final String referer,
			final String encoding, final boolean trustAll) throws IOException {
		return getHttpClient(url, cookies, postData, userAgent, referer,
				encoding, trustAll, (String[]) null);
	}

	/**
	 * Get a fresh HTTP-Connection.
	 * 
	 * @param url
	 *            URL to open
	 * @param cookies
	 *            cookies to transmit
	 * @param postData
	 *            post data
	 * @param userAgent
	 *            user agent
	 * @param referer
	 *            referer
	 * @param knownFingerprints
	 *            fingerprints that are known to be valid; only used on first
	 *            call!
	 * @return the connection
	 * @throws IOException
	 *             IOException
	 */
	@Deprecated
	public static HttpResponse getHttpClient(final String url,
			final ArrayList<Cookie> cookies,
			final ArrayList<BasicNameValuePair> postData,
			final String userAgent, final String referer, // .
			final String... knownFingerprints) throws IOException {
		return getHttpClient(url, cookies, postData, userAgent, referer, null,
				false, knownFingerprints);
	}

	/**
	 * Get a fresh HTTP-Connection.
	 * 
	 * @param url
	 *            URL to open
	 * @param cookies
	 *            cookies to transmit
	 * @param postData
	 *            post data
	 * @param userAgent
	 *            user agent
	 * @param referer
	 *            referer
	 * @param encoding
	 *            encoding; default encoding: ISO-8859-15
	 * @param knownFingerprints
	 *            fingerprints that are known to be valid; only used on first
	 *            call!
	 * @return the connection
	 * @throws IOException
	 *             IOException
	 */
	public static HttpResponse getHttpClient(final String url,
			final ArrayList<Cookie> cookies,
			final ArrayList<BasicNameValuePair> postData,
			final String userAgent, final String referer,
			final String encoding, final String... knownFingerprints)
			throws IOException {
		return getHttpClient(url, cookies, postData, userAgent, referer,
				encoding, false, knownFingerprints);
	}

	/**
	 * Get a fresh HTTP-Connection.
	 * 
	 * @param url
	 *            URL to open
	 * @param cookies
	 *            cookies to transmit
	 * @param postData
	 *            post data
	 * @param userAgent
	 *            user agent
	 * @param referer
	 *            referer
	 * @param encoding
	 *            encoding; default encoding: ISO-8859-15
	 * @param trustAll
	 *            trust all SSL certificates; only used on first call!
	 * @param knownFingerprints
	 *            fingerprints that are known to be valid; only used on first
	 *            call! Only used if {@code trustAll == false}
	 * @return the connection
	 * @throws IOException
	 *             IOException
	 */
	private static HttpResponse getHttpClient(final String url,
			final ArrayList<Cookie> cookies,
			final ArrayList<BasicNameValuePair> postData,
			final String userAgent, final String referer,
			final String encoding, final boolean trustAll,
			final String... knownFingerprints) throws IOException {
		Log.d(TAG, "HTTPClient URL: " + url);

		SchemeRegistry registry = null;
		if (httpClient == null) {
			if (trustAll || (// .
					knownFingerprints != null && // .
					knownFingerprints.length > 0)) {
				registry = new SchemeRegistry();
				registry.register(new Scheme("http", new PlainSocketFactory(),
						PORT_HTTP));
				final FakeSocketFactory httpsSocketFactory;
				if (trustAll) {
					httpsSocketFactory = new FakeSocketFactory();
				} else {
					httpsSocketFactory = new FakeSocketFactory(
							knownFingerprints);
				}
				registry.register(new Scheme("https", httpsSocketFactory,
						PORT_HTTPS));
				HttpParams params = new BasicHttpParams();
				httpClient = new DefaultHttpClient(
						new ThreadSafeClientConnManager(params, registry),
						params);
			} else {
				httpClient = new DefaultHttpClient();
			}
			httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
				public void process(final HttpResponse response,
						final HttpContext context) throws HttpException,
						IOException {
					HttpEntity entity = response.getEntity();
					Header contentEncodingHeader = entity.getContentEncoding();
					if (contentEncodingHeader != null) {
						HeaderElement[] codecs = contentEncodingHeader
								.getElements();
						for (int i = 0; i < codecs.length; i++) {
							if (codecs[i].getName().equalsIgnoreCase(GZIP)) {
								response.setEntity(new GzipDecompressingEntity(
										response.getEntity()));
								return;
							}
						}
					}
				}
			});
		}
		if (cookies != null && cookies.size() > 0) {
			final int l = cookies.size();
			CookieStore cs = httpClient.getCookieStore();
			for (int i = 0; i < l; i++) {
				cs.addCookie(cookies.get(i));
			}
		}
		Log.d(TAG, getCookies(httpClient));

		HttpRequestBase request;
		if (postData == null) {
			request = new HttpGet(url);
		} else {
			HttpPost pr = new HttpPost(url);
			if (encoding != null && encoding.length() > 0) {
				pr.setEntity(new UrlEncodedFormEntity(postData, encoding));
			} else {
				pr.setEntity(new UrlEncodedFormEntity(postData, "ISO-8859-15"));
			}
			// Log.d(TAG, "HTTPClient POST: " + postData);
			request = pr;
		}
		request.addHeader(ACCEPT_ENCODING, GZIP);
		if (referer != null) {
			request.setHeader("Referer", referer);
			// Log.d(TAG, "HTTPClient REF: " + referer);
		}
		if (userAgent != null) {
			request.setHeader("User-Agent", userAgent);
			// Log.d(TAG, "HTTPClient AGENT: " + userAgent);
		}
		// Log.d(TAG, getHeaders(request));
		return httpClient.execute(request);
	}

	/**
	 * Update cookies from response.
	 * 
	 * @param cookies
	 *            old {@link Cookie} list
	 * @param headers
	 *            {@link Header}s from {@link HttpResponse}
	 * @param url
	 *            requested URL
	 * @throws URISyntaxException
	 *             malformed URI
	 * @throws MalformedCookieException
	 *             malformed {@link Cookie}
	 */
	@Deprecated
	public static void updateCookies(final ArrayList<Cookie> cookies,
			final Header[] headers, final String url)
			throws URISyntaxException, MalformedCookieException {
		final URI uri = new URI(url);
		int port = uri.getPort();
		if (port < 0) {
			if (url.startsWith("https")) {
				port = PORT_HTTPS;
			} else {
				port = PORT_HTTP;
			}
		}
		final CookieOrigin origin = new CookieOrigin(uri.getHost(), port, uri
				.getPath(), false);
		final CookieSpecBase cookieSpecBase = new BrowserCompatSpec();
		String name;
		String value;
		for (final Header header : headers) {
			for (final Cookie cookie : cookieSpecBase.parse(header, origin)) {
				// THE cookie
				name = cookie.getName();
				value = cookie.getValue();
				if (value == null || value.equals("")) {
					continue;
				}
				for (final Cookie c : cookies) {
					if (name.equals(c.getName())) {
						cookies.remove(c);
						cookies.add(cookie);
						name = null;
						break;
					}
				}
				if (name != null) {
					cookies.add(cookie);
				}
			}
		}
	}

	/**
	 * Read {@link InputStream} and convert it into {@link String}.
	 * 
	 * @param is
	 *            {@link InputStream} to read from
	 * @return {@link String} holding all the bytes from the {@link InputStream}
	 * @throws IOException
	 *             IOException
	 */
	public static String stream2str(final InputStream is) throws IOException {
		return stream2str(is, 0, -1, null);
	}

	/**
	 * Read {@link InputStream} and convert it into {@link String}.
	 * 
	 * @param is
	 *            {@link InputStream} to read from param charset to read the
	 *            {@link InputStream}. Can be null.
	 * @param charset
	 *            charset to be used to read {@link InputStream}. Can be null.
	 * @return {@link String} holding all the bytes from the {@link InputStream}
	 * @throws IOException
	 *             IOException
	 */
	public static String stream2str(final InputStream is, final String charset)
			throws IOException {
		return stream2str(is, 0, -1, null);
	}

	/**
	 * Read {@link InputStream} and convert it into {@link String}.
	 * 
	 * @param is
	 *            {@link InputStream} to read from
	 * @param start
	 *            first characters of stream that should be fetched. Set to 0,
	 *            if nothing should be skipped.
	 * @param end
	 *            last characters of stream that should be fetched. This method
	 *            might read some more characters. Set to -1 if all characters
	 *            should be read.
	 * @return {@link String} holding all the bytes from the {@link InputStream}
	 * @throws IOException
	 *             IOException
	 */
	public static String stream2str(final InputStream is, final int start,
			final int end) throws IOException {
		return stream2str(is, null, start, end);
	}

	/**
	 * Read {@link InputStream} and convert it into {@link String}.
	 * 
	 * @param is
	 *            {@link InputStream} to read from param charset to read the
	 *            {@link InputStream}. Can be null.
	 * @param charset
	 *            charset to be used to read {@link InputStream}. Can be null.
	 * @param start
	 *            first characters of stream that should be fetched. Set to 0,
	 *            if nothing should be skipped.
	 * @param end
	 *            last characters of stream that should be fetched. This method
	 *            might read some more characters. Set to -1 if all characters
	 *            should be read.
	 * @return {@link String} holding all the bytes from the {@link InputStream}
	 * @throws IOException
	 *             IOException
	 */
	public static String stream2str(final InputStream is, final String charset,
			final int start, final int end) throws IOException {
		return stream2str(is, charset, start, end, null);
	}

	/**
	 * Read {@link InputStream} and convert it into {@link String}.
	 * 
	 * @param is
	 *            {@link InputStream} to read from
	 * @param start
	 *            first characters of stream that should be fetched. Set to 0,
	 *            if nothing should be skipped.
	 * @param end
	 *            last characters of stream that should be fetched. This method
	 *            might read some more characters. Set to -1 if all characters
	 *            should be read.
	 * @param pattern
	 *            start reading at this pattern, set end = -2 to return only the
	 *            line, matching this pattern
	 * @return {@link String} holding all the bytes from the {@link InputStream}
	 * @throws IOException
	 *             IOException
	 */
	public static String stream2str(final InputStream is, final int start,
			final int end, final String pattern) throws IOException {
		return stream2str(is, null, start, end, pattern);
	}

	/**
	 * Read {@link InputStream} and convert it into {@link String}.
	 * 
	 * @param is
	 *            {@link InputStream} to read from
	 * @param charset
	 *            charset to be used to read {@link InputStream}. Can be null.
	 * @param start
	 *            first characters of stream that should be fetched. Set to 0,
	 *            if nothing should be skipped.
	 * @param end
	 *            last characters of stream that should be fetched. This method
	 *            might read some more characters. Set to -1 if all characters
	 *            should be read.
	 * @param pattern
	 *            start reading at this pattern, set end = -2 to return only the
	 *            line, matching this pattern
	 * @return {@link String} holding all the bytes from the {@link InputStream}
	 * @throws IOException
	 *             IOException
	 */
	public static String stream2str(final InputStream is, final String charset,
			final int start, final int end, final String pattern)
			throws IOException {
		boolean foundPattern = false;
		if (pattern == null) {
			foundPattern = true;
		}
		InputStreamReader r;
		if (charset == null) {
			r = new InputStreamReader(is);
		} else {
			r = new InputStreamReader(is, charset);
		}
		final BufferedReader bufferedReader = new BufferedReader(r, BUFSIZE);
		final StringBuilder data = new StringBuilder();
		String line = null;
		long totalSkipped = 0;
		long skipped = 0;
		while (start > totalSkipped) {
			skipped = bufferedReader.skip(start - totalSkipped);
			if (skipped == 0) {
				break;
			}
			totalSkipped += skipped;
		}
		skipped = 0;
		while ((line = bufferedReader.readLine()) != null) {
			skipped += line.length() + 1;
			if (!foundPattern) {
				if (line.indexOf(pattern) >= 0) {
					if (end == ONLY_MATCHING_LINE) {
						return line;
					}
					foundPattern = true;
					Log.d(TAG, "skipped: " + skipped);
				}
			}
			if (foundPattern) {
				data.append(line + "\n");

			}
			if (end >= 0 && skipped > (end - start)) {
				break;
			}
		}
		bufferedReader.close();
		if (!foundPattern) {
			return null;
		}
		return data.toString();
	}

	/**
	 * Generate MD5 Hash from String.
	 * 
	 * @param s
	 *            input
	 * @return hash
	 */
	public static String md5(final String s) {
		try {
			// Create MD5 Hash
			final MessageDigest digest = java.security.MessageDigest
					.getInstance("MD5");
			digest.update(s.getBytes());
			final byte[] messageDigest = digest.digest();
			// Create Hex String
			final StringBuilder hexString = new StringBuilder(32);
			int b;
			for (int i = 0; i < messageDigest.length; i++) {
				b = 0xFF & messageDigest[i];
				if (b < 0x10) {
					hexString.append('0' + Integer.toHexString(b));
				} else {
					hexString.append(Integer.toHexString(b));
				}
			}
			return hexString.toString();
		} catch (final NoSuchAlgorithmException e) {
			Log.e(TAG, null, e);
		}
		return "";
	}
}
