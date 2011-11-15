/**
 *
 */
package org.servalproject.rhizome;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * @author rbochet
 *
 */
public class StuffDownloader {

	/** The repository where we should get the manifest */
	private String repository;

	/** TAG for debugging */
	public static final String TAG = "R2";

	/**
	 * Constructor of the class.
	 *
	 * @param repository
	 *            The root of server where the manifests are stored.
	 */
	public StuffDownloader(String repository) {
		this.repository = repository;

		Log.v(TAG, "Start downloading from " + this.repository);

		List<String> manifests = fetchManifests();

		List<String> dlManifests = chooseManifests(manifests);

		for (String manifest : dlManifests) {
			dlFile(manifest);
		}

	}

	/**
	 * Download the manifest, grab the file name and download it. If the hash of
	 * the file downloaded is different from the hash of the manifest, discard
	 * both the file and the manifest.
	 *
	 * @param manifest
	 *            The manifest address
	 */
	private void dlFile(String manifest) {
		try {
			// Download the manifest in the Rhizome directory
			Log.v(TAG, "Downloading " + manifest);
			String[] tokenizedUrl = manifest.split("/");
			String mfName = tokenizedUrl[tokenizedUrl.length - 1];
			downloadFile(new URL(manifest), RhizomeUtils.dirRhizomeTemp + "/"
					+ mfName);

			// Check the key TODO
			Log.v(TAG, "Loading properties from " + mfName);
			Properties pManifest = new Properties();
			pManifest.load(new FileInputStream(RhizomeUtils.dirRhizomeTemp
					+ "/" + mfName));

			// If alright, compute the actual file URL and name
			tokenizedUrl[tokenizedUrl.length - 1] = pManifest
					.getProperty("name");
			StringBuilder fileNameB = new StringBuilder(tokenizedUrl[0]);
			for (int i = 1; i < tokenizedUrl.length; i++) {
				fileNameB.append("/" + tokenizedUrl[i]);
			}
			String file = fileNameB.toString();

			// Download it
			Log.v(TAG, "Downloading " + file);
			downloadFile(new URL(file), RhizomeUtils.dirRhizomeTemp + "/"
					+ pManifest.getProperty("name"));

			// Check the hash
			String hash = RhizomeUtils.ToHexString(RhizomeUtils
					.DigestFile(new File(RhizomeUtils.dirRhizomeTemp + "/"
							+ pManifest.getProperty("name"))));

			if (!hash.equals(pManifest.get("hash"))) {
				// Hell, the hash's wrong! Delete the logical file
				Log.w(TAG, "Wrong hash detected for manifest " + manifest);
			} else { // If it's all right, copy it to the real repo
				RhizomeUtils.CopyFileToDir(
						new File(RhizomeUtils.dirRhizomeTemp, pManifest
								.getProperty("name")), RhizomeUtils.dirRhizome);

				RhizomeUtils.CopyFileToDir(new File(RhizomeUtils.dirRhizomeTemp
						+ "/" + mfName), RhizomeUtils.dirRhizome);

				// Generate the meta file for the newly received file
				RhizomeFile.GenerateMetaForFilename(
						pManifest.getProperty("name"),
 Long.parseLong((String) pManifest
						.get("version")));

				// Notify the main view that a file has been updated
				Handler handler = RhizomeRetriever.getHandlerInstance();
				Message updateMessage = handler.obtainMessage(
						RhizomeRetriever.MSG_UPD,
						pManifest.getProperty("name") + " (v. "
								+ pManifest.getProperty("version") + ")");
				handler.sendMessage(updateMessage);

				if (file.endsWith(".rpml"))
				{
					// File is a public message log - so we should tell batphone to
					// look for messages in the file.
					MessageLogExaminer.examineLog(file);
				}
				if (file.endsWith(".map")) {
					// File is a map.
					// copy into place and notify user to restart mapping

				}
			}
			// Delete the files in the temp dir
			new RhizomeFile(RhizomeUtils.dirRhizomeTemp,
					pManifest.getProperty("name")).delete();

		} catch (MalformedURLException e) {
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void copyFile(File src, File dst) throws IOException {
		InputStream in = new FileInputStream(src);
		OutputStream out = new FileOutputStream(dst);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}

	/**
	 * Choose the interesting manifests among a list of the manifest that can be
	 * downloaded from an host. If we dont have Manifest nor Meta for this file,
	 * we'll download it. If we have just the meta, it means the user delete the
	 * file ; so we download it just if it is a new version.
	 *
	 * @param manifests
	 *            The list of all the manifests URL
	 * @return A list of the selected manifests
	 */
	private List<String> chooseManifests(List<String> manifests) {
		List<String> ret = new ArrayList<String>();

		// Iterate
		for (String manifest : manifests) {
			// "Unwrapp" the names
			String mfName = manifest.split("/")[manifest.split("/").length - 1];
			String metaName = mfName.replace("manifest", "meta");
			// Check if it exists on the local repo
			if (!(new File(RhizomeUtils.dirRhizome, metaName).exists())) {
				// We add it to the DL list
				ret.add(manifest);
			} else { // The manifest already exists ; but is it a new version ?
				try {
					// DL the new manifest in a temp directory
					Log.v(TAG, "Downloading " + manifest);
					downloadFile(new URL(manifest), RhizomeUtils.dirRhizomeTemp
							+ "/" + mfName);

					// Compare the new manifest to the old meta ; if new.version
					// > old.version,
					// DL
					Properties newManifest = new Properties();
					newManifest.load(new FileInputStream(
							RhizomeUtils.dirRhizomeTemp + "/" + mfName));
					float nmversion = Float.parseFloat((String) newManifest
							.get("version"));

					Properties oldMeta = new Properties();
					oldMeta.load(new FileInputStream(RhizomeUtils.dirRhizome
							+ "/" + metaName));
					float omversion = Float.parseFloat((String) oldMeta
							.get("version"));

					if (nmversion > omversion) {
						ret.add(manifest);
					}
				} catch (IOException e) {
					Log.e(TAG, "Error evaluating if the manifest " + manifest
							+ " version.");
					e.printStackTrace();
				}

			}
		}
		return ret;
	}

	/**
	 * Fetch the list of the manifests on the server.
	 *
	 * @return The list of manifests.
	 */
	private List<String> fetchManifests() {
		List<String> manifests = new ArrayList<String>();
		try {
			URL repoURL = new URL(repository);
			BufferedReader in = new BufferedReader(new InputStreamReader(
					repoURL.openStream()));

			String repositoryPrefix = repository;
			if (repository.lastIndexOf(":")!=-1)
				repositoryPrefix = repository.substring(0,repository.lastIndexOf(":"));

			// Read each line
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				if (inputLine.trim().startsWith("http")) {
					// Feed them to the manifest
					// But replace any references to http://0.0.0.0 with the real IP of the node
					if (inputLine.startsWith("http://0.0.0.0:"))
					{
						inputLine=repositoryPrefix+inputLine.substring(14);
					}
					manifests.add(inputLine);
				}
			}
			// Close stream
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return manifests;
	}

	/**
	 * Download a file using HTTP.
	 *
	 * @param file
	 *            The URL of the file
	 * @param path
	 *            The path were the file will be saved on the local FS.
	 * @throws IOException
	 *             If something goes wrong
	 */
	private void downloadFile(URL url, String path) throws IOException {
		URLConnection uc = url.openConnection();
		int contentLength = uc.getContentLength();
		InputStream raw = uc.getInputStream();
		InputStream in = new BufferedInputStream(raw);
		byte[] data = new byte[contentLength];
		int bytesRead = 0;
		int offset = 0;
		while (offset < contentLength) {
			bytesRead = in.read(data, offset, data.length - offset);
			if (bytesRead == -1)
				break;
			offset += bytesRead;
		}
		in.close();

		if (offset != contentLength) {
			throw new IOException("Only read " + offset + " bytes; Expected "
					+ contentLength + " bytes");
		}
		// Save it !
		Log.e(TAG, "PATH :: " + path);
		FileOutputStream out = new FileOutputStream(path);
		out.write(data);
		out.flush();
		out.close();

	}

}
