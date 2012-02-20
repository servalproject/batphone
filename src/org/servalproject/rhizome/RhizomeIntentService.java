/*
 * Copyright (c) 2012, The Serval Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the The Serval Project nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE SERVAL PROJECT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.servalproject.rhizome;

import java.io.File;
import java.io.IOException;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

/**
 * respond to incoming intents to add a file to the Rhizome repository
 *
 */
public class RhizomeIntentService extends IntentService {

	/*
	 * class level constants
	 */
	private final boolean V_LOG = true;
	private final String TAG = "RhizomeIntentService";

	public RhizomeIntentService() {
		super("RhizomeIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		// check to see if all of the parameters are available
		if (intent == null) {
			Log.w(TAG, "service called without an intent");
			return;
		}

		if (intent.getAction().equals("org.servalproject.rhizome.SHARE_FILE") != true) {
			Log.w(TAG, "service called with incorrect intent action");
			return;
		}

		Bundle mBundle = intent.getExtras();

		if (mBundle == null) {
			Log.e(TAG, "service called without a bundle of extras");
			return;
		}

		// get the file path
		String mFile = mBundle.getString("path");

		if (TextUtils.isEmpty(mFile) == true) {
			Log.e(TAG, "service called without the path extra");
			return;
		}

		// check to see if we can access the file
		if (isFileReadable(mFile) == false) {
			Log.e(TAG, "file specified in extra cannot be read: " + mFile);
			return;
		}

		// get the file name
		File mFilePath = new File(mFile);

		// get the version
		long mVersion = mBundle.getLong("version", -1);

		if (mVersion == -1) {
			Log.e(TAG, "service called without the version extra");
			return;
		}

		// get the author
		String mAuthor = mBundle.getString("author");

		if (TextUtils.isEmpty(mAuthor) == true) {
			Log.e(TAG, "service called whtout the author extra");
			return;
		}

		// add the file to the repository
		try {
			File mRhizomeFile = RhizomeFile.CopyFile(mFilePath, mFilePath.getName());
			RhizomeFile.GenerateManifestForFilename(mRhizomeFile, mAuthor,
					mVersion);
			RhizomeFile.GenerateMetaForFilename(mRhizomeFile.getName(),
					mVersion);

			if (V_LOG) {
				Log.v(TAG,
						"new file added to Rhizome repository '"
								+ mFilePath.getName() + "'");
			}

		} catch (IOException e) {
			Log.e("TAG",
					"unable to complete the add file to Rhizome repository task",
					e);
			return;
		}

	}

	/**
	 * tests to see if the given path is a file and is readable
	 *
	 * @param path
	 *            the full path to test
	 * @return true if the path is a file and is readable
	 */
	// private method to test a path
	private boolean isFileReadable(String path) {

		if (TextUtils.isEmpty(path) == true) {
			throw new IllegalArgumentException("the path parameter is required");
		}

		File mFile = new File(path);

		if (mFile.isFile() && mFile.canRead()) {
			return true;
		} else {
			return false;
		}
	}

}
