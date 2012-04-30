package org.servalproject.rhizome;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;

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
	}

	@Override
	protected void onDestroy() {
		Log.i(Rhizome.TAG, "rhizome.onDestroy()");
		Log.i(Rhizome.TAG, "Rhizome shutting down.");
		super.onDestroy();
	}

	/**
	 * Form a list of all files in the Rhizome database.
	 */
	private void listFiles() {
		try {
			String[][] list = ServalD.rhizomeList(-1, -1); // all rows
			Log.i(Rhizome.TAG, "list=" + Arrays.deepToString(list));
			if (list.length < 1)
				throw new ServalDInterfaceError("missing header row");
			if (list[0].length < 1)
				throw new ServalDInterfaceError("missing columns");
			int i;
			for (i = 0; i != list[0].length && !list[0][i].equals("name"); ++i)
				;
			if (i >= list[0].length)
				throw new ServalDInterfaceError("missing 'name' column");
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
	}

	/**
	 * Set up the interface based on the list of files.
	 */
	private void setUpUI() {
		listFiles();
		setListAdapter(new ArrayAdapter<String>(this, R.layout.list_item, fList));
	}

}
