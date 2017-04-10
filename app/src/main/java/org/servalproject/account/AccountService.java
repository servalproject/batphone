package org.servalproject.account;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import org.servalproject.Main;
import org.servalproject.servaldna.AbstractId;
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.wizard.Wizard;

import java.util.ArrayList;

public class AccountService extends Service {
	private static final String TAG="AccountService";
	private static AccountAuthenticator authenticator=null;
	public static final String ACTION_ADD = "org.servalproject.account.add";
	public static final String TYPE = "org.servalproject.account";

	public static final String SID_FIELD_MIMETYPE = "vnd.android.cursor.item/org.servalproject.unsecuredSid";

	public static long getContactId(ContentResolver resolver,
			SubscriberId sid) {
		Cursor cursor = resolver.query(ContactsContract.Data.CONTENT_URI,
				new String[] {
					ContactsContract.Data.CONTACT_ID
				},
				"UPPER(" + ContactsContract.Data.DATA1 + ") = ? AND "
						+ ContactsContract.Data.MIMETYPE + " = ?",
				new String[] {
						sid.toHex(), SID_FIELD_MIMETYPE
				}, null);
		if (cursor == null)
			return -1;
		try {
			if (!cursor.moveToNext()) {
				return -1;
			}

			return cursor.getLong(0);
		} finally {
			cursor.close();
		}
	}

	public static long getContactId(ContentResolver resolver,
			String did) {
		Cursor cursor = resolver
				.query(ContactsContract.Data.CONTENT_URI,
						new String[] {
							ContactsContract.Data.CONTACT_ID
						},
						ContactsContract.CommonDataKinds.Phone.NUMBER
								+ " = ? AND " + ContactsContract.Data.MIMETYPE
								+ " = ?",
						new String[] {
								did,
								ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
						},
						null);

		try {
			if (!cursor.moveToNext())
				return -1;
			return cursor.getLong(0);
		} finally {
			cursor.close();
		}
	}

	public static String getContactName(ContentResolver resolver,
			long contactId) {
		Cursor cursor = resolver.query(ContactsContract.Contacts.CONTENT_URI,
				new String[] { ContactsContract.Contacts.DISPLAY_NAME },
				"_ID = ?", new String[] { Long.toString(contactId) }, null);

		try {
			if (!cursor.moveToNext()) {
				Log.w("BatPhone", "Could not find contact name for "
						+ contactId);
				return null;
			}

			return cursor.getString(0);
		} finally {
			cursor.close();
		}
	}

	public static SubscriberId getContactSid(ContentResolver resolver, Uri uri) throws AbstractId.InvalidHexException {
		if (uri==null)
			return null;
		// TODO patern match Uri?
		long contactId=-1;
		Cursor cursor = resolver.query(
				uri,
				new String[]{
						ContactsContract.Data.RAW_CONTACT_ID
				},
				null,
				null,
				null);
		try {
			if (cursor.moveToNext())
				contactId = cursor.getLong(0);
		} finally {
			cursor.close();
		}
		if (contactId==-1)
			return null;
		return getContactSid(resolver, contactId);
	}
	
	public static SubscriberId getContactSid(ContentResolver resolver,
			long contactId) {
		Cursor cursor = resolver.query(ContactsContract.Data.CONTENT_URI,
				new String[] { ContactsContract.Data.DATA1 },
				ContactsContract.Data.CONTACT_ID + " = ? AND "
						+ ContactsContract.Data.MIMETYPE + " = ?",
				new String[] { Long.toString(contactId), SID_FIELD_MIMETYPE },
				null);
		try {
			if (cursor.moveToNext())
				return new SubscriberId(cursor.getString(0));
		}
		catch (SubscriberId.InvalidHexException e) {
			Log.e("BatPhone", "Invalid SID", e);
		}
		finally {
			cursor.close();
		}
		return null;
	}

	public static Account getAccount(Context context) {
		AccountManager manager = AccountManager.get(context);
		Account[] accounts = manager.getAccountsByType(AccountService.TYPE);
		if (accounts == null || accounts.length == 0)
			return null;
		return accounts[0];
	}

	private static void insertSettings(ContentResolver resolver, Account account) throws RemoteException {
		ContentProviderClient client = resolver.acquireContentProviderClient(ContactsContract.AUTHORITY_URI);
		ContentValues values = new ContentValues();
		values.put(ContactsContract.Settings.ACCOUNT_NAME, account.name);
		values.put(ContactsContract.Settings.ACCOUNT_TYPE, account.type);
		values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, true);
		values.put(ContactsContract.Settings.ANY_UNSYNCED, false);
		client.insert(
				ContactsContract.Settings.CONTENT_URI.buildUpon()
						.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(),
				values);
	}

	public static Account createAccount(Context context, String name) {
		Account account = new Account(name, AccountService.TYPE);
		AccountManager am = AccountManager.get(context);

		if (!am.addAccountExplicitly(account, "", null))
			throw new IllegalStateException(
					"Failed to create account");

		try {
			insertSettings(context.getContentResolver(), account);
		} catch (RemoteException e) {
			Log.e(TAG, e.getMessage(), e);
		}

		return account;
	}

	public static long addContact(Context context, String name,
			SubscriberId sid, String did) throws RemoteException,
			OperationApplicationException {
		ContentResolver resolver = context.getContentResolver();
		Account account = getAccount(context);
		if (account == null)
			throw new IllegalStateException();
		return addContact(resolver, account, name, sid, did);
	}

	public static long addContact(ContentResolver resolver, Account account,
			String name, SubscriberId sid, String did) throws RemoteException,
			OperationApplicationException {
		Log.i("BatPhone", "Adding contact: " + name);
		ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

		// Create our RawContact
		ContentProviderOperation.Builder builder = ContentProviderOperation
				.newInsert(RawContacts.CONTENT_URI);
		builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
		builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
		builder.withValue(RawContacts.VERSION, 1);
		operationList.add(builder.build());

		// Create a Data record of common type 'StructuredName' for our
		// RawContact
		builder = ContentProviderOperation
				.newInsert(ContactsContract.Data.CONTENT_URI);
		builder.withValueBackReference(
				ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID,
				0);
		builder.withValue(
				ContactsContract.Data.MIMETYPE,
				ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
		if (name != null && !name.equals(""))
			builder.withValue(
					ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
					name);
		operationList.add(builder.build());

		// Create a Data record for the subscriber id
		builder = ContentProviderOperation
				.newInsert(ContactsContract.Data.CONTENT_URI);
		builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
		builder.withValue(ContactsContract.Data.MIMETYPE, SID_FIELD_MIMETYPE);
		builder.withValue(ContactsContract.Data.DATA1, sid.toHex());
		builder.withValue(ContactsContract.Data.DATA2, "Call Mesh");
		builder.withValue(ContactsContract.Data.DATA3, sid.abbreviation());
		operationList.add(builder.build());

		// Create a Data record for their phone number
		builder = ContentProviderOperation
				.newInsert(ContactsContract.Data.CONTENT_URI);
		builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
		builder.withValue(ContactsContract.Data.MIMETYPE,
				ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
		builder.withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, did);
		builder.withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
				ContactsContract.CommonDataKinds.Phone.TYPE_MAIN);
		operationList.add(builder.build());

		resolver.applyBatch(
				ContactsContract.AUTHORITY,
				operationList);

		return getContactId(resolver, sid);
	}

	// auto-magically update anything related to android contacts, where we have changed how we store things
	public static void upgradeContacts(Context context){
		Account account = getAccount(context);
		if (account==null)
			return;

		try{
			insertSettings(context.getContentResolver(), account);
		}catch(Exception e){
			// ignore
		}
	}

	private class AccountAuthenticator extends AbstractAccountAuthenticator {
		Context context;

		public AccountAuthenticator(Context context) {
			super(context);
			this.context = context;
		}

		@Override
		public Bundle addAccount(AccountAuthenticatorResponse response,
				String accountType, String authTokenType,
				String[] requiredFeatures, Bundle options)
				throws NetworkErrorException {

			Intent intent = new Intent(context, Wizard.class);
			intent.setAction(ACTION_ADD);
			intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
					response);
			Bundle reply = new Bundle();
			reply.putParcelable(AccountManager.KEY_INTENT, intent);
			return reply;
		}

		@Override
		public Bundle confirmCredentials(AccountAuthenticatorResponse response,
				Account account, Bundle options) throws NetworkErrorException {
			return null;
		}

		@Override
		public Bundle editProperties(AccountAuthenticatorResponse response,
				String accountType) {
			Intent intent = new Intent(context, Main.class);
			Bundle reply = new Bundle();
			reply.putParcelable(AccountManager.KEY_INTENT, intent);
			return reply;
		}

		@Override
		public Bundle getAuthToken(AccountAuthenticatorResponse response,
				Account account, String authTokenType, Bundle options)
				throws NetworkErrorException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getAuthTokenLabel(String authTokenType) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Bundle hasFeatures(AccountAuthenticatorResponse response,
				Account account, String[] features)
				throws NetworkErrorException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Bundle updateCredentials(AccountAuthenticatorResponse response,
				Account account, String authTokenType, Bundle options)
				throws NetworkErrorException {
			// TODO Auto-generated method stub
			return null;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		if (intent.getAction().equals(AccountManager.ACTION_AUTHENTICATOR_INTENT)){
			if (authenticator==null)
				authenticator = new AccountAuthenticator(this);
			return authenticator.getIBinder();
		}
		return null;
	}

}
