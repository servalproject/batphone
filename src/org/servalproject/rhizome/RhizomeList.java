package org.servalproject.rhizome;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDInterfaceError;

import android.app.ListActivity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.Cursor;

//import android.content.DialogInterface;
//import android.content.DialogInterface.OnClickListener;
//import android.content.Intent;
//import android.net.Uri;
//import android.os.Handler;
//import android.os.Message;
//import android.view.ContextMenu;
//import android.view.ContextMenu.ContextMenuInfo;
//import android.view.Menu;
//import android.view.MenuInflater;
//import android.view.MenuItem;
//import android.view.View;
//import android.webkit.MimeTypeMap;
//import android.widget.AdapterView;
//import android.widget.AdapterView.AdapterContextMenuInfo;
//import android.widget.AdapterView.OnItemClickListener;
//import android.widget.ListView;
import android.widget.ArrayAdapter;

/**
 * Rhizome list activity.  Presents the contents of the Rhizome store as a list of names.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeList extends ListActivity /*implements OnClickListener*/ {

	/** The Rhizome database. */
	private SQLiteDatabase db = null;

	/** The list of file names */
	private String[] fList = null;

	/**
	 * Display a toast message in a toast.
	 */
	private void goToast(String text) {
		ServalBatPhoneApplication app = (ServalBatPhoneApplication) this.getApplication();
		app.displayToastMessage(text);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(Rhizome.TAG, "rhizome.onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.rhizome);
	}

	@Override
	protected void onStart() {
		Log.i(Rhizome.TAG, "rhizome.onStart()");
		//openDatabase(); Not needed, because all database access is done through servald now
		super.onStart();
	}

	@Override
	protected void onResume() {
		Log.i(Rhizome.TAG, "rhizome.onResume()");
		setUpUI();
		super.onResume();
	}

	@Override
	protected void onStop() {
		Log.i(Rhizome.TAG, "rhizome.onStop()");
		super.onStop();
		closeDatabase();
	}

	@Override
	protected void onDestroy() {
		Log.i(Rhizome.TAG, "rhizome.onDestroy()");
		Log.i(Rhizome.TAG, "Rhizome shutting down.");
		super.onDestroy();
	}

	/**
	 * Open a connection to the Rhizome database.
	 */
	private void openDatabase() {
		// Check first if the storage is available
		String state = Environment.getExternalStorageState();
		if (!Environment.MEDIA_MOUNTED.equals(state)) {
			Log.e(Rhizome.TAG, "openDatabase(): external storage not mounted");
			goToast(getString(R.string.rhizomesdcard));
		} else if (db == null) {
			ServalBatPhoneApplication app = (ServalBatPhoneApplication) this.getApplication();
			try {
				db = SQLiteDatabase.openDatabase(
						app.coretask.DATA_FILE_PATH + "/rhizome.db",
						null,
						SQLiteDatabase.OPEN_READWRITE
					);
			}
			catch (SQLiteException e) {
				Log.e(Rhizome.TAG, "openDatabase(): " + e.getClass().getName() + ": " + e.getMessage());
				goToast(getString(R.string.rhizome_no_db));
			}
		} else {
			Log.e(Rhizome.TAG, "openDatabase(): database is already open");
		}
	}

	/**
	 * Close connection to the Rhizome database.
	 */
	private void closeDatabase() {
		if (db != null) {
			db.close();
			db = null;
		}
	}

	/**
	 * Form a list of all files in the Rhizome database.
	 */
	private void listFiles() {
		try {
			ServalD sdi = new ServalD();
			String[][] list = sdi.rhizomeList(-1, -1); // all rows
			int i;
			assert list.length >= 1; // assert there is a header row
			for (i = 0; i != list[0].length && list[0][i] != "name"; ++i)
				;
			assert i < list[0].length; // assert there is a "name" column
			int namecol = i;
			fList = new String[list.length - 1];
			for (i = 1; i < list.length; ++i) {
				fList[i - 1] = list[i][namecol];
			}
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
			fList = new String[0];
		}
		catch (ServalDInterfaceError e) {
			Log.e(Rhizome.TAG, "servald interface problem", e);
			fList = new String[0];
		}
		catch (AssertionError e) {
			Log.e(Rhizome.TAG, "ServalD.rhizomeList() returned something wierd", e);
			fList = new String[0];
		}
	}

	/**
	 * Set up the interface based on the list of files.
	 */
	private void setUpUI() {
		listFiles();
		setListAdapter(new ArrayAdapter<String>(this, R.layout.list_item, fList));
	}

}
