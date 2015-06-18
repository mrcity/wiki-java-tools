package app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.ResourceBundle;

import javax.security.auth.login.LoginException;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

import wiki.Wiki;

interface StatusHandler {

	/**
	 * Handle the status for the initial step of each iteration
	 * 
	 * @param i
	 *            the current index of the file about to download
	 * @param fileName
	 *            the name of the file about to download
	 */
	void handle(int i, String fileName);

	/**
	 * Handle the status for the last step of the file download
	 * 
	 * @param status
	 *            Can be something like "... saved" or "... exists"
	 */
	void handleConclusion(String status);
}

public class ImkerBase extends App {
	protected static final String VERSION = "v15.06.16";
	protected static final String PROGRAM_NAME = "Imker";
	protected static final String GITHUB_ISSUE_TRACKER = "https://github.com/MarcoFalke/wiki-java-tools/issues/new?title=%s&body=%s";
	protected static final String FILE_PREFIX = "File:";
	protected static final String[] INVALID_FILE_NAME_CHARS = { "{", "}", "<",
			">", "[", "]", "|" };
	protected static Wiki wiki = null;
	protected static String[] fileNames = null;
	protected static FileStatus[] fileStatuses = null;
	protected static File outputFolder = null;
	protected static final ResourceBundle MSGS = ResourceBundle.getBundle(
			"i18n/Bundle", Locale.getDefault());

	protected enum FileStatus {
		DOWNLOADED, NOT_FOUND, CHECKSUM_CONFIRMED, CHECKSUM_ERROR
	}

	/**
	 * Read the given file and extract valid file names in each line
	 * 
	 * @param localFilePath
	 *            the path to the local file
	 * @return array holding all file names
	 * @throws FileNotFoundException
	 *             if the local file was not found
	 * @throws IOException
	 *             if there was an issue reading the file
	 */
	protected static String[] parseFileNames(String localFilePath)
			throws FileNotFoundException, IOException {
		// TODO resolve redirects
		Queue<String> FileNameQueue = new LinkedList<String>();

		try (BufferedReader br = new BufferedReader(new FileReader(
				localFilePath))) {
			String line = br.readLine();
			while (line != null) {
				String fileName = normalizeFileName(line);
				if (fileName != null)
					FileNameQueue.add(fileName);
				line = br.readLine();
			}
		}
		String[] tempStringArray = { "" };
		return FileNameQueue.toArray(tempStringArray);
	}

	/**
	 * Loop over all file names and call the StatusHandler; Files already
	 * existing locally as well as files not found in the remote are skipped!
	 * 
	 * @param sh
	 *            the status handler to call
	 * @throws IOException
	 *             if an io error (network or file related) occurs
	 * @throws LoginException
	 */
	protected static void downloadLoop(StatusHandler sh) throws IOException,
			LoginException {
		for (int i = 0; i < fileNames.length; i++) {
			final String fileName = fileNames[i]
					.substring(FILE_PREFIX.length());
			sh.handle(i, fileName);
			final File outputFile = new File(outputFolder.getPath()
					+ File.separator + fileName);
			if (outputFile.exists()) {
				fileStatuses[i] = FileStatus.DOWNLOADED;
				sh.handleConclusion(" ... "
						+ MSGS.getString("Status_File_Exists"));
				continue;
			}
			// TODO add option to rate limit
			boolean downloaded = (boolean) attemptFetch(new WikiAPI() {

				@Override
				public Object fetch() throws IOException {
					return wiki.getImage(fileName, outputFile);
				}
			}, MAX_FAILS, EXCEPTION_SLEEP_TIME);
			if (downloaded == false) {
				fileStatuses[i] = FileStatus.NOT_FOUND;
				sh.handleConclusion(" ... "
						+ MSGS.getString("Status_File_Not_Found"));
				continue;
			}
			fileStatuses[i] = FileStatus.DOWNLOADED;
			sh.handleConclusion(" ... " + MSGS.getString("Status_File_Saved"));
		}
	}

	/**
	 * Loop over all file names and call the StatusHandler; All files with
	 * FileStatus.DOWNLOADED get their checksum confirmed
	 * 
	 * @param sh
	 *            the status handler to call
	 * @throws IOException
	 *             if an io error (network or file related) occurs
	 * @throws NoSuchAlgorithmException
	 *             should never happen
	 * @throws FileNotFoundException
	 *             if the file does not exist
	 */
	protected static int checksumLoop(StatusHandler sh)
			throws FileNotFoundException, NoSuchAlgorithmException, IOException {
		int errors = 0;
		for (int i = 0; i < fileStatuses.length; i++) {
			final String fileName = fileNames[i]
					.substring(FILE_PREFIX.length());
			sh.handle(i, fileName);
			if (fileStatuses[i] == FileStatus.DOWNLOADED) {

				String localSHA1 = calcSHA1(new File(outputFolder.getPath()
						+ File.separator + fileName));
				String wikiSHA1 = ((String) wiki.getFileMetadata(fileName).get(
						"sha1")).toUpperCase();

				if (localSHA1.equals(wikiSHA1)) {
					fileStatuses[i] = FileStatus.CHECKSUM_CONFIRMED;
					sh.handleConclusion(" ... "
							+ MSGS.getString("Status_Checksum_Confirmed"));
				} else {
					fileStatuses[i] = FileStatus.CHECKSUM_ERROR;
					errors++;
					sh.handleConclusion(" ... "
							+ MSGS.getString("Status_Checksum_Error"));
				}
			}
		}
		return errors;
	}

	/**
	 * Read the file and calculate the SHA-1 checksum
	 * 
	 * @param file
	 *            the file to read
	 * @return the hex representation of the SHA-1 using uppercase chars
	 * @throws FileNotFoundException
	 *             if the file does not exist, is a directory rather than a
	 *             regular file, or for some other reason cannot be opened for
	 *             reading
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws NoSuchAlgorithmException
	 *             should never happen
	 */
	private static String calcSHA1(File file) throws FileNotFoundException,
			IOException, NoSuchAlgorithmException {

		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		try (InputStream input = new FileInputStream(file)) {

			byte[] buffer = new byte[8192];
			int len = input.read(buffer);

			while (len != -1) {
				sha1.update(buffer, 0, len);
				len = input.read(buffer);
			}

			return new HexBinaryAdapter().marshal(sha1.digest());
		}
	}

	/**
	 * Discard invalid file names and normalize the others.
	 * 
	 * @param line
	 *            the String to be normalized
	 * @return a normalized file name or null
	 */
	protected static String normalizeFileName(String line) {
		for (String invalidChar : INVALID_FILE_NAME_CHARS) {
			if (line.contains(invalidChar))
				return null;
		}
		line = "File:" + line.replaceFirst("^([fF]ile:)", "");
		if (line.length() == "File:".length())
			return null;
		return line;
	}

}
