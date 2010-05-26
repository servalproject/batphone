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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.Intent;

/**
 * AsyncTask to manage IO to a basic WebAPI.
 * 
 * @author flx
 */
public abstract class BasicConnector extends Connector {
	/** Tag for output. */
	private static final String TAG = "bcon";

	/**
	 * @return gateway URL for sending
	 */
	protected abstract String getUrlSend();

	/** @return gateway URL for balance update */
	protected abstract String getUrlBalance();

	/**
	 * Use HTTP POST for transmitting data to the Service.
	 * 
	 * @return true to use POST, default implementation returns true
	 */
	protected boolean usePost() {
		return true;
	}

	/** @return API param for username */
	protected abstract String getParamUsername();

	/** @return API param for password */
	protected abstract String getParamPassword();

	/** @return API param for recipients */
	protected abstract String getParamRecipients();

	/** @return API param for text */
	protected abstract String getParamText();

	/** @return API param for sender */
	protected abstract String getParamSender();

	/** @return API param for send later. Default returns null. */
	protected String getParamSendLater() {
		return null;
	}

	/**
	 * @return API param name for subconnector. Default Implementation returns
	 *         null.
	 */
	protected String getParamSubconnector() {
		return null;
	}

	/** @return API param for flash type. Default returns null. */
	protected String getParamFlash() {
		return null;
	}

	/**
	 * @param command
	 *            {@link ConnectorCommand}
	 * @return the username for authorization at the WebAPI.
	 */
	protected abstract String getUsername(final ConnectorCommand command);

	/**
	 * @param command
	 *            {@link ConnectorCommand}
	 * @return the password for authorization at the WebAPI.
	 */
	protected abstract String getPassword(final ConnectorCommand command);

	/**
	 * Get used subconnector. Default implementation returns just the param
	 * subcon.
	 * 
	 * @param subcon
	 *            subconnector
	 * @return value for subconnector
	 */
	protected String getSubconnector(final String subcon) {
		return subcon;
	}

	/**
	 * Get message's text. Default implementation returns just the param text.
	 * 
	 * @param text
	 *            message body
	 * @return value for text
	 */
	protected String getText(final String text) {
		return text;
	}

	/**
	 * @param command
	 *            {@link ConnectorCommand}
	 * @return value for sender.
	 */
	protected abstract String getSender(final ConnectorCommand command);

	/**
	 * @param command
	 *            {@link ConnectorCommand}
	 * @return value for recipients.
	 */
	protected abstract String getRecipients(final ConnectorCommand command);

	/**
	 * @param sendLater
	 *            send later
	 * @return value for send later
	 */
	protected String getSendLater(final long sendLater) {
		return String.valueOf(sendLater);
	}

	/**
	 * Parse response.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param command
	 *            {@link ConnectorCommand}
	 * @param cs
	 *            {@link ConnectorSpec}
	 * @param htmlText
	 *            HTTP response body
	 * @throws WebSMSException
	 *             WebSMSException
	 */
	protected abstract void parseResponse(final Context context,
			final ConnectorCommand command, final ConnectorSpec cs,
			final String htmlText) throws WebSMSException;

	/**
	 * Parse HTTP response code. Default implementation throws
	 * {@link WebSMSException} if resp != HTTP_OK.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param resp
	 *            HTTP response code
	 * @throws WebSMSException
	 *             WebSMSException
	 */
	protected void parseResponseCode(final Context context, final int resp)
			throws WebSMSException {
		if (resp != HttpURLConnection.HTTP_OK) {
			throw new WebSMSException(context, R.string.error_http, " " + resp);
		}
	}

	/**
	 * Send data.
	 * 
	 * @param context
	 *            Context
	 * @param command
	 *            ConnectorCommand
	 * @throws WebSMSException
	 *             WebSMSException
	 */
	private void sendData(final Context context, final ConnectorCommand command)
			throws WebSMSException {
		// do IO
		try { // get Connection
			final ConnectorSpec cs = this.getSpec(context);
			String url;
			ArrayList<BasicNameValuePair> d = // .
			new ArrayList<BasicNameValuePair>();
			final String text = command.getText();
			if (text != null && text.length() > 0) {
				url = this.getUrlSend();
				final String subCon = command.getSelectedSubConnector();
				d.add(new BasicNameValuePair(this.getParamText(), this
						.getText(text)));

				d.add(new BasicNameValuePair(this.getParamRecipients(), this
						.getRecipients(command)));

				if (command.getFlashSMS()) {
					d.add(new BasicNameValuePair(this.getParamSubconnector(),
							this.getParamFlash()));
				} else {
					d.add(new BasicNameValuePair(this.getParamSubconnector(),
							this.getSubconnector(subCon)));
				}

				final String customSender = command.getCustomSender();
				if (customSender == null) {
					d.add(new BasicNameValuePair(this.getParamSender(), this
							.getSender(command)));
				} else {
					d.add(new BasicNameValuePair(this.getParamSender(),
							customSender));
				}
				final long sendLater = command.getSendLater();
				final String pSendLater = this.getParamSendLater();
				if (sendLater > 0 && pSendLater != null) {
					d.add(new BasicNameValuePair(pSendLater, this
							.getSendLater(sendLater)));
				}
			} else {
				url = this.getUrlBalance();
			}

			d.add(new BasicNameValuePair(this.getParamUsername(), this
					.getUsername(command)));
			d.add(new BasicNameValuePair(this.getParamPassword(), this
					.getPassword(command)));

			if (!this.usePost()) {
				StringBuilder u = new StringBuilder(url);
				u.append("?");
				final int l = d.size();
				for (int i = 0; i < l; i++) {
					BasicNameValuePair nv = d.get(i);
					u.append(nv.getName());
					u.append("=");
					u.append(URLEncoder.encode(nv.getValue()));
					u.append("&");
				}
				url = u.toString();
				d = null;
			}
			Log.d(TAG, "HTTP REQUEST: " + url);
			final HttpResponse response = Utils.getHttpClient(url, null, d,
					null, null);
			int resp = response.getStatusLine().getStatusCode();
			this.parseResponseCode(context, resp);
			final String htmlText = Utils.stream2str(
					response.getEntity().getContent()).trim();
			Log.d(TAG, "HTTP RESPONSE: " + htmlText);
			this.parseResponse(context, command, cs, htmlText);
		} catch (IOException e) {
			Log.e(TAG, null, e);
			throw new WebSMSException(e.getMessage());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doUpdate(final Context context, final Intent intent)
			throws WebSMSException {
		this.sendData(context, new ConnectorCommand(intent));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doSend(final Context context, final Intent intent)
			throws WebSMSException {
		this.sendData(context, new ConnectorCommand(intent));
	}
}
