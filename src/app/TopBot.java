package app;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.logging.Level;

import javax.security.auth.login.LoginException;

import wiki.Wiki;
import wiki.WikiPage;

class TopBotThread extends Thread {

	private Wiki wiki;
	private String category;
	private int exceptions;

	/**
	 * Create a thread which can crawl a category and count each member's global
	 * usage
	 * 
	 * @param wiki
	 *            the wiki to connect to
	 * @param category
	 *            the category to crawl
	 */
	public TopBotThread(Wiki wiki, String category) {
		this.wiki = wiki;
		this.category = category;
		exceptions = 0;
	}

	/**
	 * Try to crawl the category and return right after the second exception
	 * occurs
	 */
	public void run() {
		if (exceptions == 2) {
			System.out.println(this.getName() + " shut down after "
					+ exceptions + " exceptions. (" + category + ")");
			return;
		}
		try {
			System.out.println(this.getName() + "   START");
			crawlCategory();
			System.out.println(this.getName() + "   END");
		} catch (LoginException | IOException e) {
			e.printStackTrace();
			exceptions++;
			run();
		}

	}

	/**
	 * Count usage and write to page
	 * 
	 * @param wiki
	 *            Target wiki
	 * @param category
	 *            Target category (Ex.: 'Flag images that should use vector
	 *            graphics')
	 * @param number
	 *            The number of top files to list
	 * @throws IOException
	 * @throws LoginException
	 */
	private void crawlCategory() throws IOException, LoginException {
		String[] members = getAllCategoryMembers(wiki, category);
		Comparable[][] cArray = new Comparable[members.length][2];

		for (int i = 0; i < members.length; ++i) {
			if (i % 25 == 0)
				System.out.println(this.getName() + "   PROGRESS: " + i
						+ " of " + members.length + " done.");
			cArray[i][1] = getActualUsageCount(wiki.getGlobalUsage(members[i]));
			cArray[i][0] = members[i];
		}

		java.util.Arrays.sort(cArray, new java.util.Comparator<Comparable[]>() {
			public int compare(Comparable[] a, Comparable[] b) {
				return b[1].compareTo(a[1]);
			}
		});

		String text = "\nLast update: "
				+ DateFormat.getDateInstance(DateFormat.FULL).format(
						Calendar.getInstance().getTime()) + "." + "\n"
				+ "\nCounting only the usage in the main namespace:\n\n"
				+ "<gallery showfilename=yes >\n";
		for (int i = 0; i < Math.min(TopBot.NUMBER, members.length); i++) {
			text = text + cArray[i][0] + "|" + (i + 1) + ". Used "
					+ cArray[i][1] + " times.\n";
		}
		text = text + "</gallery>"
				+ "\n[[Category:Images that should use vector graphics]]"
				+ "\n[[Category:" + WikiPage.firstCharToUpperCase(category)
				+ "]]";
		String title = "Top " + TopBot.NUMBER + " "
				+ WikiPage.firstCharToLowerCase(category);
		String[] splittedText = { "" };
		try {
			splittedText = wiki.getPageText(title).split(TopBot.SEPARATOR);
		} catch (FileNotFoundException ignore) {
		}
		wiki.edit(title, (splittedText.length == 1 ? "" : splittedText[0])
				+ TopBot.SEPARATOR + "\n" + text, "Update");
	}

	/**
	 * Scan in several subcategories for the members of valid
	 * "TopBot.ROOT_CATEGORY"-categories
	 * 
	 * @param wiki
	 *            the wiki to connect to
	 * @param category
	 *            the category (and it's subcategories) to scan
	 * @return string array holding all members of the given category and
	 *         members of valid subcategories
	 * @throws IOException
	 *             if a network error occurs
	 */
	private String[] getAllCategoryMembers(Wiki wiki, String category)
			throws IOException {
		int depth = 5;
		String[] possibleCats = wiki.getCategoryMembers(category, depth,
				Wiki.CATEGORY_NAMESPACE);
		ArrayList<String> allCategoryMembers = new ArrayList<String>();
		for (String cat : possibleCats) {
			if (cat.endsWith(TopBot.ROOT_CATEGORY)) {
				allCategoryMembers.addAll(Arrays.asList(wiki
						.getCategoryMembers(cat, false, Wiki.FILE_NAMESPACE)));
			}
		}
		allCategoryMembers.addAll(Arrays.asList(wiki.getCategoryMembers(
				category, false, Wiki.FILE_NAMESPACE)));
		return allCategoryMembers.toArray(new String[0]);
	}

	/**
	 * Calculate the global usage only in the main namespace
	 * 
	 * @param globalUsage
	 *            the list of globalusage returned by the wiki java api
	 * @return the global usage count in the main namespace
	 */
	private int getActualUsageCount(String[][] globalUsage) {
		int count = 0;
		for (String[] usage : globalUsage) {
			String title = usage[1];
			if (!title.contains(":"))
				count++;
		}
		return count;
	}

}

public class TopBot {

	public static final String ROOT_CATEGORY = "images that should use vector graphics";
	public static final int NUMBER = 200;
	public static final String SEPARATOR = "<!-- Only text ABOVE this line will be preserved on updates -->";
	public static final String VERSION = "v15.05.20";

	public static void main(String[] args) {

		System.out.println(VERSION);

		String[] expectedArgs = { "username" };
		String[] expectedArgsDescription = { "username is your username on the wiki." };
		if (args.length != expectedArgs.length) {
			System.out.print("Usage: java -jar filename.jar");
			for (String i : expectedArgs)
				System.out.print(" [" + i + "]");
			System.out.println("");
			for (String i : expectedArgsDescription)
				System.out.println("Where " + i);
			System.exit(-1);
		}
		Wiki commons = new Wiki("commons.wikimedia.org");
		try {
			System.out.println("Please type in the password for " + args[0]
					+ ".");
			commons.login(args[0], System.console().readPassword());

			commons.setThrottle(5 * 1000);
			commons.setMaxLag(3);
			commons.setMarkMinor(false);
			commons.setMarkBot(false);
			commons.setLogLevel(Level.WARNING);

			System.out.println("Fetching categories");

			String[] cats = getSvgCategories(commons);

			System.out.println("\n\n========\n\n"
					+ "Processing the following categories:" + "\n");
			for (String cat : cats) {
				System.out.println("Category:" + cat);
			}
			System.out.println("\n========\n\n");
			Thread.sleep(1000);
			// Create threads

			TopBotThread[] threads = new TopBotThread[cats.length];
			for (int i = 0; i < cats.length; i++) {
				threads[i] = new TopBotThread(commons, cats[i]);
				threads[i].start();
			}
		} catch (LoginException | IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Determine the categories which should be crawled
	 * 
	 * @param wiki
	 *            the wiki to connect to
	 * @return all categories which end in "ROOT_CATEGORY"
	 * @throws IOException
	 *             if a network error occurs
	 */
	private static String[] getSvgCategories(Wiki wiki) throws IOException {
		String[] allCategories = wiki.getCategoryMembers(ROOT_CATEGORY, false,
				Wiki.CATEGORY_NAMESPACE);
		ArrayList<String> allSvgCategories = new ArrayList<String>(
				allCategories.length + 1);
		allSvgCategories.add(ROOT_CATEGORY);
		for (String cat : allCategories) {
			if (cat.endsWith(ROOT_CATEGORY))
				allSvgCategories.add(cat);
		}
		return allSvgCategories.toArray(new String[0]);
	}

}