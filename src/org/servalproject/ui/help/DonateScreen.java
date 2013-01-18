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
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;

/**
 * help screens - donate screen
 * 
 * @author Romana Challans <romana@servalproject.org>
 */

/*
 * Help system now embedded HTML
 */

public class DonateScreen extends Activity {
	WebView HelpdonateBrowser;
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.donateview);
		HelpdonateBrowser = (WebView) findViewById(R.id.donatebrowser);
		HelpdonateBrowser.loadUrl("file:///android_asset/helpdonate.html");
		HelpdonateBrowser.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		HelpdonateBrowser.setBackgroundColor(Color.BLACK);

		// button launches paypal for serval

		Button donateButton = (Button) this.findViewById(R.id.donateButton);
		donateButton.setOnClickListener(new View.OnClickListener() {
			Uri uriUrl = Uri
					.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=92YQN3YXF7CRC");
			Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
			@Override
			public void onClick(View arg0) {
				DonateScreen.this.startActivity(launchBrowser);
			}
		});
	}

}