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

/**
 * Wizard - initial settings, reset phone.
 * @author Paul Gardner-Stephen <paul@servalproject.org>
 * @author Jeremy Lakeman <jeremy@servalproject.org>
 * @author Romana Challans <romana@servalproject.org>
 */
package org.servalproject;

import java.io.IOException;

import org.servalproject.shell.Shell;
import org.servalproject.system.Chipset;
import org.servalproject.system.ChipsetDetection;
import org.servalproject.system.WiFiRadio;
import org.servalproject.system.WifiMode;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.widget.TextView;

public class PreparationWizard extends Activity {

	protected static final int DISMISS_PROGRESS_DIALOG = 0;
	protected static final int CREATE_PROGRESS_DIALOG = 1;
	private TextView status;
	private ServalBatPhoneApplication app;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.preparationlayout);
		status = (TextView) this.findViewById(R.id.status);
		app = (ServalBatPhoneApplication) this.getApplication();
	}

	@Override
	protected void onResume() {
		super.onResume();

		new PreparationTask().execute();
	}

	private class PreparationTask extends AsyncTask<Void, String, String> {
		private PowerManager.WakeLock wakeLock = null;
		private Shell rootShell;

		private PreparationTask() {
			PowerManager powerManager = (PowerManager) ServalBatPhoneApplication.context
					.getSystemService(Context.POWER_SERVICE);
			wakeLock = powerManager.newWakeLock(
					PowerManager.SCREEN_DIM_WAKE_LOCK, "PREPARATION_WAKE_LOCK");
		}

		@Override
		protected String doInBackground(Void... arg) {
			try {
				wakeLock.acquire();
				ChipsetDetection detection = ChipsetDetection.getDetection();

				// clear any previously detected chipset
				detection.setChipset(null);
				Editor ed = app.settings.edit();
				ed.putString("detectedChipset", "Unknown");
				ed.commit();

				this.publishProgress("Starting root shell");

				try {
					rootShell = Shell.startRootShell();
					app.coretask.rootTested(true);
				} catch (IOException e) {
					app.coretask.rootTested(false);
					throw e;
				}

				if (app.wifiRadio == null) {
					// not sure if we need this anymore....

					// this constructor is a bit too convoluted,
					// mainly so we can re-use the single root
					// shell for the entire preparation process
					app.wifiRadio = WiFiRadio
							.getWiFiRadio(
									app,
									WifiMode.getWiFiMode(rootShell));
				}

				WifiMode initialMode = app.wifiRadio.getCurrentMode();

				try {
					this.publishProgress("Scanning for known android hardware");
					detection.identifyChipset();

					if (detection.detected_chipsets.size() == 0) {
						this.publishProgress("Hardware is unknown, scanning for wifi modules");

						detection.inventSupport();
						detection.detect(true);
					}

					for (Chipset c : detection.detected_chipsets) {
						this.publishProgress("Testing - " + c.chipset);

						detection.setChipset(c);
						if (!c.supportedModes.contains(WifiMode.Adhoc))
							continue;

						try {
							app.wifiRadio.testAdhoc(rootShell);
						} catch (IOException e) {
							Log.e("BatPhone", e.getMessage(), e);
							continue;
						} catch (InterruptedException e) {
							Log.e("BatPhone", e.getMessage(), e);
							continue;
						}

						ed = app.settings.edit();
						ed.putString("detectedChipset", c.chipset);
						ed.commit();
						break;
					}

				} finally {
					try {
						app.wifiRadio.setWiFiMode(initialMode);
					} catch (IOException e) {
						Log.e("BatPhone", e.getMessage(), e);
					}

					if (rootShell != null) {
						this.publishProgress("Closing root shell");
						rootShell.waitFor();
						rootShell = null;
					}
				}

				return "Finished";
			} catch (Exception e) {
				Log.e("BatPhone", e.getMessage(), e);
				return e.getMessage();
			} finally {
				wakeLock.release();
			}
		}

		@Override
		protected void onProgressUpdate(String... arg) {
			status.setText(arg[0]);
		}

		@Override
		protected void onPostExecute(String arg) {
			status.setText(arg);
			finish();
		}
	}
}
