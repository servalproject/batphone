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

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.impl.cookie.CookieSpecBase;
import org.apache.http.message.BasicNameValuePair;

import android.util.Log;

/**
 * General Utils calls.
 * 
 * @author flx
 */
public final class Utils {
	/** Standard buffer size. */
	public static final int BUFSIZE = 32768;

	/** HTTP Response 200. */
	public static final int HTTP_SERVICE_OK = 200;
	/** HTTP Response 401. */
	public static final int HTTP_SERVICE_UNAUTHORIZED = 401;
	/** HTTP Response 500. */
	public static final int HTTP_SERVICE_500 = 500;
	/** HTTP Response 503. */
	public static final int HTTP_SERVICE_UNAVAILABLE = 503;

	/** Default port for HTTP. */
	private static final int PORT_HTTP = 80;
	/** Default port for HTTPS. */
	private static final int PORT_HTTPS = 443;

	/**
	 * No Constructor needed here.
	 */
	private Utils() {
		return;
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
		return s.split(",");
	}

	/**
	 * Join an array of recipients separated with separator.
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
		if (i >= 0) {
			return recipient.substring(0, i - 1);
		}
		return recipient;
	}

	/**
	 * Clean recipient's phone number from [ -.()].
	 * 
	 * @param recipient
	 *            recipient's mobile number
	 * @return clean number
	 */
	public static String cleanRecipient(final String recipient) {
		if (recipient == null) {
			return "";
		}
		return recipient.replace(" ", "").replace("-", "").replace(".", "")
				.replace("(", "").replace(")", "").replace("<", "").replace(
						">", "").trim();
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
		}
		return number;
	}

	/**
	 * Convert national number to international.
	 * 
	 * @param defPrefix
	 *            default prefix
	 * @param number
	 *            national number
	 * @return international number
	 */
	public static String national2international(final String defPrefix,
			final String number) {
		if (number.startsWith("0")) {
			return defPrefix + number.substring(1);
		}
		return number;
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
	 * @return the connection
	 * @throws IOException
	 *             IOException
	 */
	public static HttpResponse getHttpClient(final String url,
			final ArrayList<Cookie> cookies,
			final ArrayList<BasicNameValuePair> postData,
			final String userAgent, final String referer) throws IOException {
		// TODO flx, this method does not return an HttpClientInstance. It
		// should be executeRequest IMHO. Not so gut in public api?
		HttpClient client = new DefaultHttpClient();
		HttpRequestBase request;
		if (postData == null) {
			request = new HttpGet(url);
		} else {
			request = new HttpPost(url);
			((HttpPost) request).setEntity(new UrlEncodedFormEntity(postData,
					"ISO-8859-15"));// TODO make it as parameter
		}
		if (referer != null) {
			request.setHeader("Referer", referer);
		}
		if (userAgent != null) {
			request.setHeader("User-Agent", userAgent);
		}

		if (cookies != null && cookies.size() > 0) {
			CookieSpecBase cookieSpecBase = new BrowserCompatSpec();
			for (Header cookieHeader : cookieSpecBase.formatCookies(cookies)) {
				// Setting the cookie
				request.setHeader(cookieHeader);
			}
		}
		return client.execute(request);
	}

	/**
	 * Update cookies from response.
	 * 
	 * @param cookies
	 *            old cookie list
	 * @param headers
	 *            headers from response
	 * @param url
	 *            requested url
	 * @throws URISyntaxException
	 *             malformed uri
	 * @throws MalformedCookieException
	 *             malformed cookie
	 */
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
		CookieOrigin origin = new CookieOrigin(uri.getHost(), port, uri
				.getPath(), false);
		CookieSpecBase cookieSpecBase = new BrowserCompatSpec();
		for (Header header : headers) {
			for (Cookie cookie : cookieSpecBase.parse(header, origin)) {
				// THE cookie
				String name = cookie.getName();
				String value = cookie.getValue();
				if (value == null || value.equals("")) {
					continue;
				}
				for (Cookie c : cookies) {
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
	 * Read in data from Stream into String.
	 * 
	 * @param is
	 *            stream
	 * @return String
	 * @throws IOException
	 *             IOException
	 */
	public static String stream2str(final InputStream is) throws IOException {
		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(is), BUFSIZE);
		StringBuilder data = new StringBuilder();
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			data.append(line + "\n");
		}
		bufferedReader.close();
		return data.toString();
	}

	/**
	 * Calc MD5 Hash from String.
	 * 
	 * @param s
	 *            input
	 * @return hash
	 */
	public static String md5(final String s) {
		try {
			// Create MD5 Hash
			MessageDigest digest = java.security.MessageDigest
					.getInstance("MD5");
			digest.update(s.getBytes());
			byte[] messageDigest = digest.digest();
			// Create Hex String
			StringBuilder hexString = new StringBuilder(32);
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
		} catch (NoSuchAlgorithmException e) {
			Log.e("Utils", null, e);
		}
		return "";
	}
}
