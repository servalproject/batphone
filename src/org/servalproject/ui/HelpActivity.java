/* Copyright (C) 2012 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.servalproject.ui;

import org.servalproject.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

/**
 * main activity for contact management
 */
public class HelpActivity extends Activity implements OnClickListener {

	/*
	 * private class level constants
	 */
	// private final boolean V_LOG = true;
	private final String TAG = "HelpActivity";

	private final int PEER_LIST_RETURN = 0;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.help_main);

		// attach handlers to the button
		ViewGroup mButton = (ViewGroup) findViewById(R.id.help_screen);
		mButton.setOnClickListener(this);

		mButton = (ViewGroup) findViewById(R.id.help_screen);
		mButton.setOnClickListener(this);

	}

}
