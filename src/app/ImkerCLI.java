package app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import wiki.Wiki;

public class ImkerCLI {
	private static final String categoryParam = "-category=";
	private static final String pageParam = "-page=";
	private static final String fileParam = "-file=";
	private static final String outParam = "-outfolder=";
	private static final String version = "v15.04.26";

	public static void main(String[] args) throws FileNotFoundException,
			IOException {

		System.out.println(version);
		printWelcome(args);

		Wiki commons = new Wiki("commons.wikimedia.org");
		commons.setMaxLag(3);
		commons.setLogLevel(Level.WARNING);

		File outputFolder = getFolder(args[1]);
		String[] fileNames = getFilenames(args[0], commons);

		download(fileNames, outputFolder, commons);
	}

	private static void download(String[] fileNames, File outputFolder,
			Wiki wiki) throws IOException {

		System.out.println("\nFolder: " + outputFolder.getPath()
				+ "\nDownload " + fileNames.length + " files?\n"
				+ "Press enter to continue. (Abort with CTRL+C)");
		System.in.read();

		for (int i = 0; i < fileNames.length; i++) {
			String fileName = fileNames[i].substring("File:".length());
			System.out.println("(" + (i + 1) + "/" + fileNames.length + "): "
					+ fileName);
			File outputFile = new File(outputFolder.getPath() + File.separator
					+ fileName);
			if (outputFile.exists()) {
				System.out.println(" ... exists locally");
				continue;
			}
			boolean downloaded = wiki.getImage(fileName, outputFile);
			if (downloaded == false) {
				System.out.println(" ... not found");
				continue;
			}
			System.out.println(" ... saved");
		}
		System.out.println("\nCompleted run!");
	}

	private static File getFolder(String pathArg) {

		int pathIndex = pathArg.indexOf(outParam);

		if (pathIndex < 0)
			// exit and warn user
			printWelcome(null);

		pathIndex += outParam.length();
		String path = pathArg.substring(pathIndex);
		File folder = new File(path);
		if (folder.isDirectory()) {
			return folder;
		} else {
			System.out.println("ERROR: Is not a folder: " + path);
			System.exit(-1);
			return null;
		}
	}

	private static String[] getFilenames(String input, Wiki wiki)
			throws FileNotFoundException, IOException {

		int catIndex = input.indexOf(categoryParam);
		int pageIndex = input.indexOf(pageParam);
		int fileIndex = input.indexOf(fileParam);

		if (catIndex > 0) {
			boolean subcat = false; // TODO: add argument --subcat
			catIndex += categoryParam.length();
			return wiki.getCategoryMembers(input.substring(catIndex), subcat,
					Wiki.FILE_NAMESPACE);
		} else if (pageIndex > 0) {
			pageIndex += pageParam.length();
			return wiki.getImagesOnPage(input.substring(pageIndex));
		} else if (fileIndex > 0) {
			fileIndex += fileParam.length();
			return readFileNames(input.substring(fileIndex));
		} else {
			// exit and warn user
			printWelcome(null);
			System.exit(-1);
			return null;
		}

	}

	private static String[] readFileNames(String localFilePath)
			throws FileNotFoundException, IOException {
		File input = new File(localFilePath);
		if (input.isFile()) {

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

		} else {
			System.out.println("ERROR: Is not a file: " + localFilePath);
			System.exit(-1);
			return null;
		}
	}

	private static String normalizeFileName(String line) {
		line = "File:" + line.replaceFirst("^([fF]ile:)", "");
		return line;
	}

	/**
	 * Verify input and print welcome message accordingly
	 * 
	 * @param args
	 *            the command line arguments
	 */
	private static void printWelcome(String[] args) {

		String[] expectedArgs = { "download-source", "target-folder" };
		String[] expectedArgsDescription = {
				"download-source can be a category, wiki page or local file."
						+ "\n    Examples:" + "\n     -" + categoryParam
						+ "\"Denver, Colorado\"" + "\n     -" + pageParam
						+ "\"Sandboarding\"" + "\n     -" + fileParam
						+ "\"Documents/files.txt\" (one filename per line!)",
				"target-folder is the output folder." + "\n    Example:"
						+ "\n     -" + outParam + "\"user/downloads\"" };
		if (args == null || args.length != expectedArgs.length) {
			System.out.print("Usage: java -jar filename.jar");
			for (String i : expectedArgs)
				System.out.print(" [" + i + "]");
			System.out.println("");
			for (String i : expectedArgsDescription)
				System.out.println(" ↳ " + i);
			System.exit(-1);
		}
	}
}
