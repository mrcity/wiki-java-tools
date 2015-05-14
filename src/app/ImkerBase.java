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

import wiki.Wiki;

interface WikiAPI {
	/**
	 * Fetch something from the WikiAPI
	 * 
	 * @return the result from the API
	 * @throws IOException
	 *             if a network issue occurs
	 */
	Object fetch() throws IOException;
}

public class ImkerBase {
	protected static final String VERSION = "v15.05.12";
	protected static final String PROGRAM_NAME = "Imker";
	protected static final String githubIssueTracker = "https://github.com/MarcoFalke/wiki-java-tools/issues/new?title=%s&body=%s";
	protected static final String[] INVALID_FILE_NAME_CHARS = { "{", "}", "<",
			">", "[", "]", "|" };
	protected static Wiki wiki = null;
	protected static String[] fileNames = null;
	protected static File outputFolder = null;
	protected static final ResourceBundle MSGS = ResourceBundle.getBundle(
			"i18n/Bundle", Locale.getDefault());
	protected static final int MAX_FAILS = 3;
	private static final int EXCEPTION_SLEEP_TIME = 30 * 1000; // ms

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
	 * Attempt to fetch from the given api a maximum of maxFails and wait some
	 * time (max EXCEPTION_SLEEP_TIME) between tries.
	 * 
	 * @param api
	 *            the API to fetch from
	 * @param maxFails
	 *            the maximum number of exceptions to tolerate
	 * @return the result from the API
	 * @throws IOException
	 *             when a network error occurs
	 */
	protected static Object attemptFetch(WikiAPI api, int maxFails)
			throws IOException {
		maxFails--;
		try {
			return api.fetch();
		} catch (IOException e) {
			if (maxFails == 0) {
				throw e;
			} else {
				try {
					Thread.sleep(EXCEPTION_SLEEP_TIME / (maxFails * maxFails));
				} catch (InterruptedException ignore) {
				}
				return attemptFetch(api, maxFails);
			}
		}
	}

	/**
	 * Discard invalid file names and normalize the other.
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
