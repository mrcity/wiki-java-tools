package app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;

import wiki.Wiki;

public class ImkerCLI extends ImkerBase {
	private static final String CATEGORY_PARAM = "-category=";
	private static final String PAGE_PARAM = "-page=";
	private static final String FILE_PARAM = "-file=";
	private static final String OUT_PARAM = "-outfolder=";

	public static void main(String[] args) throws FileNotFoundException,
			IOException {

		System.out.println(PROGRAM_NAME);
		System.out.println(MSGS.getString("Description_Program"));
		System.out.println(VERSION);
		System.out.println();
		printHelp(args);

		wiki = new Wiki("commons.wikimedia.org");
		wiki.setMaxLag(3);
		wiki.setLogLevel(Level.WARNING);

		outputFolder = getFolder(args[1]);
		fileNames = getFilenames(args[0]);

		download();
		// TODO verifyChecksum();
	}

	/**
	 * Attempts download of the previously fetched file names
	 * 
	 * @throws IOException
	 *             if a network error occurs
	 */
	private static void download() throws IOException {

		System.out.println("\n"
				+ MSGS.getString("Text_Folder")
				+ " "
				+ outputFolder.getPath()
				+ "\n"
				+ String.format(MSGS.getString("Prompt_Download"),
						fileNames.length) + "\n"
				+ MSGS.getString("Prompt_Enter"));
		System.in.read();

		for (int i = 0; i < fileNames.length; i++) {
			String fileName = fileNames[i].substring("File:".length());
			System.out.println("(" + (i + 1) + "/" + fileNames.length + "): "
					+ fileName);
			File outputFile = new File(outputFolder.getPath() + File.separator
					+ fileName);
			if (outputFile.exists()) {
				System.out.println(" ... "
						+ MSGS.getString("Status_File_Exists"));
				continue;
			}
			boolean downloaded = wiki.getImage(fileName, outputFile);
			if (downloaded == false) {
				System.out.println(" ... "
						+ MSGS.getString("Status_File_Not_Found"));
				continue;
			}
			System.out.println(" ... " + MSGS.getString("Status_File_Saved"));
		}
		System.out.println("\n" + MSGS.getString("Status_Run_Complete"));
	}

	/**
	 * Parse the given path parameter from the command line or exit if no such
	 * directory is found
	 * 
	 * @param pathArg
	 *            the command line parameter
	 * @return the folder represented by the path
	 */
	private static File getFolder(String pathArg) {

		int pathIndex = pathArg.indexOf(OUT_PARAM);

		if (pathIndex < 0)
			// exit and warn user
			printHelp(null);

		pathIndex += OUT_PARAM.length();
		String path = pathArg.substring(pathIndex);
		File folder = new File(path);
		if (folder.isDirectory()) {
			return folder;
		} else {
			System.out.println(MSGS.getString("Status_Not_A Folder") + " "
					+ path);
			System.exit(-1);
			return null;
		}
	}

	/**
	 * Search for a category, a page or a file in the given parameter in this
	 * order or exit if none was found
	 * 
	 * @param inputArg
	 *            the command line parameter
	 * @return a String array with all file names
	 * @throws FileNotFoundException
	 *             if the file parameter points to a missing file
	 * @throws IOException
	 *             if a IO issue occurs (network or file related)
	 */
	private static String[] getFilenames(String inputArg)
			throws FileNotFoundException, IOException {

		int catIndex = inputArg.indexOf(CATEGORY_PARAM);
		int pageIndex = inputArg.indexOf(PAGE_PARAM);
		int fileIndex = inputArg.indexOf(FILE_PARAM);

		if (catIndex > 0) {
			boolean subcat = false; // TODO: add argument --subcat
			catIndex += CATEGORY_PARAM.length();
			return wiki.getCategoryMembers(inputArg.substring(catIndex),
					subcat, Wiki.FILE_NAMESPACE);
		} else if (pageIndex > 0) {
			pageIndex += PAGE_PARAM.length();
			return wiki.getImagesOnPage(inputArg.substring(pageIndex));
		} else if (fileIndex > 0) {
			fileIndex += FILE_PARAM.length();
			return readFileNames(inputArg.substring(fileIndex));
		} else {
			// exit and warn user
			printHelp(null);
			System.exit(-1);
			return null;
		}

	}

	/**
	 * Create a new file from the path and return valid file names in it or exit
	 * if it is a folder
	 * 
	 * @param localFilePath
	 *            the path to the local file
	 * @return a array of Strings holding all file names
	 * @throws FileNotFoundException
	 *             if the file can not be found
	 * @throws IOException
	 *             if there was an issue reading the file
	 */
	private static String[] readFileNames(String localFilePath)
			throws FileNotFoundException, IOException {
		File input = new File(localFilePath);
		if (input.isFile()) {
			return parseFileNames(localFilePath);
		} else {
			System.out.println(MSGS.getString("Status_Not_A_File") + " "
					+ localFilePath);
			System.exit(-1);
			return null;
		}
	}

	/**
	 * Verify input parameters; Print help message and exit if invalid arguments
	 * were given
	 * 
	 * @param args
	 *            the command line arguments or null
	 */
	private static void printHelp(String[] args) {

		String[] expectedArgs = { MSGS.getString("CLI_Arg_Src"),
				MSGS.getString("CLI_Arg_Output") };
		String[] expectedArgsDescription = {
				MSGS.getString("Description_Download_Src") + "\n    "
						+ MSGS.getString("Text_Examples") + "\n     -"
						+ CATEGORY_PARAM + "\"Denver, Colorado\"" + "\n     -"
						+ PAGE_PARAM + "\"Sandboarding\"" + "\n     -"
						+ FILE_PARAM + "\"Documents/files.txt\" ("
						+ MSGS.getString("Hint_File_Syntax") + ")",
				MSGS.getString("Description_Target_Folder") + "\n    "
						+ MSGS.getString("Text_Example") + "\n     -"
						+ OUT_PARAM + "\"user/downloads\"" };
		if (args == null || args.length != expectedArgs.length) {
			System.out.print(MSGS.getString("Text_Usage")
					+ " java -jar filename.jar");
			for (String i : expectedArgs)
				System.out.print(" [" + i + "]");
			System.out.println("");
			for (String i : expectedArgsDescription)
				System.out.println(" ↳ " + i);
			System.exit(-1);
		}
	}
}
