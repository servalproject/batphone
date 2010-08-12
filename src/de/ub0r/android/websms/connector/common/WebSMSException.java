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

import android.content.Context;

/**
 * Exception while Connector IO.
 * 
 * @author flx
 */
public class WebSMSException extends RuntimeException {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -6215729019426883487L;

	/**
	 * Create a new WebSMSException.
	 * 
	 * @param s
	 *            error message
	 */
	public WebSMSException(final String s) {
		super(s);
	}

	/**
	 * Create a new {@link WebSMSException}.
	 * 
	 * @param ex
	 *            a {@link Throwable} instance
	 */
	public WebSMSException(final Throwable ex) {
		super(ex);
	}

	/**
	 * Create a new {@link WebSMSException}.
	 * 
	 * @param ex
	 *            a {@link WebSMSException} instance
	 */
	public WebSMSException(final WebSMSException ex) {
		super(ex.getMessage());
	}

	/**
	 * Create a new WebSMSException.
	 * 
	 * @param c
	 *            Context to resolve resource id
	 * @param rid
	 *            error message as resource id
	 */
	public WebSMSException(final Context c, final int rid) {
		super(c.getString(rid));
	}

	/**
	 * Create a new WebSMSException.
	 * 
	 * @param c
	 *            Context to resolve resource id
	 * @param rid
	 *            error message as resource id
	 * @param s
	 *            error message
	 */
	public WebSMSException(final Context c, final int rid, final String s) {
		super(c.getString(rid) + s);
	}
}
