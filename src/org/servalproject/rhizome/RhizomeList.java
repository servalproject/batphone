package org.servalproject.rhizome;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeDetail;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalD.RhizomeListResult;
import org.servalproject.servald.ServalDFailureException;
import org.servalproject.servald.ServalDInterfaceError;

import android.os.Bundle;
import android.util.Log;
import android.app.ListActivity;
import android.app.Dialog;
import android.view.View;
import android.widget.ListView;
import android.widget.ArrayAdapter;

/**
 * Rhizome list activity.  Presents the contents of the Rhizome store as a list of names.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeList extends ListActivity {

	static final int DIALOG_DETAILS_ID = 0;

	/** The doalog showing a file detail */
	RhizomeDetail mDetailDialog = null;

	/** The list of file names */
	private String[] fNames = null;

	/** The list of data bundles */
	private Bundle[] fBundles = null;

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
		setContentView(R.layout.rhizome_list);
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
			RhizomeListResult result = ServalD.rhizomeList(-1, -1); // all rows
			Log.i(Rhizome.TAG, "list=" + Arrays.deepToString(result.list));
			if (result.list.length < 1)
				throw new ServalDInterfaceError("missing header row", result);
			if (result.list[0].length < 1)
				throw new ServalDInterfaceError("missing columns", result);
			int i;
			int namecol = -1;
			int manifestidcol = -1;
			int datecol = -1;
			int lengthcol = -1;
			int versioncol = -1;
			for (i = 0; i != result.list[0].length; ++i) {
				if (result.list[0][i].equals("name"))
					namecol = i;
				else if (result.list[0][i].equals("manifestid"))
					manifestidcol = i;
				else if (result.list[0][i].equals("date"))
					datecol = i;
				else if (result.list[0][i].equals("length"))
					lengthcol = i;
				else if (result.list[0][i].equals("version"))
					versioncol = i;
			}
			if (namecol == -1)
				throw new ServalDInterfaceError("missing 'name' column", result);
			if (manifestidcol == -1)
				throw new ServalDInterfaceError("missing 'manifestid' column", result);
			if (datecol == -1)
				throw new ServalDInterfaceError("missing 'date' column", result);
			if (lengthcol == -1)
				throw new ServalDInterfaceError("missing 'length' column", result);
			if (versioncol == -1)
				throw new ServalDInterfaceError("missing 'version' column", result);
			fNames = new String[result.list.length - 1];
			fBundles = new Bundle[result.list.length - 1];
			for (i = 1; i < result.list.length; ++i) {
				fNames[i - 1] = result.list[i][namecol];
				Bundle b = new Bundle();
				b.putString("name", result.list[i][namecol]);
				b.putString("manifestid", result.list[i][manifestidcol]);
				b.putLong("date", Long.parseLong(result.list[i][datecol]));
				b.putLong("length", Long.parseLong(result.list[i][lengthcol]));
				b.putLong("version", Long.parseLong(result.list[i][versioncol]));
				fBundles[i - 1] = b;
			}
		}
		catch (ServalDFailureException e) {
			Log.e(Rhizome.TAG, "servald failed", e);
			fNames = new String[0];
			fBundles = null;
		}
		catch (ServalDInterfaceError e) {
			Log.e(Rhizome.TAG, "servald interface problem", e);
			fNames = new String[0];
			fBundles = null;
		}
		catch (IllegalArgumentException e) {
			Log.e(Rhizome.TAG, "servald interface problem", e);
			fNames = new String[0];
			fBundles = null;
		}
	}

	/**
	 * Set up the interface based on the list of files.
	 */
	private void setUpUI() {
		listFiles();
		setListAdapter(new ArrayAdapter<String>(this, R.layout.rhizome_list_item, fNames));
	}

	@Override
	protected void onListItemClick(ListView listview, View view, int position, long id) {
		showDialog(DIALOG_DETAILS_ID, fBundles[position]);
	}

	@Override
	protected Dialog onCreateDialog(int id, Bundle bundle) {
		switch (id) {
		case DIALOG_DETAILS_ID:
			mDetailDialog = new RhizomeDetail(this);
			return mDetailDialog;
		}
		return super.onCreateDialog(id, bundle);
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
		switch (id) {
		case DIALOG_DETAILS_ID:
			((RhizomeDetail) dialog).setData(bundle);
			break;
		}
		super.onPrepareDialog(id, dialog, bundle);
	}

}
