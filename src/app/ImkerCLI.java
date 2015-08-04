package app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import javax.security.auth.login.LoginException;

import wiki.Wiki;

public class ImkerCLI extends ImkerBase {
	private final String CATEGORY_PARAM = "-category=";
	private final String PAGE_PARAM = "-page=";
	private final String FILE_PARAM = "-file=";
	private final String OUT_PARAM = "-outfolder=";
	private final StatusHandler stdOutPrint = new StatusHandler() {

		@Override
		public void handle(int i, String fileName) {
			System.out.println("(" + (i + 1) + "/" + getFileNames().length
					+ "): " + fileName);
		}

		@Override
		public void handleConclusion(String status2) {
			System.out.println(status2);
		}
	};

	public static void main(String[] args) throws FileNotFoundException,
			IOException, LoginException, NoSuchAlgorithmException {

		final ImkerCLI cli = new ImkerCLI();

		System.out.println(PROGRAM_NAME);
		System.out.println(MSGS.getString("Description_Program"));
		System.out.println(VERSION);
		System.out.println();
		cli.printHelp(args);

		cli.setWiki("commons.wikimedia.org");

		cli.setFolder(args[1]);
		cli.setFilenames(args[0]);

		cli.solveWindowsBug();
		cli.download();
		cli.verifyChecksum();

		cli.resetMemory();
	}

	/**
	 * Verify the checksum of all files and request permission from the user to
	 * delete corrupt files
	 * 
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	private void verifyChecksum() throws NoSuchAlgorithmException, IOException {
		System.out.println(MSGS.getString("Status_Checksum"));
		int errors = checksumLoop(stdOutPrint);
		System.out.println(String.format(
				MSGS.getString("Status_Checksum_Complete"), errors));
		if (errors == 0) {
			return;
		}

		for (int i = 0; i < getFileStatuses().length; i++) {
			if (getFileStatuses()[i] == FileStatus.CHECKSUM_ERROR) {
				System.out.println(" * " + getFileNames()[i]);
			}
		}
		System.out.println(String.format(MSGS.getString("Hint_Files_Corrupt"),
				errors));

		promptEnter();

		for (int i = 0; i < getFileStatuses().length; i++) {
			if (getFileStatuses()[i] == FileStatus.CHECKSUM_ERROR)
				Files.delete(new File(getOutputFolder().getPath()
						+ File.separator
						+ getFileNames()[i].substring(FILE_PREFIX.length()))
						.toPath());
		}

		System.out.println(MSGS.getString("Status_Checksum_Deleted"));
		System.out.println(MSGS.getString("Status_Restart_Needed"));
	}

	/**
	 * Check if affected by the "invalid character windows bug" and ask the user
	 * to exit or replace those chars
	 * 
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private void solveWindowsBug() throws IOException {
		if (!checkWindowsBug())
			return;

		System.out.println(MSGS.getString("Hint_Windows_Bug"));
		promptEnter();
	}

	/**
	 * Attempts download of the previously fetched file names
	 * 
	 * @throws IOException
	 *             if a network error occurs
	 * @throws LoginException
	 */
	private void download() throws IOException, LoginException {

		System.out.println("\n"
				+ MSGS.getString("Text_Folder")
				+ " "
				+ getOutputFolder().getPath()
				+ "\n"
				+ String.format(MSGS.getString("Prompt_Download"),
						getFileNames().length));
		promptEnter();

		downloadLoop(stdOutPrint);

		System.out.println("\n" + MSGS.getString("Status_Download_Complete"));
	}

	/**
	 * Prompt the user to hit enter and wait for them to hit enter
	 * 
	 * @throws IOException
	 *             if an I/O error occurs
	 * 
	 */
	private void promptEnter() throws IOException {
		System.out.println(MSGS.getString("Prompt_Enter"));
		System.in.read();
	}

	/**
	 * Parse the given path parameter from the command line or exit if no such
	 * directory is found
	 * 
	 * @param pathArg
	 *            the command line parameter
	 */
	private void setFolder(String pathArg) {

		int pathIndex = pathArg.indexOf(OUT_PARAM);

		if (pathIndex < 0)
			// exit and warn user
			printHelp(null);

		pathIndex += OUT_PARAM.length();
		String path = pathArg.substring(pathIndex);
		File folder = new File(path);
		if (folder.isDirectory()) {
			setOutputFolder(folder);
		} else {
			System.out.println(MSGS.getString("Status_Not_A_Folder") + " "
					+ path);
			System.exit(-1);
		}
	}

	/**
	 * Search for a category, a page or a file in the given parameter in this
	 * order or exit if none was found
	 * 
	 * @param inputArg
	 *            the command line parameter
	 * @throws FileNotFoundException
	 *             if the file parameter points to a missing file
	 * @throws IOException
	 *             if a IO issue occurs (network or file related)
	 * @throws LoginException
	 */
	private void setFilenames(String inputArg) throws FileNotFoundException,
			IOException, LoginException {

		int catIndex = inputArg.indexOf(CATEGORY_PARAM);
		int pageIndex = inputArg.indexOf(PAGE_PARAM);
		int fileIndex = inputArg.indexOf(FILE_PARAM);

		final String arg;
		String[] fnames = null;
		if (catIndex > 0) {
			arg = inputArg.substring(catIndex + CATEGORY_PARAM.length());
			fnames = (String[]) attemptFetch(new WikiAPI() {

				@Override
				public String[] fetch() throws IOException {
					boolean subcat = false; // TODO: add argument --subcat
					return getWiki().getCategoryMembers(arg, subcat,
							Wiki.FILE_NAMESPACE);
				}
			}, MAX_FAILS, EXCEPTION_SLEEP_TIME);
		} else if (pageIndex > 0) {
			arg = inputArg.substring(pageIndex + PAGE_PARAM.length());
			fnames = getImagesOnPage(arg, true);
		} else if (fileIndex > 0) {
			arg = inputArg.substring(fileIndex + FILE_PARAM.length());
			fnames = readFileNames(arg);
		} else {
			// exit and warn user
			printHelp(null);
			System.exit(-1);
		}
		setFileStatuses(new FileStatus[fnames.length]);
		setFileNames(fnames);
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
	private String[] readFileNames(String localFilePath)
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
	private void printHelp(String[] args) {

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
				System.out.println(" \u21B3 " + i); // \u21B3 is unicode for ↳
			System.exit(-1);
		}
	}
}
