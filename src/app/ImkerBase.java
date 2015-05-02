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

public class ImkerBase {
	protected static final String VERSION = "v15.05.01";
	protected static final String PROGRAM_NAME = "Imker";
	protected static final String githubIssueTracker = "https://github.com/MarcoFalke/wiki-java-tools/issues/new";
	protected static Wiki wiki = null;
	protected static String[] fileNames = null;
	protected static File outputFolder = null;

	protected static final ResourceBundle msgs = ResourceBundle.getBundle(
			"app.I18nBundle", Locale.getDefault());

	protected static String[] parseFileNames(String localFilePath)
			throws FileNotFoundException, IOException {
		Queue<String> FileNameQueue = new LinkedList<String>();

		try (BufferedReader br = new BufferedReader(new FileReader(
				localFilePath))) {
			String line = br.readLine();
			while (line != null) {
				FileNameQueue.add(normalizeFileName(line));
				line = br.readLine();
			}
		}
		String[] tempStringArray = { "" };
		return FileNameQueue.toArray(tempStringArray);
	}

	protected static String normalizeFileName(String line) {
		line = "File:" + line.replaceFirst("^([fF]ile:)", "");
		return line;
	}

}
