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
	private static final String outParam = "-outfolder=";

	public static void main(String[] args) throws FileNotFoundException,
			IOException {

		System.out.println(PROGRAM_NAME);
		System.out.println(msgs.getString("Description_Program"));
		System.out.println(VERSION);
		System.out.println();
		printWelcome(args);

		wiki = new Wiki("commons.wikimedia.org");
		wiki.setMaxLag(3);
		wiki.setLogLevel(Level.WARNING);

		outputFolder = getFolder(args[1]);
		fileNames = getFilenames(args[0]);

		download();
		// TODO verifyChecksum();
	}

	private static void download() throws IOException {

		System.out.println("\n"
				+ msgs.getString("Text_Folder")
				+ " "
				+ outputFolder.getPath()
				+ "\n"
				+ String.format(msgs.getString("Prompt_Download"),
						fileNames.length) + "\n"
				+ msgs.getString("Prompt_Enter"));
		System.in.read();

		for (int i = 0; i < fileNames.length; i++) {
			String fileName = fileNames[i].substring("File:".length());
			System.out.println("(" + (i + 1) + "/" + fileNames.length + "): "
					+ fileName);
			File outputFile = new File(outputFolder.getPath() + File.separator
					+ fileName);
			if (outputFile.exists()) {
				System.out.println(" ... "
						+ msgs.getString("Status_File_Exists"));
				continue;
			}
			boolean downloaded = wiki.getImage(fileName, outputFile);
			if (downloaded == false) {
				System.out.println(" ... "
						+ msgs.getString("Status_File_Not_Found"));
				continue;
			}
			System.out.println(" ... " + msgs.getString("Status_File_Saved"));
		}
		System.out.println("\n" + msgs.getString("Status_Run_Complete"));
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
			System.out.println(msgs.getString("Status_Not_A Folder") + " "
					+ path);
			System.exit(-1);
			return null;
		}
	}

	private static String[] getFilenames(String input)
			throws FileNotFoundException, IOException {

		int catIndex = input.indexOf(CATEGORY_PARAM);
		int pageIndex = input.indexOf(PAGE_PARAM);
		int fileIndex = input.indexOf(FILE_PARAM);

		if (catIndex > 0) {
			boolean subcat = false; // TODO: add argument --subcat
			catIndex += CATEGORY_PARAM.length();
			return wiki.getCategoryMembers(input.substring(catIndex), subcat,
					Wiki.FILE_NAMESPACE);
		} else if (pageIndex > 0) {
			pageIndex += PAGE_PARAM.length();
			return wiki.getImagesOnPage(input.substring(pageIndex));
		} else if (fileIndex > 0) {
			fileIndex += FILE_PARAM.length();
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
			return parseFileNames(localFilePath);
		} else {
			System.out.println(msgs.getString("Status_Not_A_File") + " "
					+ localFilePath);
			System.exit(-1);
			return null;
		}
	}

	/**
	 * Verify input and print welcome message accordingly
	 * 
	 * @param args
	 *            the command line arguments
	 */
	private static void printWelcome(String[] args) {

		String[] expectedArgs = { msgs.getString("CLI_Arg_Src"),
				msgs.getString("CLI_Arg_Output") };
		String[] expectedArgsDescription = {
				msgs.getString("Description_Download_Src") + "\n    "
						+ msgs.getString("Text_Examples") + "\n     -"
						+ CATEGORY_PARAM + "\"Denver, Colorado\"" + "\n     -"
						+ PAGE_PARAM + "\"Sandboarding\"" + "\n     -"
						+ FILE_PARAM + "\"Documents/files.txt\" ("
						+ msgs.getString("Hint_File_Syntax") + ")",
				msgs.getString("Description_Target_Folder") + "\n    "
						+ msgs.getString("Text_Example") + "\n     -"
						+ outParam + "\"user/downloads\"" };
		if (args == null || args.length != expectedArgs.length) {
			System.out.print(msgs.getString("Text_Usage")
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
