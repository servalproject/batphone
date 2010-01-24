/*
 * Copyright (C) 2010 Felix Bechstein
 * 
 * This file is part of ConnectorTest.
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

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * This is the default Activity launched from android market after install of
 * any plugin. This activity simply says: "i am a plugin for WebSMS".
 * 
 * @author flx
 */
public final class InfoActivity extends Activity {
	/** Tag for debug output. */
	private static final String TAG = "WebSMS.Info";

	/** Link to WebSMS in android market. */
	private static final Intent INTENT_MARKET_WEBSMS = new Intent(
			Intent.ACTION_VIEW, Uri.parse(// .
					"market://search?q=pname:de.ub0r.android.websms"));

	/** Link to Connectors in android market. */
	// FIXME: change uri
	private static final Intent INTENT_MARKET_CONNECTORS = new Intent(
			Intent.ACTION_VIEW, Uri.parse(// .
					"market://search?q=websms+connector"));

	/** Info text shown to user. */
	private static final String INFO_TEXT = "This is a WebSMS Connector."
			+ "\nThe only way to use it, is lauching it with WebSMS.";

	/** Button label: search websms. */
	private static final String BTN_MARKET_WEBSMS = "market: WebSMS";
	/** Button label: search connectors. */
	private static final String BTN_MARKET_CONNECTORS = // .
	"market: Connectors";

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onResume() {
		super.onResume();
		final Builder builder = new Builder(this);
		builder.setTitle(this.getTitle());
		builder.setMessage(INFO_TEXT);
		final int icon = this.getResources().getIdentifier("icon", "drawable",
				this.getPackageName());
		Log.d(TAG, "resID.icon=" + icon);
		if (icon > 0) {
			builder.setIcon(icon);
		}
		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog,
							final int which) {
						InfoActivity.this.finish();
					}
				});
		builder.setNeutralButton(BTN_MARKET_WEBSMS,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog,
							final int which) {
						try {
							InfoActivity.this
									.startActivity(INTENT_MARKET_WEBSMS);
						} catch (ActivityNotFoundException e) {
							Log.e(TAG, "no market", e);
						}
						InfoActivity.this.finish();
					}
				});
		builder.setNegativeButton(BTN_MARKET_CONNECTORS,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog,
							final int which) {
						try {
							InfoActivity.this
									.startActivity(INTENT_MARKET_CONNECTORS);
						} catch (ActivityNotFoundException e) {
							Log.e(TAG, "no market", e);
						}
						InfoActivity.this.finish();
					}
				});
		builder.setCancelable(true);
		builder.create().show();
	}
}
