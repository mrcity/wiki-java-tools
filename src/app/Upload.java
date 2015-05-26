package app;

import java.io.File;
import java.io.IOException;

import javax.security.auth.login.LoginException;

import wiki.Wiki;

public class Upload {

	public static final String VERSION = "v15.05.05";

	public static void main(String[] args) throws IOException, LoginException {

		char[] password = passwordDialog(args);
		Wiki commons = new Wiki("commons.wikimedia.org");

		commons.login(args[0], password);
		password = null;

		uploadFolder(commons, new File(args[1]), args[2]);

	}

	/**
	 * Verify the command line arguments and print program information while
	 * asking for the user's password
	 * 
	 * @param args
	 *            the command line arguments
	 * @return the password
	 */
	private static char[] passwordDialog(String[] args) {
		System.out.println(VERSION);

		String[] expectedArgs = { "username", "folder", "comment" };
		String[] expectedArgsDescription = {
				"username is your username on the wiki.",
				"folder is the folder to upload.",
				"comment is the upload summary." };
		if (args.length != expectedArgs.length) {
			System.out.print("Usage: java -jar filename.jar");
			for (String i : expectedArgs)
				System.out.print(" [" + i + "]");
			System.out.println("");
			for (String i : expectedArgsDescription)
				System.out.println("Where " + i);
			System.exit(-1);
		}
		System.out.println("Please type in the password for " + args[0] + ".");
		return System.console().readPassword();
	}

	/**
	 * 
	 * Recursively upload all files in this folder and its subfolders to the
	 * given wiki. The file names are kept.
	 * 
	 * @param wiki
	 *            Target wiki
	 * @param folder
	 *            Folder to be uploaded
	 * @param comment
	 *            Reason for the reupload. (Goes into the file history)
	 * @throws IOException
	 * @throws LoginException
	 * 
	 */
	private static void uploadFolder(Wiki wiki, File folder, String comment)
			throws LoginException, IOException {
		for (File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory()) {
				uploadFolder(wiki, fileEntry, comment);
			} else {
				System.out.println("Uploading " + fileEntry.getName());
				// TODO catch exceptions
				wiki.upload(fileEntry, fileEntry.getName(), "", comment
						+ " (Uploaded new version of file using Wiki.java)");
			}
		}
	}
}