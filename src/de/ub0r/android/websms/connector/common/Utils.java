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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * General Utils calls.
 * 
 * @author flx
 */
public final class Utils {
	/** Tag for output. */
	private static final String TAG = "WebSMS.con";

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

	/** Preference's name: use default sender. */
	public static final String PREFS_USE_DEFAULT_SENDER = "use_default_sender";
	/** Preference's name: custom sender. */
	public static final String PREFS_CUSTOM_SENDER = "custom_sender";

	/** Resturn only matching line in stream2str(). */
	public static final int ONLY_MATCHING_LINE = -2;

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
				ret.add(r.trim());
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
		Log.d(TAG, "HTTPClient URL: " + url);
		final DefaultHttpClient client = new DefaultHttpClient();
		HttpRequestBase request;
		if (postData == null) {
			request = new HttpGet(url);
		} else {
			request = new HttpPost(url);
			((HttpPost) request).setEntity(new UrlEncodedFormEntity(postData,
					"ISO-8859-15")); // TODO make it as parameter
			Log.d(TAG, "HTTPClient POST: " + postData);
		}
		if (referer != null) {
			request.setHeader("Referer", referer);
			Log.d(TAG, "HTTPClient REF: " + referer);
		}
		if (userAgent != null) {
			request.setHeader("User-Agent", userAgent);
			Log.d(TAG, "HTTPClient AGENT: " + userAgent);
		}

		if (cookies != null && cookies.size() > 0) {
			final CookieSpecBase cookieSpecBase = new BrowserCompatSpec();
			for (final Header cookieHeader : cookieSpecBase
					.formatCookies(cookies)) {
				// Setting the cookie
				request.setHeader(cookieHeader);
				Log.d(TAG, "HTTPClient COOKIE: " + cookieHeader);
			}
		}
		return client.execute(request);
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
		return stream2str(is, start, end, null);
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
		boolean foundPattern = false;
		if (pattern == null) {
			foundPattern = true;
		}
		final BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(is), BUFSIZE);
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
					Log.d("utils", "skipped: " + skipped);
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
			Log.e("Utils", null, e);
		}
		return "";
	}
}
