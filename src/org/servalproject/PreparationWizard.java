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
import org.servalproject.system.LogOutput;
import org.servalproject.system.NetworkManager;
import org.servalproject.system.WifiControl;
import org.servalproject.system.WifiControl.Completion;
import org.servalproject.system.WifiControl.CompletionReason;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class PreparationWizard extends Activity implements LogOutput,
		OnClickListener {

	protected static final int DISMISS_PROGRESS_DIALOG = 0;
	protected static final int CREATE_PROGRESS_DIALOG = 1;
	private TextView status;
	private Button done;
	private ServalBatPhoneApplication app;

	private HandlerThread handlerThread;
	private Handler handler;
	private WifiControl control;
	private PowerManager.WakeLock wakeLock;
	private Shell rootShell;
	private static final String TAG = "Preparation";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.preparationlayout);
		status = (TextView) this.findViewById(R.id.status);
		done = (Button) this.findViewById(R.id.done);
		app = (ServalBatPhoneApplication) this.getApplication();
		done.setOnClickListener(this);
		// Are we recovering from a crash / reinstall?
		handlerThread = new HandlerThread("WifiControl");
		handlerThread.start();
		handler = new Handler(handlerThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				next();
				super.handleMessage(msg);
			}
		};

		this.control = NetworkManager.getNetworkManager(this).control;

		PowerManager powerManager = (PowerManager) this
				.getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(
				PowerManager.SCREEN_DIM_WAKE_LOCK, "PREPARATION_WAKE_LOCK");
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (state==-1){
			state++;
			triggerNext();
		}
	}

	private void triggerNext() {
		handler.removeMessages(0);
		handler.sendEmptyMessage(0);
	}

	private Completion completion = new Completion() {
		@Override
		public void onFinished(CompletionReason reason) {
			state++;
			triggerNext();
		}
	};

	int state = -1;

	private void next() {
		switch (state) {
		case 0:
			wakeLock.acquire();
			log("Ensure wifi radio is off");
			control.off(completion);
			break;
		case 1:
			log("Starting root shell");

			try {
				rootShell = Shell.startRootShell();
				app.coretask.rootTested(true);
			} catch (IOException e) {
				app.coretask.rootTested(false);
				failed(e);
				return;
			}
			state++;
		case 2:

			try {
				control.testAdhoc(rootShell, this);
				complete();
			} catch (IOException e) {
				failed(e);
			}
		}
	}

	private void complete() {
		if (rootShell != null) {
			try {
				rootShell.waitFor();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			} catch (InterruptedException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		wakeLock.release();
		state = -1;
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				done.setVisibility(View.VISIBLE);
			}
		});
	}

	private void failed(Throwable t) {
		String message = t.getMessage();
		if (message == null)
			message = "An unknown error occurred; "
					+ t.getClass().getSimpleName();
		log(message);
		Log.e(TAG, t.getMessage(), t);
		complete();
	}

	@Override
	public void log(final String message) {
		if (message == null)
			return;

		Log.v(TAG, message);
		if (app.isMainThread()) {
			status.setText(message);
		} else {
			this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					status.setText(message);
				}
			});
		}
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.done:
			finish();
			break;
		}
	}
}
