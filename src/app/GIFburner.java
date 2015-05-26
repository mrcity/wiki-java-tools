package app;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
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

public class GIFburner {

	static Wiki commons;
	static String tempFolder = "/tmp";
	static int replaceCounter = 0;
	static int replaceCounterMax = 10;
	static String replaceCOMDEL = "";
	static String commonsDelinkerPage = "User:CommonsDelinker/commands";
	static int waitAfterComDelRequest = 15; // in minutes

	static String linkList = "";
	static Queue<String> deleteQueue = new LinkedList<>();

	public static void main(String[] args) {

		System.out
				.println("version 15.01.02\n"
						+ "Keep in mind to move the list without the 'File:' prefix to "
						+ tempFolder
						+ "/FileList.\n"
						+ "After the bot finished: Clean up all still tagged files first!\n");

		commons = new Wiki("commons.wikimedia.org");

		try {

			commons.login("GifTagger", "31cfa1691e67505729dc1d985d4b14f3");
			commons.setThrottle(8 * 1000);
			commons.setMaxLag(3);
			commons.setMarkBot(true);
			commons.setMarkMinor(true);
			commons.setLogLevel(Level.WARNING);

			BufferedReader br = new BufferedReader(new InputStreamReader(
					new DataInputStream(new FileInputStream(tempFolder
							+ "/FileList"))));

			gifTagger(br);

		} catch (Exception e) {
			e.printStackTrace();
			writeConclusion();
			System.out.println("\n"
					+ "Emergency Shutdown: Unhandled exception!" + "\n");
		}

	}

	private static void gifTagger(BufferedReader br) throws LoginException,
			IOException, InterruptedException {

		File PNGtemp = new File(tempFolder
				+ "/temp-058db7b7f89f89bd75f4b4c63848d741");

		String strLine;
		WikiPage source;
		while ((strLine = br.readLine()) != null) {
			if (strLine.length() == 0)
				continue;
			try {
				source = new WikiPage(commons, "File:" + strLine);
			} catch (FileNotFoundException e) {
				System.out.println("Skip: File:" + strLine + " (Not found)");
				continue;
			}
			if (saveIsRedirect(source.getName(), 0)) {
				System.out.println("Skip: " + source.getName() + " (Redirect)");
				continue;
			}
			String metadata = saveGetFileMetadata(source.getName(), 0)
					.toString();
			if (!metadata.contains("mime=image/gif")) {
				System.out.println("Skip: " + source.getName()
						+ " (Not image/gif)");
				System.exit(-1);
				return;
			}
			if (metadata.contains("frameCount=1, ")) {

				LogEntry[] log = saveGetImageHistory(source.getName(), 0);
				if (log.length > 1) {
					System.out.println("Skip: " + source.getName()
							+ " (More than one revision)");
					// TODO not continue!
					continue;
				}
				byte[] GIFbytes = saveGetImage(source.getName(), 0);
				BufferedImage GIFimage;
				try {
					GIFimage = ImageIO.read(new ByteArrayInputStream(GIFbytes));
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
				String issue = "This GIF was problematic due to "
						+ (isOpaque ? "" : "transparency")
						+ (!isGreyScale && !isOpaque ? " and " : "")
						+ (isGreyScale ? "" : "non-greyscale color table")
						+ ". ";

				System.out.println("Doing: " + source.getName()
						+ " (frameCount=1)");
				ImageIO.write(GIFimage, "png", PNGtemp);

				String filename = source.getName().replaceAll("\\.[a-zA-Z]+$",
						"");
				filename = appendSuffix(commons, filename);
				String historyLog = createHistoryLog(commons, GIFimage,
						GIFbytes, log);

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

				String pageText = source.getPlainText()
						+ "\n== {{Original upload log}} ==\n"
						+ "{| class=\"wikitable\"\n|-\n"
						+ "! {{int:filehist-datetime}} !! {{int:filehist-dimensions}} !! {{int:filehist-user}} !! {{int:filehist-comment}}\n"
						+ historyLog + "|}";

				saveUpload(
						PNGtemp,
						filename,
						pageText,
						"Bot: Converting file to [[c:COM:FT#GIFvsPNG|superior]] PNG file. (Source: [[c:"
								+ source.getName()
								+ "|"
								+ source.getName().substring(5)
								+ "]]). "
								+ issue, 0);

				linkList += "*[[:" + source.getName()
						+ "#6cca01a2b5ea26e7871b]]\n";

				saveEdit(
						source.getName(),
						"{{DuplicatePNG|" + filename + "}}\n"
								+ source.getPlainText(),
						"File was replaced by [[c:COM:FT#GIFvsPNG|superior]] PNG version - tagging with "
								+ "{{[[c:template:Duplicate|Duplicate]]|[[c:"
								// (no DuplicatePNG)
								+ filename
								+ "|"
								+ filename.substring(5)
								+ "]]}}. " + issue, 0);

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

	//
	// Exception "resistant" wrappers
	//
	final private static int maxExceptions = 3;
	final private static int sleepAfterExc = 7 * 60;

	private static void sleepAndPrint(int secs) {
		System.out.println("\n  ************\n" + " Exception caught\n"
				+ " Sleep " + secs + " seconds.\n  ************\n");
		try {
			Thread.sleep(secs * 1000);
		} catch (InterruptedException e) {
		}
	}

	private static byte[] saveGetImage(String title, int exceptions)
			throws IOException {
		try {
			return commons.getImage(title);
		} catch (IOException e) {
			sleepAndPrint(sleepAfterExc);
			exceptions++;
			if (exceptions == maxExceptions)
				throw e;
			else
				return saveGetImage(title, exceptions);
		}
	}

	private static LogEntry[] saveGetImageHistory(String title, int exceptions)
			throws IOException {
		try {
			return commons.getImageHistory(title);
		} catch (IOException e) {
			sleepAndPrint(sleepAfterExc);
			exceptions++;
			if (exceptions == maxExceptions)
				throw e;
			else
				return saveGetImageHistory(title, exceptions);
		}
	}

	private static Object saveGetFileMetadata(String file, int exceptions)
			throws IOException {
		try {
			return commons.getFileMetadata(file);
		} catch (IOException e) {
			sleepAndPrint(sleepAfterExc);
			exceptions++;
			if (exceptions == maxExceptions)
				throw e;
			else
				return saveGetFileMetadata(file, exceptions);
		}
	}

	private static boolean saveIsRedirect(String page, int exceptions)
			throws IOException {
		try {
			return commons.isRedirect(page);
		} catch (IOException e) {
			sleepAndPrint(sleepAfterExc);
			exceptions++;
			if (exceptions == maxExceptions)
				throw e;
			else
				return saveIsRedirect(page, exceptions);
		}
	}

	private static void saveEdit(String name, String text, String summary,
			int exceptions) throws LoginException, IOException {
		try {
			commons.edit(name, text, summary);
		} catch (IOException e) {
			sleepAndPrint(sleepAfterExc);
			exceptions++;
			if (exceptions == maxExceptions)
				throw e;
			else
				saveEdit(name, text, summary, exceptions);
		}
	}

	private static void saveUpload(File pNGtemp, String filename,
			String pageText, String reason, int exceptions)
			throws LoginException, IOException {
		try {
			commons.upload(pNGtemp, filename, pageText, reason);
		} catch (IOException e) {
			sleepAndPrint(sleepAfterExc);
			exceptions++;
			if (exceptions == maxExceptions)
				throw e;
			else
				saveUpload(pNGtemp, filename, pageText, reason, exceptions);
		}
	}

	private static void saveDelete(String title, String reason, int exceptions)
			throws LoginException, IOException {
		try {
			commons.delete(title, reason);
		} catch (IOException e) {
			sleepAndPrint(sleepAfterExc);
			exceptions++;
			if (exceptions == maxExceptions)
				throw e;
			else
				saveDelete(title, reason, exceptions);
		}
	}

	private static String saveGetPageText(String title, int exceptions)
			throws IOException {
		try {
			return commons.getPageText(title);
		} catch (IOException e) {
			sleepAndPrint(sleepAfterExc);
			exceptions++;
			if (exceptions == maxExceptions)
				throw e;
			else
				return saveGetPageText(title, exceptions);
		}
	}

	private static void processDuplicates() throws LoginException, IOException,
			InterruptedException {
		String poll;
		String[] temp;
		String source;
		String target;
		int sleepSeconds = waitAfterComDelRequest * 60;

		System.out.println("Sleeping " + sleepSeconds
				+ " seconds before processing duplicates.");
		Thread.sleep(1000 * sleepSeconds);
		while (true) {
			poll = deleteQueue.poll();
			if (poll == null)
				return;
			temp = poll.split("\\|");
			source = temp[0];
			target = temp[1];
			System.out.println("#REDIRECT [[" + source + "]] -> [[" + target
					+ "]]");
			saveDelete(source, "Exact duplicate of [[c:" + target + "]].", 0);
			saveEdit(source, "#REDIRECT [[" + target + "]]",
					"Redirecting to [[c:" + target + "|duplicate file]].", 0);

		}
	}

	private static void writeConclusion() {
		System.out.println("Request replacements of:" + "\n" + replaceCOMDEL
				+ "\n");
		System.out.println("Process those links:" + "\n" + linkList
				+ "\n\n(At least the last " + replaceCounter + " files)");
	}

	private static void replaceViaDelinkerAndProcessDupes(String rep)
			throws LoginException, IOException, InterruptedException {
		if (rep.length() > 0) {
			replaceCOMDEL += rep;
			replaceCounter++;
		}
		if (replaceCounter > 0 && replaceCounter >= replaceCounterMax) {
			saveEdit(commonsDelinkerPage,
					saveGetPageText(commonsDelinkerPage, 0) + replaceCOMDEL,
					"Requesting to replace " + replaceCounter
							+ " GIFs by exact PNG duplicates.", 0);
			replaceCounter = 0;
			replaceCOMDEL = "";
			// Also delete
			processDuplicates();
		}

	}

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

	private static String createHistoryLog(Wiki commons,
			BufferedImage GIFimage, byte[] GIFbytes, LogEntry[] log) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		LogEntry relevantLog = log[0]; // TODO log.length>1
		String username = null;
		if (relevantLog.getUser() != null)
			username = relevantLog.getUser().getUsername();
		String historyLog = "|-\n" + "| " + "{{ISOdate|"
				+ sdf.format(relevantLog.getTimestamp().getTime()) + "}} || "
				+ GIFimage.getWidth() + " × " + GIFimage.getHeight() + " ("
				+ GIFbytes.length + " bytes) || {{User|" + username
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
