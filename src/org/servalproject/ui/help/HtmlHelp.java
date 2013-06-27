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
import android.util.Log;
import android.view.View;
import android.webkit.WebBackForwardList;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

/**
 * help screens - guide to using Serval
 *
 * @author Romana Challans <romana@servalproject.org>
 */

public class HtmlHelp extends Activity {
	WebView helpBrowser;
	TextView header;
	String startPage;
	int viewId = R.layout.htmlhelp;

	public class Client extends WebViewClient {

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Log.v("HtmlHelp", "Should override " + url + "?");
			if (url.startsWith("file:///android_asset/")
					|| url.equals("about:blank")) {
				// TODO test that the asset actually exists?
				return false;
			}
			// Load the uri using the full internet browser app.
			Uri uri = Uri.parse(url);
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);
			startActivity(intent);
			return true;
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			header.setText(view.getTitle());
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(viewId);

		header = (TextView) findViewById(R.id.help_header);
		helpBrowser = (WebView) findViewById(R.id.help_browser);
		helpBrowser.setWebViewClient(new Client());
		helpBrowser.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		helpBrowser.setBackgroundColor(Color.BLACK);
		helpBrowser.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Intent intent = this.getIntent();
		startPage = "file:///android_asset/" + intent.getStringExtra("page");
		helpBrowser.clearHistory();
		helpBrowser.loadUrl(startPage);
	}

	@Override
	public void onBackPressed() {
		WebBackForwardList history = helpBrowser.copyBackForwardList();
		int index = history.getCurrentIndex();
		for (int offset = -1; index + offset >= 0; offset--) {
			if (!history.getItemAtIndex(index + offset).getUrl()
					.equals("about:blank")) {
				Log.v("HtmlHelp", "Going back " + offset + " "
						+ history.getItemAtIndex(index + offset).getUrl());
				helpBrowser.goBackOrForward(offset);
				return;
			}
		}
		super.onBackPressed();
	}

}
