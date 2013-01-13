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
package org.servalproject.ui.help;

import org.servalproject.R;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

/**
 * help screens - permissions
 * Help content is diaplayed as embedded HTML
 * 
 * @author Romana Challans <romana@servalproject.com>
 */
public class PermissionsScreen extends Activity {

	WebView HelppermBrowser;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.permissions_screen);
		HelppermBrowser = (WebView) findViewById(R.id.permbrowser);
		HelppermBrowser.loadUrl("file:///android_asset/helppermissions.html");
		HelppermBrowser.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		HelppermBrowser.setBackgroundColor(Color.BLACK);


	}
}