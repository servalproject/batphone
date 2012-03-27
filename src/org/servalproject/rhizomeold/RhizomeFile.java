/**
 *
 */
package org.servalproject.rhizomeold;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import to.yp.cr.NaCl;
import android.content.Intent;
import android.util.Log;

/**
 * @author rbochet A Rhizome logical file is composed by three files : - The
 *         actual file (ie <file>) - The .<file>.manifest - The .<file>.meta
 */
public class RhizomeFile {

	/** TAG for debugging */
	public static final String TAG = "R2";

	List<byte[]> signatories = null;

	String path = null;

	/** The actual file */
	File file = null;

	/** The associated manifest file */
	File manifest = null;

	/** The associated meta file */
	File meta = null;

	/**
	 * Create the logical file
	 *
	 * @param path
	 *            The root of the rhizome directory
	 * @param fileName
	 *            the relative path
	 */
	public RhizomeFile(File path, String fileName) {
		// We know that the actual file exists because it has been detected
		file = new File(path, fileName);

		// Create the manifest/meta path if they exists
		setPath(path.getAbsolutePath());
		setManifest(path, fileName);
		setMeta(path, fileName);
	}

	/**
	 * Create a Rhizome file, and put the provided data into the physical file.
	 *
	 * @param fileName
	 *            Name of file to create/update in Rhizome space
	 * @param contents
	 *            The bytes that should be put into the file
	 * @throws IOException
	 */
	public RhizomeFile(String fileName, byte[] contents, boolean appendP)
			throws IOException {
		File path = RhizomeUtils.dirRhizome;
		file = new File(path, fileName);
		FileOutputStream o = new FileOutputStream(file, appendP);
		o.write(contents);
		o.close();

		// Create the manifest/meta path if they exists
		setPath(path.getAbsolutePath());
		setManifest(path, fileName);
		setMeta(path, fileName);
	}

	/**
	 * Delete the logical file -- ie all 3 files if they exists. If this
	 * function is called, the object should be destroyed quickly.
	 *
	 * @throws IOException
	 *             If the deletion fails
	 */
	public void delete() throws IOException {
		file.delete();
		if (manifest != null)
			manifest.delete();
//		if (meta != null)
//			meta.delete();
	}

	/**
	 * Export the file in the given dir. The directory is defined in the main
	 * app.
	 *
	 * @throws IOException
	 *             If the copy fails.
	 */
	public void export() throws IOException {
		RhizomeUtils.CopyFileToDir(file, RhizomeUtils.dirExport);
	}

	private void setPath(String pathname) {
		path = pathname;
	}

	/**
	 * @return the file path
	 */
	public File getFile() {
		return file;
	}

	/**
	 * @return the manifest path
	 */
	public File getManifest() {
		return manifest;
	}

	/**
	 * @return the meta path
	 */
	public File getMeta() {
		return meta;
	}

	/**
	 * Mark the file for expiration (put true in marked_expiration key)
	 *
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public void markForExpiration() throws FileNotFoundException, IOException {
		Properties metaP = new Properties();
		metaP.load(new FileInputStream(meta));
		metaP.remove("marked_expiration");
		metaP.put("marked_expiration", true + "");
		metaP.store(new FileOutputStream(meta),
				"Rhizome meta data for " + file.getName());
	}

	/**
	 * If the manifest file exists, sets it up.
	 *
	 * @param path
	 *            The root of the rhizome directory
	 * @param fileName
	 *            The name of the actual file
	 */
	private void setManifest(File path, String fileName) {
		File tmp = new File(path, "." + fileName + ".manifest");
		if (tmp.exists())
			manifest = tmp;
	}

	/**
	 * If the meta file exists, sets it up.
	 *
	 * @param path
	 *            The root of the rhizome directory
	 * @param fileName
	 *            The name of the actual file
	 */
	private void setMeta(File path, String fileName) {
		File tmp = new File(path, "." + fileName + ".meta");
		if (tmp.exists())
			meta = tmp;
	}

	public boolean isTrustedP(long threshold) {
		// See if the manifest has been signed
		byte[] hash = RhizomeUtils.DigestFile(getManifest());
		if (hash == null)
			return false;
		String signatureFilename = path +"/.signature."+RhizomeUtils.ToHexString(hash);
		File signatureFile = null;
		try {
			signatureFile = new File(signatureFilename);
			byte[] signature = RhizomeUtils.readFileBytes(signatureFile);
			// Go through signature data trying each in turn (a file may be signed
			// by more than one authority).
			discoverSignatories(signature,hash);
			Iterator<byte[]> it =signatories.iterator();
			while (it.hasNext())
			{
				byte[] e = it.next();
				if (RhizomeSignatures.getTrustInMilliCents(e)>=threshold) return true;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {

		}

		return false;
	}

	private void discoverSignatories(byte[] signatureData,byte[] hash)
	{
		signatories = new LinkedList<byte[]>();
		int i,j;
		int signatureBlockSize=NaCl.crypto_sign_PUBLICKEYBYTES+NaCl.crypto_sign_BYTES+hash.length;

		// Grab each signature in turn and check it.
		byte[] signatory = new byte[NaCl.crypto_sign_PUBLICKEYBYTES];
		byte[] sig = new byte[NaCl.crypto_sign_BYTES+hash.length];
		for(i=0;i<=(signatureData.length-signatureBlockSize);i+=signatureBlockSize) {
			// Get public key
			for(j=0;j<signatureBlockSize;j++) signatory[j]=signatureData[i+j];
			// Get signed data
			for(j=0;j<sig.length;j++) sig[j]=signatureData[i+NaCl.crypto_sign_PUBLICKEYBYTES+j];
			// Test the signature
			NaCl.CryptoSignOpen sigResult = new NaCl.CryptoSignOpen(signatory, sig);
			if (sigResult.result == 0 && sigResult.message.length >= hash.length) {
				// Okay, signature tests out, but let's just make sure it was a signature for
				// this file, and not some other.  This would be bad otherwise.
				for(j=0;j<hash.length;j++) if (hash[j]!=sigResult.message[j]) break;
				// XXX - Allow further constraints on the signature, such as expiry dates and
				// uses.  These can follow the hash in the signed message whe we get that far.

				// All tests passed, so add this signatory to the list of those who have attested
				// to the validity of this file.
				if (j==hash.length) signatories.add(signatory.clone());
			}
		}
	}

	public boolean certifyManifest()
	{
		// Get our key (okay, this is something we haven't sorted out yet)
		byte[] secretKey = null;
		// Get hash of manifest
		byte[] hash = RhizomeUtils.DigestFile(getManifest());
		String signatureFilename = path +"/.signature."+RhizomeUtils.ToHexString(hash);
		File signatureFile = null;
		try {
			signatureFile = new File(signatureFilename);
		} finally {

		}
		// Create signature block
		// Write signature block

		// Failed
		return false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer ts = new StringBuffer();

		ts.append("-- BOF --\n");
		ts.append(" File:                        " + getFile() + "\n");
		ts.append(" -> Manifest:                 " + getManifest() + "\n");
		ts.append(" -> Meta:                     " + getMeta() + "\n");
		ts.append(" -> Trusted >$1?:             " + isTrustedP(100*1000)+"\n");
		ts.append("-- EOF --");

		return ts.toString();
	}

	/**
	 * When a file appears (imported or downloaded), this method creates the
	 * associated meta file. The meta file is only for the current handset. The
	 * meta file is created in Rhizome home directory.
	 *
	 * @param fileName
	 *            The name of the incoming file
	 * @param version
	 */
	public static void GenerateMetaForFilename(String fileName, long version) {
		try {
			Properties metaP = new Properties();

			// Setup the property object
			metaP.put("date", System.currentTimeMillis() + "");
			metaP.put("read", false + ""); // the file is just created
			metaP.put("marked_expiration", false + ""); // Just imported
			metaP.put("version", version +"");

			// Save the file
			File tmpMeta = new File(RhizomeUtils.dirRhizome, "." + fileName + ".meta");
			Log.v(TAG, tmpMeta + "");
			metaP.store(new FileOutputStream(tmpMeta), "Rhizome meta data for "
					+ fileName);
		} catch (Exception e) {
			Log.e(TAG, "Error when creating meta for " + fileName);
			e.printStackTrace();
		}
	}

	public static File CopyFile(File source, String dest)
			throws IOException {
		if (dest.contains("/")) {
			dest = dest.substring(dest.lastIndexOf("/") + 1);
		}
		File destFile = new File(RhizomeUtils.dirRhizome,
				dest == null ? source.getName() : dest);

		// well if we can't depend on cp, might as well copy it using streams...
		byte buff[] = new byte[4 * 1024];

		InputStream in = null;
		OutputStream out = null;
		try {
			in = new FileInputStream(source);
			out = new FileOutputStream(destFile);
			int read;
			while ((read = in.read(buff)) >= 0) {
				out.write(buff, 0, read);
			}
		} finally {
			if (in != null)
				in.close();
			if (out != null)
				out.close();
		}
		destFile.setLastModified(source.lastModified());
		return destFile;
	}
	/**
	 * Create a manifest file for the imported file. The timestamp is set at the
	 * current value.
	 *
	 * @param fileName
	 *            Name of the file
	 * @param author
	 *            Author of the file
	 * @param version
	 *            Version of the file
	 * @param size
	 */
	public static void GenerateManifestForFilename(File file,
 String author,
			long version) {
		String fileName = file.getName();
		try {
			Properties manifestP = new Properties();
			// Set up the property object
			manifestP.put("author", author);
			manifestP.put("name", fileName);
			manifestP.put("version", version + "");
			manifestP.put("date", System.currentTimeMillis() + "");
			// The locally computed
			manifestP.put("size", file.length()
					+ "");
			manifestP.put("hash",
					RhizomeUtils.ToHexString(RhizomeUtils.DigestFile(file)));

			// Save the file
			File tmpManifest = new File(RhizomeUtils.dirRhizome, "." + fileName
					+ ".manifest");
			Log.v(TAG, tmpManifest + "");
			manifestP.store(new FileOutputStream(tmpManifest),
					"Rhizome manifest data for " + fileName);
		} catch (IOException e) {
			Log.e(TAG, "Error when creating manifest for " + fileName);
		}

	}

	/**
	 * This function populates an Intent for the manifest
	 *
	 * @return The manifest wrapped in an Intent
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public Intent populateDisplayIntent(Intent intent)
			throws FileNotFoundException, IOException {
		// Load the properties
		Properties manifestP = new Properties();
		manifestP.load(new FileInputStream(manifest));

		// Populate the intent
		intent.putExtra("author", manifestP.getProperty("author"));
		intent.putExtra("hash", manifestP.getProperty("hash"));
		intent.putExtra("version", manifestP.getProperty("version"));
		intent.putExtra("date", manifestP.getProperty("date"));
		intent.putExtra("size", manifestP.getProperty("size"));
		intent.putExtra("name", manifestP.getProperty("name"));

		return intent;
	}

}

