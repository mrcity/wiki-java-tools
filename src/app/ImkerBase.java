package app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.ResourceBundle;

import javax.security.auth.login.LoginException;

import wiki.Wiki;

interface DownloadStatusHandler {

	/**
	 * Handle the status for the initial step
	 * 
	 * @param i
	 *            the current index of the file about to download
	 * @param fileName
	 *            the name of the file about to download
	 */
	void handle1(int i, String fileName);

	/**
	 * Handle the status for the last step of the file download
	 * 
	 * @param status
	 *            Can be something like "... saved" or "... exists"
	 */
	void handle2(String status);
}

public class ImkerBase extends App {
	protected static final String VERSION = "v15.06.15";
	protected static final String PROGRAM_NAME = "Imker";
	protected static final String githubIssueTracker = "https://github.com/MarcoFalke/wiki-java-tools/issues/new?title=%s&body=%s";
	protected static final String[] INVALID_FILE_NAME_CHARS = { "{", "}", "<",
			">", "[", "]", "|" };
	protected static Wiki wiki = null;
	protected static String[] fileNames = null;
	protected static File outputFolder = null;
	protected static final ResourceBundle MSGS = ResourceBundle.getBundle(
			"i18n/Bundle", Locale.getDefault());

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
	protected static void downloadLoop(DownloadStatusHandler sh)
			throws IOException, LoginException {
		for (int i = 0; i < fileNames.length; i++) {
			final String fileName = fileNames[i].substring("File:".length());
			sh.handle1(i, fileName);
			final File outputFile = new File(outputFolder.getPath()
					+ File.separator + fileName);
			if (outputFile.exists()) {
				sh.handle2(" ... " + MSGS.getString("Status_File_Exists"));
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
				sh.handle2(" ... " + MSGS.getString("Status_File_Not_Found"));
				continue;
			}
			sh.handle2(" ... " + MSGS.getString("Status_File_Saved"));
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
