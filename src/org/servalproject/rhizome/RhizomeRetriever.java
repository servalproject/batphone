package org.servalproject.rhizome;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.jibble.simplewebserver.SimpleWebServer;
import org.servalproject.R;
import org.servalproject.rhizome.peers.BatmanPeerList;
import org.servalproject.rhizome.peers.BatmanServiceClient;

import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Rhizome Retriever main activity. Extends ListActivity to be able to list the
 * files in a table.
 *
 *
 * @author rbochet
 */
public class RhizomeRetriever extends ListActivity implements OnClickListener {

	public static RhizomeRetriever instance = null;

	/** Main handler reference */
	private static Handler mHandlerRef;

	/** The file picker dialog */
	private FolderPicker mFileDialog;

	/** TAG for debugging */
	public static final String TAG = "R2";

	/** The list of file names */
	private String[] fList = null;

	/** The list of logical files */
	private RhizomeFile[] rList = null;

	/** The thread that looks for updates */
	private PeerWatcher pWatcher;

	/** Listening port for the server */
	public static final int SERVER_PORT = 6666;

	/**
	 * Var used to ensure that the return of the activity comes from the
	 * manifest filling view
	 */
	private static final int FILL_MANIFEST = 0;

	/** Handler constant for an error */
	protected static final int MSG_ERR = 10;

	/** Handler constant for a file update */
	protected static final int MSG_UPD = 11;

	/**
	 * Create a new key pair. Delete the old one if still presents.
	 */
	private void createKeyPair() {
		Log.e(TAG, "TODO : createKeyPair()");
	}

	/**
	 * Display a toast message in a short popup
	 *
	 * @param text
	 *            The text displayed
	 */
	private void goToast(String text) {
		Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT)
				.show();
	}

	/**
	 * Import a file in the Rhizome directory. Generates automatically the
	 * associated meta file, amd ask the user the relevant informations for
	 * building a manifest file.
	 *
	 * @param fileName
	 *            The path of the file we need to import
	 */
	private void importFile(String fileName) {
		try {
			File file = new File(fileName);
			// Move the actual file
			RhizomeUtils.CopyFileToDir(file, RhizomeUtils.dirRhizome);
			// Ask data for creating the Manifest
			Intent myIntent = new Intent(this.getBaseContext(),
					ManifestEditorActivity.class);

			myIntent.putExtra("fileName", file.getName());
			startActivityForResult(myIntent, FILL_MANIFEST);
			// --> the result will be back in onAcRes

		} catch (IOException e) {
			Log.e(TAG, "Importation failed.");
			goToast("Importation failed.");
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		Log.i(TAG,
				"Rhizome's shutting down. Cleaning the tmp directory & stopping updates.");
		RhizomeUtils.deleteDirectory(RhizomeUtils.dirRhizomeTemp);
		pWatcher.stopUpdate();
		super.onDestroy();
	}

	/**
	 * List files of the directory serval on the SD Card
	 */
	private void listFiles() {
		Log.v(TAG, RhizomeUtils.dirRhizome.getAbsolutePath());

		// If the path exists, list all the non-hidden files (no dir)
		if (RhizomeUtils.dirRhizome.exists()) {
			FilenameFilter filter = new FilenameFilter() {
				@Override
				public boolean accept(File dir, String filename) {
					File sel = new File(dir, filename);
					return (sel.isFile() && !sel.isHidden());
				}
			};

			// List of the relative paths
			fList = RhizomeUtils.dirRhizome.list(filter);
			// List of the RhizomeFile
			rList = new RhizomeFile[fList.length];

			for (int i = 0; i < rList.length; i++) {
				rList[i] = new RhizomeFile(RhizomeUtils.dirRhizome, fList[i]);
				Log.v(TAG, rList[i].toString());
			}

		} else { // The pass does not exist
			Log.e(TAG, "No serval-rhizome path found on the SD card.");
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == FILL_MANIFEST) { // Comes back from the manifest
											// filling activity
			if (resultCode == RESULT_OK) {
				// Get the parameters
				String fileName = data.getExtras().getString("fileName");
				String author = data.getExtras().getString("author");
				long version = Long.parseLong(data.getExtras().getString(
						"version"));

				// Creates the manifest
				RhizomeFile.GenerateManifestForFilename(fileName, author,
						version);
				// Create silently the meta data
				RhizomeFile.GenerateMetaForFilename(fileName, version);

				// Reset the UI
				setUpUI();
				// Alright
				goToast("Success: " + fileName + " imported.");

			}
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (dialog == mFileDialog) { // security, not really needed
			String path = mFileDialog.getPath();
			importFile(path);
		}

	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		Log.v(TAG, "Context menu pressed: " + info.id);
		switch (item.getItemId()) {

		// Delete the file
		case R.id.rrcm_delete:
			try {
				rList[(int) info.id].delete();
				// Need also to reset the UI
				setUpUI();
				// Alright
				goToast("Deletion successed.");
			} catch (IOException e1) {
				Log.e(TAG, "Deletion failed.");
				goToast("Deletion failed.");
			}
			return true;
			// Export the file in a known folder
		case R.id.rrcm_export:
			try {
				rList[(int) info.id].export();
				goToast("Export successed.");
			} catch (IOException e) { // The copy failed. Warn the user
				Log.e(TAG, "Export failed.");
				goToast("Export failed.");
			}
			return true;
		case R.id.rrcm_mark:
			try {
				rList[(int) info.id].markForExpiration();
				goToast("Marked for expiration.");
			} catch (IOException e) {
				Log.e(TAG, "Impossible to mark for expiration");
				goToast("Impossible to mark for expiration.");
			}
			return true;
		case R.id.rrcm_vcert:
			try {
				// Create the empty intent
				Intent intent = new Intent(this.getBaseContext(),
						ManifestViewActivity.class);
				// Populate it
				intent = rList[(int) info.id].populateDisplayIntent(intent);
				// Send it
				startActivity(intent);
			} catch (IOException e) {
				Log.e(TAG, "Impossible to read the manifest.");
				goToast("Error while reading the manifest.");
			}
			return true;
		case R.id.rrcm_certify:
			//XXX Add code here to sign a manifest
			 if (rList[(int) info.id].certifyManifest() == true) {
				Log.d(TAG, "Certified manifest");
				goToast("You have certified/published this file.");

			}
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Setup the main handler reference
		RhizomeRetriever.mHandlerRef = this.mHandler;

		// Provide a handle to the application
		instance = this;

		// setContentView(R.layout.rhizome_retriever);

		// Creates the path folders if they dont exist
		setUpDirectories();

		// Setup the UI
		setUpUI();

		// Setup and start the peer list stuff
		BatmanPeerList peerList = new BatmanPeerList();
		BatmanServiceClient bsc = new BatmanServiceClient(getApplicationContext(), peerList);
		new Thread(bsc).start();

		// Launch the updater thread with the peer list object
		pWatcher = new PeerWatcher(peerList);
		pWatcher.start();

		// Start the web server
		try {
			// Get the wifi address
			WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			WifiInfo wifiInfo = wifiManager.getConnectionInfo();
			int ipAddress = wifiInfo.getIpAddress();
			String stringIP = Formatter.formatIpAddress(ipAddress);

			SimpleWebServer server = new SimpleWebServer(
					RhizomeUtils.dirRhizome, stringIP, 6666);
		} catch (IOException e) {
			goToast("Error starting webserver. Only listening.");
			e.printStackTrace();
		}

	}

	/**
	 * Set up the directories dirRhizome and dirExport if they dont exist yet.
	 */
	private void setUpDirectories() {
		// Check first if the storage is available
		String state = Environment.getExternalStorageState();

		if (!Environment.MEDIA_MOUNTED.equals(state)) {
			Log.e(TAG, "Cannot read/write on the FS. Exiting.");
			goToast("Cannot read/write on the FS. Exiting.");
			// System.exit(1);
		}

		if (!RhizomeUtils.dirRhizome.isDirectory()) {
			RhizomeUtils.dirRhizome.mkdirs();
			Log.i(TAG, "Rhizome folder (" + RhizomeUtils.dirRhizome
					+ ") has been created");
		}
		if (!RhizomeUtils.dirExport.isDirectory()) {
			RhizomeUtils.dirExport.mkdirs();
			Log.i(TAG, "Rhizome export folder (" + RhizomeUtils.dirExport
					+ ") has been created");
		}
		if (!RhizomeUtils.dirRhizomeTemp.isDirectory()) {
			RhizomeUtils.dirRhizomeTemp.mkdirs();
			Log.i(TAG, "Rhizome temp folder (" + RhizomeUtils.dirRhizomeTemp
					+ ") has been created");
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.rr_context_menu, menu);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.rr_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.m_import:
			mFileDialog = new FolderPicker(this, this, android.R.style.Theme,
					true);
			mFileDialog.show();
			return true;
		case R.id.m_new_keys:
			createKeyPair();
			return true;
		case R.id.m_reinit_list:
			reinitExclusionList();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Reinitialize the exclusion list by deleting all the meta files that dont
	 * belong to an existing file (ie that dont have a companion manifest file).
	 */
	private void reinitExclusionList() {
		String[] metaList;

		// We keep just the meta files
		FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				File sel = new File(dir, filename);
				return (sel.isFile() && filename.endsWith("meta"));
			}
		};

		// List of the relative paths
		metaList = RhizomeUtils.dirRhizome.list(filter);
		for (String metaPath : metaList) {
			// Look for the manifest
			String manifestPath = metaPath.substring(0, metaPath.length()
					- "meta".length())
					+ "manifest";

			// If the manifest doesn't exist, delete also the meta
			if (!new File(RhizomeUtils.dirRhizome, manifestPath).exists()) {
				Log.v(TAG, metaPath);
				new File(RhizomeUtils.dirRhizome, metaPath).delete();
			}
		}
		goToast("Exclusion list reinitialized.");

	}

	/**
	 * Process a short click received from the list view
	 *
	 * @param position
	 *            The pos of the view in the adapter
	 * @param id
	 *            The id in the list view.
	 */
	private void processClick(int position, long id) {
		try {
			File fileToOpen = new File(rList[(int) id].getFile()
					.getAbsolutePath());
			Uri uri = Uri.fromFile(fileToOpen);
			String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri
					.toString());
			String mimeType = MimeTypeMap.getSingleton()
					.getMimeTypeFromExtension(fileExtension);
			Intent intent = new Intent();
			intent.setAction(android.content.Intent.ACTION_VIEW);
			intent.setDataAndType(uri, mimeType);
			startActivity(intent);
		} catch (Exception e) {
			Log.e(TAG, "Not possible to resolve this intent.");
			goToast("No application offered to open the file.");
		}
	}

	/**
	 * Set up the interface based on the list of files. This function is
	 * synchronized because it can be accessed from this class and from the
	 * updater Thread.
	 */
	private void setUpUI() {
		listFiles();

		setListAdapter(new ArrayAdapter<String>(this, R.layout.list_item, fList));

		ListView lv = getListView();
		lv.setTextFilterEnabled(true);

		// The click behavior
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Log.v(TAG, rList[(int) id].getFile().toString());

				// Process the click
				processClick(position, id);

			}
		});

		// Register the context menu
		registerForContextMenu(getListView());

	}

	/**
	 * Get the main class' handler reference.
	 *
	 * @return the reference
	 */
	protected static Handler getHandlerInstance() {
		return RhizomeRetriever.mHandlerRef;
	}

	/**
	 * Handle the message for the updating the view and warning about error and
	 * updates.
	 */
	public final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_UPD: // It's an update
				setUpUI();
				goToast("Update: " + (String) msg.obj);
				break;
			case MSG_ERR: // It's an error
				goToast("Error: " + (String) msg.obj);
				break;
			default: // should never happen
				break;
			}
		}
	};

}
