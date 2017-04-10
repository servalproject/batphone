package org.servalproject.account;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;

public class SyncService extends Service {
	private static SyncAdapter syncAdapter = null;

	private class SyncAdapter extends AbstractThreadedSyncAdapter {
		public SyncAdapter(Context context, boolean autoInitialize) {
			super(context, autoInitialize);
		}

		@Override
		public void onPerformSync(Account account, Bundle extras,
				String authority, ContentProviderClient provider,
				SyncResult syncResult) {
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		if ("android.content.SyncAdapter".equals(intent.getAction())) {
			if (syncAdapter == null)
				syncAdapter = new SyncAdapter(this, false);

			return syncAdapter.getSyncAdapterBinder();
		}
		return null;
	}

}
