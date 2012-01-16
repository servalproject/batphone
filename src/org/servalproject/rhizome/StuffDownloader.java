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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.servalproject.ServalBatPhoneApplication;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
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
			RhizomeRetriever.createDirectories();

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
			String tempFileName = RhizomeUtils.dirRhizomeTemp + "/"
					+ pManifest.getProperty("name");
			String downloadedFileName = RhizomeUtils.dirRhizome + "/"
					+ pManifest.getProperty("name");
			downloadFile(new URL(file), tempFileName);

			// Check the hash
			String hash = RhizomeUtils.ToHexString(RhizomeUtils
					.DigestFile(new File(tempFileName)));

			if (!hash.equals(pManifest.get("hash"))) {
				// Hell, the hash's wrong! Delete the logical file
				Log.w(TAG, "Wrong hash detected for manifest " + manifest);
			} else { // If it's all right, copy it to the real repo
				RhizomeUtils.CopyFileToDir(new File(tempFileName),
						RhizomeUtils.dirRhizome);

				RhizomeUtils.CopyFileToDir(new File(RhizomeUtils.dirRhizomeTemp
						+ "/" + mfName), RhizomeUtils.dirRhizome);

				// Generate the meta file for the newly received file
				String name = pManifest.getProperty("name");
				String version = pManifest.getProperty("version");
				try {
					RhizomeFile.GenerateMetaForFilename(name, Float
							.parseFloat(version));
				} catch (Exception e) {
					Log.e(TAG, e.toString(), e);
				}

				// Notify the main view that a file has been updated
				Handler handler = RhizomeRetriever.getHandlerInstance();
				if (handler != null) {
					Message updateMessage = handler.obtainMessage(
							RhizomeRetriever.MSG_UPD, name + " (v. " + version
									+ ")");
					handler.sendMessage(updateMessage);
				}

				if (downloadedFileName.toLowerCase().endsWith(".rpml"))
				{
					// File is a public message log - so we should tell batphone to
					// look for messages in the file.
					MessageLogExaminer.examineLog(downloadedFileName);
				}
				if (downloadedFileName.toLowerCase().endsWith(".map")) {
					// File is a map.
					// copy into place and notify user to restart mapping
					RhizomeUtils
							.CopyFileToDir(
									new File(downloadedFileName),
 new File(
							"/sdcard/serval/mapping-services/mapsforge/"));
					// TODO: Create a notification or otherwise tell the mapping
					// application that the
					// map is available.
				}
				if (downloadedFileName.toLowerCase().endsWith(".apk")) {
					PackageManager pm = ServalBatPhoneApplication.context
							.getPackageManager();
					PackageInfo info = pm.getPackageArchiveInfo(
							downloadedFileName, 0);
					if (info.packageName.equals("org.servalproject")) {
						int downloadedVersion = info.versionCode;
						try {
							int installedVersion = ServalBatPhoneApplication.context
									.getPackageManager().getPackageInfo(
											ServalBatPhoneApplication.context
													.getPackageName(), 0).versionCode;
							if (downloadedVersion > installedVersion) {
								// We have a newer version of Serval BatPhone,
								// open it to try to install it. This will only
								// work if the signing keys match, so we don't
								// need to do any further authenticity check
								// here.
								Intent i = new Intent(Intent.ACTION_VIEW)
										.setData(Uri.parse(downloadedFileName))
										.setType(
												"application/android.com.app");
								ServalBatPhoneApplication.context
										.startActivity(i);
							}
						} catch (NameNotFoundException e) {
							Log.e("BatPhone", e.toString(), e);
						}
					}

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

					Log.e(TAG, "comparing manifest versions: " + nmversion
							+ " vs " + omversion);
					Log.e(TAG, "comparing manifest versions: L>R = "
							+ (nmversion > omversion));
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
	static final Pattern pattern = Pattern.compile("<a href=\"(.+?)\"");

	private List<String> fetchManifests() {
		List<String> manifests = new ArrayList<String>();
		try {
			URL repoURL = new URL(repository);
			BufferedReader in = new BufferedReader(new InputStreamReader(
					repoURL.openStream()), 8192);
			StringBuilder sb = new StringBuilder();

			// Read each line
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				sb.append(inputLine);
			}

			Matcher matcher = pattern.matcher(sb.toString());
			while (matcher.find()) {
				String url = matcher.group(1);
				manifests.add(repository + url);
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
		InputStream in = new BufferedInputStream(raw, 8192);
		if (contentLength < 0)
			return;
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
