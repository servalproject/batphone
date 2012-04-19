/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Originally from
 * http://developer.android.com/resources/samples/BusinessCard/src/com/example/android/businesscard/ContactAccessorSdk5.html
 */

package org.servalproject.messages;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;

public class ContactAccessor {

	/**
	 * Retrieves the contact information.
	 */
	public ContactInfo loadContact(ContentResolver contentResolver,
			Uri contactUri) {
		ContactInfo contactInfo = new ContactInfo();
		long contactId = -1;

		// Load the display name for the specified person
		Cursor cursor = contentResolver.query(contactUri,
				new String[] {
						Contacts._ID, Contacts.DISPLAY_NAME
				}, null, null, null);
		try {
			if (cursor.moveToFirst()) {
				contactId = cursor.getLong(0);
				contactInfo.setDisplayName(cursor.getString(1));
			}
		} finally {
			cursor.close();
		}

		// Load the phone number (if any).
		cursor = contentResolver.query(Phone.CONTENT_URI,
				new String[] {
					Phone.NUMBER
				},
				Phone.CONTACT_ID + "=" + contactId, null,
				Phone.IS_SUPER_PRIMARY + " DESC");
		try {
			if (cursor.moveToFirst()) {
				contactInfo.setPhoneNumber(cursor.getString(0));
			}
		} finally {
			cursor.close();
		}

		return contactInfo;
	}
}
