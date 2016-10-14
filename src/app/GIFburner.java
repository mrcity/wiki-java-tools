package app;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;

import javax.imageio.ImageIO;
import javax.security.auth.login.LoginException;

import wiki.Wiki;
import wiki.WikiPage;
import wiki.Wiki.LogEntry;

public class GIFburner extends App {

	static Wiki commons;
	static int replaceCounter = 0;
	static int replaceCounterMax = 10;
	static String replaceCOMDEL = "";
	static String zopfliPNGexe;
	static String optiPNGexe;
	final static String COMMONS_DELINKER_PAGE = "User:CommonsDelinker/commands";
	final static int WAIT_AFTER_COMDEL_REQUEST = 15; // in minutes
	final static String VERSION = "v15.08.18";

	static String linkList = "";
	static Queue<String> deleteQueue = new LinkedList<>();

	public static void main(String[] args) throws IOException {

		System.exit(-1); // not approved/tested

		char[] password = passwordDialog(args);

		System.out
				.println("Is https://commons.wikimedia.org/w/index.php?title=Category:DuplicatePNG empty?");
		System.in.read();

		commons = new Wiki("commons.wikimedia.org");

		try {
			commons.login(args[0], password);
			password = null;
			commons.setThrottle(8 * 1000);
			commons.setMaxLag(3);
			commons.setMarkBot(true);
			commons.setMarkMinor(true);
			commons.setLogLevel(Level.WARNING);

			zopfliPNGexe = args[1];
			optiPNGexe = args[2];
			Process zopfliProcess = new ProcessBuilder(zopfliPNGexe).start();
			if (0 != zopfliProcess.waitFor()) {
				System.out.println(zopfliPNGexe
						+ " not a valid zopflipng executable. Shutdown");
				System.exit(-1);
			}
			Process optiProcess = new ProcessBuilder(optiPNGexe).start();
			if (0 != optiProcess.waitFor()) {
				System.out.println(optiPNGexe
						+ " not a valid optipng executable. Shutdown");
				System.exit(-1);
			}

			gifTagger("Images which should be in PNG format");

		} catch (Exception e) {
			e.printStackTrace();
			writeConclusion();
			System.out.println("\n" + "Emergency Shutdown: Unhandled "
					+ e.getClass().getName() + "!" + "\n");
		}

	}

	/**
	 * Verify the command line arguments and print program information while
	 * asking for the user's password
	 * 
	 * @param args
	 *            the command line arguments
	 * @return the password
	 * @throws IOException
	 *             If an I/O error occurs
	 */
	private static char[] passwordDialog(String[] args) throws IOException {

		System.out.println(VERSION);

		String[] expectedArgs = { "username", "zopfli", "optipng" };
		String[] expectedArgsDescription = {
				"username is your username on the wiki.",
				"zopfli is the executable to compress png files",
				"optipng is the executable to compress gif files to png" };
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
		if (System.console() == null) {
			return (new BufferedReader(new InputStreamReader(System.in))).readLine().toCharArray();
		}
		return System.console().readPassword();
	}

	/**
	 * Run the main program
	 * 
	 * @param category
	 *            the category to read the files from
	 * @throws LoginException
	 * @throws IOException
	 *             if a network error occurs
	 * @throws InterruptedException
	 */
	private static void gifTagger(String category) throws LoginException,
			IOException, InterruptedException {

		final File PNGtemp = File.createTempFile("gif2png", ".png", null);
		final File GIFtemp = File.createTempFile("gif", ".gif", null);
		PNGtemp.deleteOnExit();
		GIFtemp.deleteOnExit();

		String[] files = commons.getCategoryMembers(category,
				new int[] { Wiki.FILE_NAMESPACE });
		for (String strLine : files) {
			strLine = strLine.substring("File:".length());

			final WikiPage source;
			source = new WikiPage(commons, "File:" + strLine);

			String metadata = (String) attemptFetch(new WikiAPI() {

				@Override
				public Object fetch() throws IOException {
					return commons.getFileMetadata(source.getName()).toString();
				}
			}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);
			if (!metadata.contains("mime=image/gif")) {
				System.out.println("Skip: " + source.getName()
						+ " (Not image/gif)");
				continue;
			}
			if (metadata.contains("frameCount=1, ")) {

				LogEntry[] log = (LogEntry[]) attemptFetch(new WikiAPI() {

					@Override
					public Object fetch() throws IOException {
						return commons.getImageHistory(source.getName());
					}
				}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);
				if (log.length > 1) {
					System.out.println("Skip: " + source.getName()
							+ " (More than one revision)");
					// TODO not continue!
					continue;
				}
				boolean downloaded = (boolean) attemptFetch(new WikiAPI() {

					@Override
					public Object fetch() throws IOException {
						return commons.getImage(source.getName(), GIFtemp);
					}
				}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);
				if (!downloaded) {
					throw new RuntimeException(source.getName()
							+ "does not exist anymore!");
				}
				BufferedImage GIFimage;
				try {
					GIFimage = ImageIO.read(GIFtemp);
				} catch (ArrayIndexOutOfBoundsException e) {
					System.out.println("Skip: File:" + strLine
							+ " (ArrayIndexOutOfBoundsException)");
					continue;
				}
				boolean isGreyScale = isGreyscale(GIFimage);
				boolean isOpaque = isOpaque(GIFimage);
				if (isGreyScale && isOpaque) {
					System.out.println("Skip: " + source.getName()
							+ " (Opaque & Greyscale)");
					continue;
				}
				final String issue = "This GIF was problematic due to "
						+ (isOpaque ? "" : "transparency")
						+ (!isGreyScale && !isOpaque ? " and " : "")
						+ (isGreyScale ? "" : "non-greyscale color table")
						+ ". ";

				System.out.println("Doing: " + source.getName()
						+ " (frameCount=1)");
				ImageIO.write(GIFimage, "png", PNGtemp);

				final String filename = appendSuffix(commons, source.getName()
						.replaceAll("\\.[a-zA-Z]+$", ""));
				String historyLog = createHistoryLog(GIFimage, GIFtemp, log);

				source.replaceAllInPagetext(
						"\\{\\{ *[Ss]?(hould)?( )?[Bb]?(e)?( )?[Cc]?(onvert)?( )?[Tt]?o? ?[Pp][Nn][Gg] *\\}\\}",
						"");
				source.replaceAllInPagetext(
						"\\[\\[ *[cC]ategory: *[gG]IF *\\]\\]", "");
				source.replaceAllInPagetext(
						"\\{\\{ *[bB]ad[gG][iI][fF] *\\}\\}", "");
				source.replaceAllInPagetext(
						"\\{\\{ *([pP]NG (version )?available|[sS]uperseded ?PNG) *\\|",
						"{{superseded|");

				final String pageText = source.getPlainText()
						+ "\n== {{Original upload log}} ==\n"
						+ "{| class=\"wikitable\"\n|-\n"
						+ "! {{int:filehist-datetime}} !! {{int:filehist-dimensions}} !! {{int:filehist-user}} !! {{int:filehist-comment}}\n"
						+ historyLog + "|}";

				// Compress PNG

				final File zopfliPngFile = File.createTempFile("pngZopfli",
						".png", null);
				final File optiPngFile = File.createTempFile("pngOpti", ".png",
						null);
				zopfliPngFile.deleteOnExit();
				optiPngFile.deleteOnExit();

				Process zopfliPNGProcess = new ProcessBuilder(zopfliPNGexe,
						"--lossy_transparent", PNGtemp.getPath(),
						zopfliPngFile.getPath()).start();
				Process optiPNGProcess = new ProcessBuilder(optiPNGexe,
						GIFtemp.getPath(), "-o5", "-clobber",// "-preserve",//"-fix",
						"-out", optiPngFile.getPath()).start();

				if (0 != zopfliPNGProcess.waitFor()) {
					throw new RuntimeException("zopfliPngExitValue");
					// TODO continue...
					// System.out.println("Skip: File:" + strLine
					// + " (zopfliPngExitValue)");
					// continue;
				}

				if (0 != optiPNGProcess.waitFor()) {
					throw new RuntimeException("optiPngExitValue");
					// System.out.println("Skip: File:" + strLine
					// + " (optiPngExitValue)");
					// continue;
				}

				BufferedImage reference = ImageIO.read(optiPngFile);

				if (!(bufferedImageEquals(reference,
						ImageIO.read(zopfliPngFile)) && bufferedImageEquals(
						reference, GIFimage))) {
					throw new RuntimeException("ImagesUnequal");
					// System.out.println("Skip: File:" + strLine
					// + " (ImagesUnequal)");
					// continue;
				}

				final File smaller = zopfliPngFile.length() < optiPngFile
						.length() ? zopfliPngFile : optiPngFile;

				attemptFetch(new WikiAPI() {

					@Override
					public Object fetch() throws IOException, LoginException {
						commons.upload(
								smaller,
								filename,
								pageText,
								"Bot: Converting file to [[c:COM:FT#GIFvsPNG|superior]] PNG file. (Source: [[c:"
										+ source.getName()
										+ "|"
										+ source.getName().substring(5)
										+ "]]). " + issue);
						return null;
					}
				}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);

				zopfliPngFile.delete();
				optiPngFile.delete();

				linkList += "*[[:" + source.getName()
						+ "#6cca01a2b5ea26e7871b]]\n";

				attemptFetch(new WikiAPI() {

					@Override
					public Object fetch() throws IOException, LoginException {
						commons.edit(
								source.getName(),
								"{{DuplicatePNG|" + filename + "}}\n"
										+ source.getPlainText(),
								"File was replaced by [[c:COM:FT#GIFvsPNG|superior]] PNG version - tagging with "
										+ "{{[[c:template:Duplicate|Duplicate]]|[[c:"
										// (no DuplicatePNG)
										+ filename
										+ "|"
										+ filename.substring(5)
										+ "]]}}. "
										+ issue);
						return null;
					}
				}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);

				deleteQueue.add(source.getName() + "|" + filename);
				replaceViaDelinkerAndProcessDupes("{{universal replace|"
						+ source.getName().substring(5).replace("_", " ") + "|"
						+ filename.substring(5).replace("_", " ")
						+ "|reason=Replacing GIF by exact PNG duplicate.}}"
						+ "\n");
			} else {
				System.out.println("Skip: " + source.getName()
						+ " (Animated GIF)");
			}
		}

		// Push all on exit
		replaceCounterMax = 0;
		replaceViaDelinkerAndProcessDupes("");
		//
		writeConclusion();

		System.out.println("\n" + "===================================="
				+ "\n\n" + "  Exiting. (All successfully done)" + "\n\n"
				+ "====================================");
	}

	/**
	 * Check if the two images are equal
	 * 
	 * @param b1
	 *            the first image
	 * @param b2
	 *            the second image
	 * @return if the images are equal
	 */
	static boolean bufferedImageEquals(BufferedImage b1, BufferedImage b2) {
		if (b1 == b2)
			return true; // true if both are null

		if (b1 == null || b2 == null)
			return false;

		if (b1.getWidth() != b2.getWidth())
			return false;

		if (b1.getHeight() != b2.getHeight())
			return false;

		for (int i = 0; i < b1.getWidth(); i++) {
			for (int j = 0; j < b1.getHeight(); j++) {
				if (b1.getRGB(i, j) != b2.getRGB(i, j)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Create all redirects (delete first)
	 * 
	 * @throws LoginException
	 * @throws IOException
	 *             if a network error occurs
	 */
	private static void processDuplicates() throws LoginException, IOException {
		String poll;
		String[] temp;
		int sleepSeconds = WAIT_AFTER_COMDEL_REQUEST * 60;

		System.out.println("Sleeping " + sleepSeconds
				+ " seconds before processing duplicates.");
		try {
			Thread.sleep(1000 * sleepSeconds);
		} catch (InterruptedException ignore) {
		}
		while (true) {
			poll = deleteQueue.poll();
			if (poll == null)
				return;
			temp = poll.split("\\|");
			final String source = temp[0];
			final String target = temp[1];
			System.out.println("#REDIRECT [[" + source + "]] -> [[" + target
					+ "]]");
			attemptFetch(new WikiAPI() {

				@Override
				public Object fetch() throws IOException, LoginException {
					commons.delete(source, "Exact duplicate of [[c:" + target
							+ "]].");
					return null;
				}
			}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);
			attemptFetch(new WikiAPI() {

				@Override
				public Object fetch() throws IOException, LoginException {
					commons.edit(source, "#REDIRECT [[" + target + "]]",
							"Redirecting to [[c:" + target
									+ "|duplicate file]].");
					return null;
				}
			}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);

		}
	}

	/**
	 * Conclude by writing which replacements should be done
	 */
	private static void writeConclusion() {
		System.out.println("Request replacements of:" + "\n" + replaceCOMDEL
				+ "\n");
		System.out.println("Process those links:" + "\n" + linkList
				+ "\n\n(The last " + replaceCounter + " files)");
	}

	/**
	 * Accept replacements for commons delinker and queue them until more than
	 * replaceCounterMax replacements accumulated; Then edit the comDel page and
	 * processDuplicates()
	 * 
	 * @param rep
	 *            the replacement to queue
	 * @throws LoginException
	 * @throws IOException
	 *             if a network error occurs
	 */
	private static void replaceViaDelinkerAndProcessDupes(String rep)
			throws LoginException, IOException {
		if (rep.length() > 0) {
			replaceCOMDEL += rep;
			replaceCounter++;
		}
		if (replaceCounter > 0 && replaceCounter >= replaceCounterMax) {
			final String pageText = (String) attemptFetch(new WikiAPI() {

				@Override
				public Object fetch() throws IOException, LoginException {
					return commons.getPageText(COMMONS_DELINKER_PAGE);
				}
			}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);
			attemptFetch(new WikiAPI() {

				@Override
				public Object fetch() throws IOException, LoginException {
					commons.edit(COMMONS_DELINKER_PAGE, pageText
							+ replaceCOMDEL, "Requesting to replace "
							+ replaceCounter + " GIFs by exact PNG duplicates.");
					return null;
				}
			}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);
			replaceCounter = 0;
			replaceCOMDEL = "";
			// Also delete
			processDuplicates();
		}

	}

	/**
	 * Determine if the given image is opaque
	 * 
	 * @param gIFimage
	 *            the image
	 * @return if it is opaque
	 */
	private static boolean isOpaque(BufferedImage gIFimage) {
		for (int w = 0; w < gIFimage.getWidth(); w++) {
			for (int h = 0; h < gIFimage.getHeight(); h++) {
				int argb = gIFimage.getRGB(w, h);
				int a = (argb >> 24) & 255;
				if (a < 255) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Determine if the given image is greyscale
	 * 
	 * @param gIFimage
	 *            the image
	 * @return if it is greyscale
	 */
	private static boolean isGreyscale(BufferedImage gIFimage) {
		for (int w = 0; w < gIFimage.getWidth(); w++) {
			for (int h = 0; h < gIFimage.getHeight(); h++) {
				int rgb = gIFimage.getRGB(w, h);
				int r = (rgb >> 16) & 255;
				int g = (rgb >> 8) & 255;
				int b = rgb & 255;
				if (r != g || g != b) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Create a String with the history log
	 * 
	 * @param GIFimage
	 *            the image to read the width and height from
	 * @param GIFfile
	 *            the file to read the length in bytes from
	 * @param log
	 *            the log entries to consider
	 * @return the history log
	 */
	private static String createHistoryLog(BufferedImage GIFimage,
			File GIFfile, LogEntry[] log) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		LogEntry relevantLog = log[0]; // TODO log.length>1
		String username = null;
		if (relevantLog.getUser() != null)
			username = relevantLog.getUser().getUsername();
		String historyLog = "|-\n" + "| " + "{{ISOdate|"
				+ sdf.format(relevantLog.getTimestamp().getTime()) + "}} || "
				+ GIFimage.getWidth() + " × " + GIFimage.getHeight() + " ("
				+ GIFfile.length() + " bytes) || {{User|" + username
				+ "}} || ''<nowiki>" + relevantLog.getReason()
				+ "</nowiki>''\n"; // TODO interpret like mediawiki upload
									// comment!
		return historyLog;
	}

	/**
	 * Find an empty file and create the resulting new file name
	 * 
	 * @param wiki
	 *            where to connect to
	 * @param filename
	 *            the file title (without the extension!)
	 * @return full new file name
	 * @throws IOException
	 */
	private static String appendSuffix(Wiki wiki, String filename)
			throws IOException {
		try {
			new WikiPage(wiki, filename + ".png");
		} catch (FileNotFoundException fe) {
			return filename + ".png";
		}
		int c = 1;
		while (true) {
			c++;
			try {
				new WikiPage(wiki, filename + " - " + c + ".png");
			} catch (FileNotFoundException fe) {
				return filename + " - " + c + ".png";
			}
		}
	}
}
